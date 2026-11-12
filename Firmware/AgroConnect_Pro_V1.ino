#include <Arduino.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_SHT31.h>
#include <Adafruit_BMP280.h>
#include <SPI.h>
#include <LoRa.h>
#include <WiFi.h>
#include <FirebaseESP32.h>

// Credenciales y Firebase
#define WIFI_SSID "Informatica2043"
#define WIFI_PASSWORD "Info20432025+"
#define FIREBASE_HOST "agroconnect-9b8cd-default-rtdb.firebaseio.com"
#define FIREBASE_AUTH "SiS2adHOeXE8LDWgVjZnP1pA67nQPVxyudmMoPJA"
#define USER_UID "DdaAJqXxIcadkzamNl16yWyy8k13"
#define NODO_CENTRAL_ID "Marco"

// Pines I2C y OneWire
#define SDA_PIN 21
#define SCL_PIN 22
#define ONE_WIRE_BUS 4

// Sensores de humedad de suelo
#define SOIL_15CM_PIN 34
#define SOIL_40CM_PIN 35
#define AIR_VAL_15CM 3200
#define WATER_VAL_15CM 1300
#define AIR_VAL_40CM 3200
#define WATER_VAL_40CM 1300

// Pines LoRa Ra-01 (SPI)
#define LORA_SCK 18
#define LORA_MISO 19
#define LORA_MOSI 23
#define LORA_CS 5
#define LORA_RST 14
#define LORA_DIO0 2
#define BAND 433E6

// Estructura de telemetría
struct LecturaNodo {
  char nodoId[32];
  float tempAire;
  float humAire;
  float tempSuelo;
  float vpd;
  float presion;
  int hum15;
  int hum40;
  float bateria;
};

// Objetos de Sensores
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature ds18b20(&oneWire);
Adafruit_SHT31 sht30 = Adafruit_SHT31();
Adafruit_BMP280 bmp(&Wire);

// Firebase y FreeRTOS
FirebaseData firebaseData;
FirebaseAuth auth;
FirebaseConfig config;
QueueHandle_t colaFirebase;

// Variables de Estado y Temporización
bool sht30_conectado = false;
bool bmp280_conectado = false;
bool lora_conectado = false;
unsigned long previousMillis = 0;
const long interval = 10000; // Lectura de sensores locales cada 10s

// Tarea en segundo plano para Firebase
void tareaFirebase(void *pvParameters) {
  LecturaNodo datos;
  for (;;) {
    if (xQueueReceive(colaFirebase, &datos, portMAX_DELAY) == pdTRUE) {
      if (WiFi.status() == WL_CONNECTED) {
        // La ruta se adapta dinámicamente según la ID del nodo recibido
        String path = "/usuarios/" + String(USER_UID) + "/lecturas/" + String(datos.nodoId);
        
        FirebaseJson json;
        json.set("tempAire", datos.tempAire);
        json.set("humAire", datos.humAire);
        json.set("tempSuelo", datos.tempSuelo);
        json.set("vpd", datos.vpd);
        json.set("presion", datos.presion);
        json.set("hum15", datos.hum15);
        json.set("hum40", datos.hum40);
        json.set("bateria", datos.bateria);
        json.set("timestamp", String(millis()));

        if (Firebase.pushJSON(firebaseData, path, json)) {
          Serial.println("✅ [Firebase] Subido OK al nodo: " + String(datos.nodoId));
        } else {
          Serial.println("❌ [Firebase] Error: " + firebaseData.errorReason());
        }
      } else {
        Serial.println("⚠️ [Firebase] Wi-Fi no disponible, reintentando...");
        vTaskDelay(pdMS_TO_TICKS(1000));
      }
    }
    vTaskDelay(pdMS_TO_TICKS(10));
  }
}

void encolarTrama(String paquete) {
  paquete.trim();
  int index = 0;
  String valores[9];

  for (int i = 0; i < paquete.length(); i++) {
    if (paquete.charAt(i) == ',') {
      index++;
    } else {
      if (index < 9) valores[index] += paquete.charAt(i);
    }
  }

  if (index < 8) {
    Serial.println("⚠️ Trama LoRa incompleta recibida: " + paquete);
    return;
  }

  LecturaNodo nuevaLectura;
  // Extrae la ID enviada por el nodo emisor (ej: nodo_central_02, nodo_campo_01)
  valores[0].toCharArray(nuevaLectura.nodoId, sizeof(nuevaLectura.nodoId));
  nuevaLectura.tempAire = valores[1].toFloat();
  nuevaLectura.humAire = valores[2].toFloat();
  nuevaLectura.tempSuelo = valores[3].toFloat();
  nuevaLectura.vpd = valores[4].toFloat();
  nuevaLectura.presion = valores[5].toFloat();
  nuevaLectura.hum15 = valores[6].toInt();
  nuevaLectura.hum40 = valores[7].toInt();
  nuevaLectura.bateria = valores[8].toFloat();

  if (xQueueSend(colaFirebase, &nuevaLectura, pdMS_TO_TICKS(100)) != pdTRUE) {
    Serial.println("⚠️ Cola Firebase llena. Paquete descartado temporalmente.");
  }
}

void leerYEncolarSensoresLocales() {
  LecturaNodo local;
  // Fuerza siempre la ID del Nodo Central 01 para los sensores propios
  snprintf(local.nodoId, sizeof(local.nodoId), "%s", NODO_CENTRAL_ID);

  local.tempAire = sht30_conectado ? sht30.readTemperature() : 0.0;
  local.humAire = sht30_conectado ? sht30.readHumidity() : 0.0;
  
  ds18b20.requestTemperatures();
  local.tempSuelo = ds18b20.getTempCByIndex(0);
  if (local.tempSuelo == DEVICE_DISCONNECTED_C) local.tempSuelo = 0.0;

  local.presion = bmp280_conectado ? (bmp.readPressure() / 100.0F) : 0.0;

  // Cálculo de VPD
  if (local.tempAire > 0 && local.humAire > 0) {
    float es = 0.61078 * exp((17.27 * local.tempAire) / (local.tempAire + 237.3));
    float ea = es * (local.humAire / 100.0);
    local.vpd = es - ea;
  } else {
    local.vpd = 0.0;
  }

  int raw15 = analogRead(SOIL_15CM_PIN);
  int raw40 = analogRead(SOIL_40CM_PIN);
  local.hum15 = map(raw15, AIR_VAL_15CM, WATER_VAL_15CM, 0, 100);
  local.hum40 = map(raw40, AIR_VAL_40CM, WATER_VAL_40CM, 0, 100);
  local.hum15 = constrain(local.hum15, 0, 100);
  local.hum40 = constrain(local.hum40, 0, 100);

  local.bateria = 4.2; // Alimentación fija

  xQueueSend(colaFirebase, &local, pdMS_TO_TICKS(100));
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n--- INICIALIZANDO NODO CENTRAL ---");

  pinMode(SOIL_15CM_PIN, INPUT);
  pinMode(SOIL_40CM_PIN, INPUT);

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setTimeOut(1000);
  ds18b20.begin();
  ds18b20.setWaitForConversion(false);

  if (sht30.begin(0x44) || sht30.begin(0x45)) sht30_conectado = true;
  if (bmp.begin(0x76) || bmp.begin(0x77)) bmp280_conectado = true;

  colaFirebase = xQueueCreate(20, sizeof(LecturaNodo));

  // 1. Inicializar LoRa
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI);
  LoRa.setPins(LORA_CS, LORA_RST, LORA_DIO0);

  if (LoRa.begin(BAND)) {
    LoRa.setSyncWord(0xF3);
    LoRa.enableCrc();
    lora_conectado = true;
    Serial.println("✅ Receptor LoRa listo (433 MHz | Sync 0xF3 | CRC Activo)");
  } else {
    Serial.println("❌ ERROR: No se detectó módulo LoRa en el Nodo Central");
  }

  // 2. Conexión Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando Wi-Fi");
  int reintentos = 0;
  while (WiFi.status() != WL_CONNECTED && reintentos < 20) {
    delay(500);
    Serial.print(".");
    reintentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n✅ Wi-Fi Conectado!");
  } else {
    Serial.println("\n⚠️ No se pudo conectar a Wi-Fi ahora. Reintentando en segundo plano.");
  }

  // 3. Configurar Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  xTaskCreatePinnedToCore(tareaFirebase, "TaskFirebase", 10000, NULL, 1, NULL, 0);
  Serial.println("--- INICIO COMPLETADO ---\n");
}

void loop() {
  if (lora_conectado) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      String payload = "";
      while (LoRa.available()) {
        payload += (char)LoRa.read();
      }
      Serial.print("📡 [LoRa Recibido] [RSSI: ");
      Serial.print(LoRa.packetRssi());
      Serial.println(" dBm]: " + payload);

      encolarTrama(payload);
    }
  }

  unsigned long currentMillis = millis();
  if (currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;
    leerYEncolarSensoresLocales();
  }

  delay(10);
}
