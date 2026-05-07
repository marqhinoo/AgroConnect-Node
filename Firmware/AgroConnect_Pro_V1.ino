#include <WiFi.h>
#include <HTTPClient.h>
#include <LoRa.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_BMP280.h>
#include <Adafruit_SHT31.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// --- Configuración IP Estática (Conecta en 1-2 segundos) ---
IPAddress local_IP(192, 168, 1, 150); 
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

const char* ssid = "TU_WIFI_RURAL";
const char* password = "TU_PASSWORD";
const char* serverName = "https://daily-pusher-sapling.ngrok-free.app/update";

// --- Pines LoRa ---
#define NSS 10
#define RST 9
#define DIO0 6

// --- Pines Sensores ---
#define SOIL_15CM A0 
#define SOIL_40CM A1
#define DS18B20_PIN A2 

// Configuración DS18B20
OneWire oneWire(DS18B20_PIN);
DallasTemperature sensors(&oneWire);

Adafruit_BMP280 bmp;
Adafruit_SHT31 sht30 = Adafruit_SHT31();

// --- Función para leer humedad estable (Promedio de 10 lecturas) ---
// Útil para cables largos de 4 metros que captan ruido
int leerHumedadEstable(int pin) {
  long suma = 0;
  for(int i = 0; i < 10; i++) {
    suma += analogRead(pin);
    delay(20); // Pequeña pausa para estabilizar
  }
  return (int)(suma / 10);
}

void setup() {
  setCpuFrequencyMhz(80); // Ahorro de batería
  Serial.begin(115200);
  
  // Inicialización I2C con velocidad estándar (más estable para sensores)
  Wire.begin(); 
  Wire.setClock(100000); 
  
  sensors.begin(); 

  if (!bmp.begin(0x76)) Serial.println("Error BMP280");
  if (!sht30.begin(0x44)) Serial.println("Error SHT30");

  LoRa.setPins(NSS, RST, DIO0);
  if (!LoRa.begin(433E6)) Serial.println("Error LoRa");

  procesarYEnviar();

  // Apagado de radios antes de dormir
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
  LoRa.sleep();
  
  Serial.println("Deep Sleep...");
  esp_sleep_enable_timer_wakeup(600 * 1000000ULL); // 10 min
  esp_deep_sleep_start();
}

void procesarYEnviar() {
  // 1. Lecturas de Aire (Cerca del ESP32)
  float t_aire = sht30.readTemperature();
  float h_aire = sht30.readHumidity();
  float pres = bmp.readPressure() / 100.0F;
  
  // 2. Lectura Suelo Digital (4 metros - Robusto)
  sensors.requestTemperatures(); 
  float t_suelo = sensors.getTempCByIndex(0);

  // 3. Lectura Humedad Suelo con Filtro de Promedio (4 metros - Con ruido)
  int s1_raw = leerHumedadEstable(SOIL_15CM);
  int s2_raw = leerHumedadEstable(SOIL_40CM);
  
  // MAPEO: Ajustar 3000 (Seco) y 1200 (Agua) según tus pruebas con el cable puesto
  int hum15 = map(s1_raw, 3000, 1200, 0, 100); 
  int hum40 = map(s2_raw, 3000, 1200, 0, 100);
  hum15 = constrain(hum15, 0, 100);
  hum40 = constrain(hum40, 0, 100);

  // 4. Armar el JSON
  StaticJsonDocument<512> doc;
  doc["usuario"] = "admin@agro.com";
  doc["tempAire"] = t_aire;
  doc["humAire"] = h_aire;
  doc["presion"] = pres;
  doc["vpd"] = String(0.61078 * exp((17.27 * t_aire) / (t_aire + 237.3)) * (1.0 - (h_aire / 100.0)), 2);
  doc["hum15"] = hum15;
  doc["hum40"] = hum40;
  doc["tempSuelo"] = (t_suelo == -127.0) ? "0.0" : String(t_suelo, 1);
  doc["bateria"] = "4.15";

  // 5. Envío WiFi
  WiFi.config(local_IP, gateway, subnet);
  WiFi.begin(ssid, password);
  
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 15) {
    delay(500);
    intentos++;
  }

  bool enviado = false;
  if (WiFi.status() == WL_CONNECTED) {
    doc["wifiCon"] = 1;
    doc["loraCon"] = 0;
    HTTPClient http;
    http.begin(serverName);
    http.addHeader("Content-Type", "application/json");
    String output;
    serializeJson(doc, output);
    if (http.POST(output) > 0) enviado = true;
    http.end();
  }

  // 6. Envío LoRa (Si falla el WiFi)
  if (!enviado) {
    doc["wifiCon"] = 0;
    doc["loraCon"] = 1;
    String loraOut;
    serializeJson(doc, loraOut);
    LoRa.beginPacket();
    LoRa.print(loraOut);
    LoRa.endPacket();
  }
}

float calcularVPD(float t, float h) {
  if (isnan(t) || isnan(h)) return 0.0;
  float vpsat = 0.61078 * exp((17.27 * t) / (t + 237.3));
  return vpsat * (1.0 - (h / 100.0));
}

void loop() {}
