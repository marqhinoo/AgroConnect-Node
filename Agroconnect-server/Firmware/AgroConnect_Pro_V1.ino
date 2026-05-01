#include <WiFi.h>
#include <PubSubClient.h>
#include <LoRa.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_BMP280.h>
#include <Adafruit_SHT31.h>

// --- Configuración WiFi y MQTT ---
const char* ssid = "TU_WIFI_RURAL";
const char* password = "TU_PASSWORD";
const char* mqtt_server = "broker.tago.io"; // Ejemplo para TagoIO
const int mqtt_port = 1883;
const char* mqtt_token = "TU_DEVICE_TOKEN"; // El Token que te da la App Web

// --- Configuración LoRa (Sx1278) ---
#define SS 5
#define RST 14
#define DIO0 2

WiFiClient espClient;
PubSubClient client(espClient);
Adafruit_BMP280 bmp;
Adafruit_SHT31 sht30 = Adafruit_SHT31();

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);
  
  bmp.begin(0x76);
  sht30.begin(0x44);

  LoRa.setPins(SS, RST, DIO0);
  LoRa.begin(433E6);

  procesarYEnviar();

  // Deep Sleep
  esp_sleep_enable_timer_wakeup(600 * 1000000ULL);
  esp_deep_sleep_start();
}

void procesarYEnviar() {
  // 1. Preparar los datos en JSON
  StaticJsonDocument<256> doc;
  doc["variable"] = "data_completa"; // Formato requerido por muchas Apps
  JsonObject valor = doc.createNestedObject("value");
  valor["temp"] = sht30.readTemperature();
  valor["hum"] = sht30.readHumidity();
  valor["pres"] = bmp.readPressure() / 100.0F;
  valor["vpd"] = calcularVPD(valor["temp"], valor["hum"]);
  
  char buffer[256];
  serializeJson(doc, buffer);

  // 2. Intentar Wi-Fi + MQTT
  WiFi.begin(ssid, password);
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(500);
    intentos++;
  }

  bool enviadoPorMQTT = false;
  if (WiFi.status() == WL_CONNECTED) {
    client.setServer(mqtt_server, mqtt_port);
    if (client.connect("AgroConnect_Node", mqtt_token, "")) {
      if (client.publish("tago/data/post", buffer)) { // Topic tipico de TagoIO
        Serial.println("Datos enviados por MQTT");
        enviadoPorMQTT = true;
      }
    }
  }

  // 3. Si MQTT falló, enviar por LoRa
  if (!enviadoPorMQTT) {
    Serial.println("MQTT/WiFi falló. Enviando por LoRa...");
    LoRa.beginPacket();
    LoRa.print(buffer);
    LoRa.endPacket();
  }
}

float calcularVPD(float t, float h) {
  float vpsat = 0.61078 * exp((17.27 * t) / (t + 237.3));
  return vpsat * (1.0 - (h / 100.0));
}

void loop() {}
