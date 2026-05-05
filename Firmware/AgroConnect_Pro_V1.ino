#include <WiFi.h>
#include <HTTPClient.h> // Cambiamos MQTT por HTTP
#include <LoRa.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_BMP280.h>
#include <Adafruit_SHT31.h>

// --- Configuración WiFi ---
const char* ssid = "TU_WIFI_RURAL";
const char* password = "TU_PASSWORD";

// --- URL de tu Servidor (Ngrok) ---
const char* serverName = "https://daily-pusher-sapling.ngrok-free.app/update";

// --- Configuración LoRa (Sx1278) ---
#define SS 5
#define RST 14
#define DIO0 2

Adafruit_BMP280 bmp;
Adafruit_SHT31 sht30 = Adafruit_SHT31();

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);
  
  if (!bmp.begin(0x76)) Serial.println("Error BMP280");
  if (!sht30.begin(0x44)) Serial.println("Error SHT30");

  LoRa.setPins(SS, RST, DIO0);
  if (!LoRa.begin(433E6)) Serial.println("Error LoRa");

  procesarYEnviar();

  // Deep Sleep: 10 minutos
  Serial.println("Entrando en Deep Sleep...");
  esp_sleep_enable_timer_wakeup(600 * 1000000ULL);
  esp_deep_sleep_start();
}

void procesarYEnviar() {
  // 1. Lectura de Sensores
  float t = sht30.readTemperature();
  float h = sht30.readHumidity();
  float p = bmp.readPressure() / 100.0F;
  float vpd = calcularVPD(t, h);

  // 2. Preparar el JSON (Formato exacto para tu nuevo index.js)
  StaticJsonDocument<512> doc;
  doc["usuario"] = "admin@agro.com"; // <-- OBLIGATORIO para el multiusuario
  doc["tempAire"] = t;
  doc["humAire"] = h;
  doc["presion"] = p;
  doc["vpd"] = String(vpd, 2); // Lo enviamos con 2 decimales
  doc["hum15"] = random(30, 45); // Aquí irían tus sensores de suelo reales
  doc["hum40"] = random(20, 35);
  doc["tempSuelo"] = t - 2.0;    // Estimación para la prueba
  doc["bateria"] = "4.15";       // Aquí iría la lectura analógica de la batería
  doc["wifiCon"] = 0;            // Se actualiza abajo
  doc["loraCon"] = 0;            // Se actualiza abajo

  // 3. Intentar Conexión WiFi
  WiFi.begin(ssid, password);
  Serial.print("Conectando a WiFi...");
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(500);
    Serial.print(".");
    intentos++;
  }

  bool enviado = false;

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi Conectado");
    doc["wifiCon"] = 1;
    
    HTTPClient http;
    http.begin(serverName); 
    http.addHeader("Content-Type", "application/json");

    String jsonStr;
    serializeJson(doc, jsonStr);

    int httpResponseCode = http.POST(jsonStr);

    if (httpResponseCode > 0) {
      Serial.print("Respuesta del Servidor: ");
      Serial.println(httpResponseCode);
      enviado = true;
    } else {
      Serial.print("Error enviando POST: ");
      Serial.println(httpResponseCode);
    }
    http.end();
  }

  // 4. Si falló WiFi, enviar por LoRa
  if (!enviado) {
    Serial.println("Enviando por LoRa...");
    doc["loraCon"] = 1;
    doc["wifiCon"] = 0;
    
    String loraStr;
    serializeJson(doc, loraStr);
    
    LoRa.beginPacket();
    LoRa.print(loraStr);
    LoRa.endPacket();
  }
}

float calcularVPD(float t, float h) {
  if (isnan(t) || isnan(h)) return 0.0;
  float vpsat = 0.61078 * exp((17.27 * t) / (t + 237.3));
  return vpsat * (1.0 - (h / 100.0));
}

void loop() {
  // Vacío por el Deep Sleep
}
