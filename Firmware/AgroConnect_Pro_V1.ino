#include <WiFi.h>
#include <HTTPClient.h>
#include <LoRa.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_BMP280.h>
#include <Adafruit_SHT31.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// ==========================================
// CONFIGURACIÓN DE ROL E IDENTIFICACIÓN
// ==========================================
const bool ES_MAESTRO_GATEWAY = false;       // Cambiar a TRUE para el de la casa/base, FALSE para el de campo
const char* idNodo = "nodo_campo_01";        // Si es Maestro, el código automáticamente usará "nodo_maestro_base"

// --- Configuración WiFi e IP Estática ---
IPAddress local_IP(192, 168, 1, 150); 
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

const char* ssid = "TU_WIFI_RURAL";
const char* password = "TU_PASSWORD";
const char* serverName = "https://daily-pusher-sapling.ngrok-free.app/update";

// --- Pines Módulo LoRa ---
#define NSS 10
#define RST 9
#define DIO0 6

// --- Pines Sensores ---
#define SOIL_15CM A0       
#define SOIL_40CM A1       
#define DS18B20_PIN A2     

const int VALOR_SECO = 3000;   
const int VALOR_HUMEDO = 1200; 

OneWire oneWire(DS18B20_PIN);
DallasTemperature sensors(&oneWire);
Adafruit_BMP280 bmp;
Adafruit_SHT31 sht30 = Adafruit_SHT31();

// Tiempos para el Maestro (en milisegundos)
unsigned long ultimoTiempoMedicionMaestro = 0;
const unsigned long INTERVALO_MEDICION_MAESTRO = 600000; // 10 minutos

// Tiempo de sueño para el Esclavo (en microsegundos)
const uint64_t TIEMPO_DORMIR_ESCLAVO_US = 600 * 1000000ULL; 

int leerHumedadEstable(int pin) {
  long suma = 0;
  for(int i = 0; i < 10; i++) { suma += analogRead(pin); delay(20); }
  return (int)(suma / 10);
}

float calcularVPD(float t, float h) {
  if (isnan(t) || isnan(h)) return 0.0;
  float vpsat = 0.61078 * exp((17.27 * t) / (t + 237.3));
  return vpsat * (1.0 - (h / 100.0));
}

// --- FUNCIÓN GENERAL DE LECTURA (La usan ambos nodos) ---
String generarJsonMedicion(const char* idActual, int wifiCon, int loraCon) {
  float t_aire = sht30.readTemperature();
  float h_aire = sht30.readHumidity();
  float pres = bmp.readPressure() / 100.0F;
  float vpdCalculado = calcularVPD(t_aire, h_aire);
  
  sensors.requestTemperatures(); 
  float t_suelo = sensors.getTempCByIndex(0);
  if (t_suelo == -127.0) t_suelo = 0.0;

  int s1_raw = leerHumedadEstable(SOIL_15CM);
  int s2_raw = leerHumedadEstable(SOIL_40CM);
  
  int hum15 = map(s1_raw, VALOR_SECO, VALOR_HUMEDO, 0, 100); 
  int hum40 = map(s2_raw, VALOR_SECO, VALOR_HUMEDO, 0, 100);
  hum15 = constrain(hum15, 0, 100);
  hum40 = constrain(hum40, 0, 100);

  JsonDocument doc;
  doc["usuario"]   = "admin@agro.com";
  doc["idNodo"]    = idActual;
  doc["tempAire"]  = serialized(String(t_aire, 2));
  doc["humAire"]   = serialized(String(h_aire, 2));
  doc["presion"]   = serialized(String(pres, 2));
  doc["vpd"]       = serialized(String(vpdCalculado, 2));
  doc["hum15"]     = hum15;
  doc["hum40"]     = hum40;
  doc["tempSuelo"] = serialized(String(t_suelo, 1));
  doc["bateria"]   = 4.15; 
  doc["wifiCon"]   = wifiCon;
  doc["loraCon"]   = loraCon;

  String output;
  serializeJson(doc, output);
  return output;
}

void setup() {
  setCpuFrequencyMhz(80); 
  Serial.begin(115200);
  delay(500);

  Wire.begin(); 
  Wire.setClock(100000); 
  sensors.begin(); 

  LoRa.setPins(NSS, RST, DIO0);
  if (!LoRa.begin(433E6)) Serial.println("Error LoRa");

  // Inicializar hardware de sensores locales comunes
  if (!bmp.begin(0x76)) Serial.println("Error BMP280");
  if (!sht30.begin(0x44)) Serial.println("Error SHT30");

  if (!ES_MAESTRO_GATEWAY) {
    // ==========================================
    // EJECUCIÓN: NODO DE CAMPO (ESCLAVO)
    // ==========================================
    Serial.printf("\n--- MODO: NODO DE CAMPO [%s] ---\n", idNodo);
    
    // Mide y envía ráfaga LoRa
    String payloadLoRa = generarJsonMedicion(idNodo, 0, 1);
    Serial.println("Transmitiendo reporte hacia el Maestro...");
    LoRa.beginPacket();
    LoRa.print(payloadLoRa);
    LoRa.endPacket();

    // Esperar ventana de SYNC
    Serial.println("Esperando SYNC_OK...");
    unsigned long startEscucha = millis();
    bool sincronizado = false;

    while (millis() - startEscucha < 3000) { 
      int packetSize = LoRa.parsePacket();
      if (packetSize) {
        String msg = "";
        while (LoRa.available()) msg += (char)LoRa.read();
        if (msg == "SYNC_OK") {
          sincronizado = true;
          break;
        }
      }
    }

    WiFi.disconnect(true);
    WiFi.mode(WIFI_OFF);
    LoRa.sleep();

    if (sincronizado) {
      Serial.println("Sincronizado. Deep Sleep 10 min...");
      esp_sleep_enable_timer_wakeup(TIEMPO_DORMIR_ESCLAVO_US);
    } else {
      Serial.println("Fallo SYNC. Reintento en 30 seg...");
      esp_sleep_enable_timer_wakeup(30 * 1000000ULL);
    }
    esp_deep_sleep_start();

  } else {
    // ==========================================
    // EJECUCIÓN: NODO MAESTRO GATEWAY (CASA)
    // ==========================================
    Serial.println("\n--- MODO: MAESTRO GATEWAY ACTIVADO (SIEMPRE DESPIERTO) ---");
    conectarWiFi();
    
    // Realiza una medición local inmediata de sus propios sensores al arrancar
    String localJson = generarJsonMedicion("nodo_maestro_base", 1, 0);
    enviarAlServidorWeb(localJson);
    ultimoTiempoMedicionMaestro = millis();
  }
}

void loop() {
  // El loop solo corre de forma permanente para el MAESTRO GATEWAY
  if (!ES_MAESTRO_GATEWAY) return;

  // --- TAREA 1: Escuchar y puentear datos de los nodos remotos ---
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    Serial.println("📡 Reporte remoto detectado por LoRa...");
    String cadenaJsonRemoto = "";
    while (LoRa.available()) cadenaJsonRemoto += (char)LoRa.read();

    if (cadenaJsonRemoto.startsWith("{") && cadenaJsonRemoto.endsWith("}")) {
      // Responder ACK inmediato al esclavo para que se duerma rápido
      delay(50); 
      LoRa.beginPacket();
      LoRa.print("SYNC_OK");
      LoRa.endPacket();
      Serial.println("ACK enviado al nodo remoto.");

      // Subir el JSON del esclavo a la API
      enviarAlServidorWeb(cadenaJsonRemoto);
    }
  }

  // --- TAREA 2: Medir sus propios sensores locales cada 10 minutos ---
  unsigned long tiempoActual = millis();
  if (tiempoActual - ultimoTiempoMedicionMaestro >= INTERVALO_MEDICION_MAESTRO) {
    ultimoTiempoMedicionMaestro = tiempoActual;
    Serial.println("\n[Maestro] Tomando lecturas de sensores locales propios...");
    
    String jsonPropio = generarJsonMedicion("nodo_maestro_base", 1, 0);
    enviarAlServidorWeb(jsonPropio);
  }
}

// --- SUBIDA HTTP DEL MAESTRO ---
void conectarWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  WiFi.config(local_IP, gateway, subnet);
  WiFi.begin(ssid, password);
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 15) { delay(500); intentos++; }
}

void enviarAlServidorWeb(String jsonString) {
  conectarWiFi();
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(serverName);
    http.addHeader("Content-Type", "application/json");
    int responseCode = http.POST(jsonString);
    Serial.printf("API HTTP Response Status: %d\n", responseCode);
    http.end();
  }
}
