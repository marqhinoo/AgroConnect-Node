#include <Arduino.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_SHT31.h>
#include <Adafruit_BMP280.h>
#include <SPI.h>
#include <LoRa.h>
#include <WiFi.h>
#include <time.h>
#include <Preferences.h>
#include <FirebaseESP32.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Credenciales por defecto (si aún no se configuró nada por BLE)
#define DEFAULT_WIFI_SSID "Informatica2043"
#define DEFAULT_WIFI_PASSWORD "Info20432025+"

// Firebase y Nodo
#define FIREBASE_HOST "agroconnect-9b8cd-default-rtdb.firebaseio.com"
#define FIREBASE_AUTH "SiS2adHOeXE8LDWgVjZnP1pA67nQPVxyudmMoPJA"
#define USER_UID "DdaAJqXxIcadkzamNl16yWyy8k13"
#define NODO_CENTRAL_ID "Marco"

// UUIDs Nordic UART Service (Compatible con Serial Bluetooth Terminal)
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

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

// Objetos de Sensores y Memoria
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature ds18b20(&oneWire);
Adafruit_SHT31 sht30 = Adafruit_SHT31();
Adafruit_BMP280 bmp(&Wire);
Preferences preferences;

// Firebase y FreeRTOS
FirebaseData firebaseData;
FirebaseAuth auth;
FirebaseConfig config;
QueueHandle_t colaFirebase;

// Variables de Wi-Fi y BLE
String wifiSSID = "";
String wifiPass = "";
BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic = NULL;
bool bleConectado = false;
volatile bool reconectarWifi = false;

// Variables de Estado, Diagnóstico LoRa y Temporización
bool sht30_conectado = false;
bool bmp280_conectado = false;
bool lora_conectado = false;
unsigned long paquetesLoRaRecibidos = 0;
int ultimoRssiLoRa = 0;
String ultimoNodoLoRa = "Ninguno";
unsigned long previousMillis = 0;
const long interval = 10000;

void enviarMensajeBLE(String mensaje) {
  Serial.println(mensaje);
  if (bleConectado && pTxCharacteristic != NULL) {
    String salida = mensaje + "\n";
    pTxCharacteristic->setValue(salida.c_str());
    pTxCharacteristic->notify();
    delay(25); // Evita pérdida de paquetes en notificaciones consecutivas
  }
}

// Callbacks de conexión BLE
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    bleConectado = true;
    Serial.println("[BLE] Dispositivo conectado");
  }

  void onDisconnect(BLEServer *pServer) {
    bleConectado = false;
    Serial.println("[BLE] Dispositivo desconectado. Reiniciando visibilidad...");
    pServer->startAdvertising();
  }
};

// Callbacks de recepción de comandos por Serial Bluetooth Terminal
class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = String(pCharacteristic->getValue().c_str());
    rxValue.trim();

    if (rxValue.length() == 0) return;

    Serial.println("[BLE RX]: " + rxValue);

    if (rxValue.startsWith("WIFI:")) {
      int comaIndex = rxValue.indexOf(',');
      if (comaIndex > 5) {
        String nuevoSSID = rxValue.substring(5, comaIndex);
        String nuevaPass = rxValue.substring(comaIndex + 1);
        nuevoSSID.trim();
        nuevaPass.trim();

        if (nuevoSSID.length() > 0) {
          wifiSSID = nuevoSSID;
          wifiPass = nuevaPass;

          // Guardar de forma permanente en memoria flash (NVS)
          preferences.begin("wifi-config", false);
          preferences.putString("ssid", wifiSSID);
          preferences.putString("pass", wifiPass);
          preferences.end();

          enviarMensajeBLE("[BLE OK] Guardado -> SSID: " + wifiSSID);
          reconectarWifi = true;
        } else {
          enviarMensajeBLE("[BLE ERROR] El SSID no puede estar vacio.");
        }
      } else {
        enviarMensajeBLE("[BLE ERROR] Formato invalido. Usa: WIFI:NombreRed,Clave");
      }
    } else if (rxValue.equalsIgnoreCase("STATUS")) {
      // 1. Estado de Wi-Fi
      String statusWifi = "[WIFI] ";
      if (WiFi.status() == WL_CONNECTED) {
        statusWifi += "Conectado | SSID: " + wifiSSID +
                      " | IP: " + WiFi.localIP().toString() +
                      " | Senal: " + String(WiFi.RSSI()) + " dBm";
      } else {
        statusWifi += "Desconectado | SSID configurado: " + wifiSSID;
      }
      enviarMensajeBLE(statusWifi);

      // 2. Estado de LoRa
      String statusLoRa = "[LORA] ";
      if (lora_conectado) {
        statusLoRa += "Activo (433MHz / Sync 0xF3) | RX: " + String(paquetesLoRaRecibidos) +
                      " pkts | Ultimo: " + ultimoNodoLoRa;
        if (paquetesLoRaRecibidos > 0) {
          statusLoRa += " (" + String(ultimoRssiLoRa) + " dBm)";
        }
      } else {
        statusLoRa += "ERROR (Modulo no detectado en SPI)";
      }
      enviarMensajeBLE(statusLoRa);

    } else if (rxValue.equalsIgnoreCase("REBOOT")) {
      enviarMensajeBLE("[BLE] Reiniciando Nodo Central...");
      delay(500);
      ESP.restart();
    } else {
      enviarMensajeBLE("[COMANDOS] Envia: WIFI:TuRed,TuClave | STATUS | REBOOT");
    }
  }
};

void iniciarBLE() {
  BLEDevice::init("AgroCentral_BLE");
  BLEDevice::setMTU(185);
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID_TX,
      BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID_RX,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("[OK] BLE listo. Nombre: AgroCentral_BLE");
}

void conectarWiFiActual() {
  WiFi.disconnect(true);
  delay(200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(wifiSSID.c_str(), wifiPass.c_str());

  enviarMensajeBLE("[Wi-Fi] Conectando a: " + wifiSSID + "...");
  int reintentos = 0;
  while (WiFi.status() != WL_CONNECTED && reintentos < 20) {
    delay(500);
    Serial.print(".");
    reintentos++;
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    enviarMensajeBLE("[OK] Wi-Fi Conectado! IP: " + WiFi.localIP().toString());
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  } else {
    enviarMensajeBLE("[WARN] No se pudo conectar a " + wifiSSID + ". Verifica SSID/Clave por BLE.");
  }
}

// Tarea en segundo plano para Firebase (Core 1, 16KB de stack)
void tareaFirebase(void *pvParameters) {
  LecturaNodo datos;
  FirebaseJson json;

  for (;;) {
    if (WiFi.status() != WL_CONNECTED) {
      vTaskDelay(pdMS_TO_TICKS(2000));
      continue;
    }

    if (xQueueReceive(colaFirebase, &datos, pdMS_TO_TICKS(500)) == pdTRUE) {
      String path = "/usuarios/" + String(USER_UID) + "/lecturas/" + String(datos.nodoId);

      json.clear();
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
        Serial.println("[OK] [Firebase] Subido al nodo: " + String(datos.nodoId));
      } else {
        Serial.println("[ERROR] [Firebase]: " + firebaseData.errorReason());
      }
    }

    vTaskDelay(pdMS_TO_TICKS(50));
  }
}

void encolarTrama(String paquete, int rssi) {
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
    enviarMensajeBLE("[LORA WARN] Trama incompleta (" + String(rssi) + " dBm): " + paquete);
    return;
  }

  // Actualizar métricas de diagnóstico LoRa
  paquetesLoRaRecibidos++;
  ultimoRssiLoRa = rssi;
  ultimoNodoLoRa = valores[0];

  enviarMensajeBLE("[LORA RX] Nodo: " + ultimoNodoLoRa + " | RSSI: " + String(rssi) + " dBm");

  LecturaNodo nuevaLectura;
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
    Serial.println("[WARN] Cola Firebase llena. Paquete descartado.");
  }
}

void leerYEncolarSensoresLocales() {
  LecturaNodo local;
  snprintf(local.nodoId, sizeof(local.nodoId), "%s", NODO_CENTRAL_ID);

  local.tempAire = sht30_conectado ? sht30.readTemperature() : 0.0;
  local.humAire = sht30_conectado ? sht30.readHumidity() : 0.0;

  ds18b20.requestTemperatures();
  local.tempSuelo = ds18b20.getTempCByIndex(0);
  if (local.tempSuelo == DEVICE_DISCONNECTED_C) local.tempSuelo = 0.0;

  local.presion = bmp280_conectado ? (bmp.readPressure() / 100.0F) : 0.0;

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

  local.bateria = 4.2;

  if (xQueueSend(colaFirebase, &local, pdMS_TO_TICKS(100)) != pdTRUE) {
    Serial.println("[WARN] Cola Firebase llena (lectura local).");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n--- INICIALIZANDO NODO CENTRAL ---");

  // Cargar credenciales Wi-Fi guardadas (o usar las por defecto)
  preferences.begin("wifi-config", true);
  wifiSSID = preferences.getString("ssid", DEFAULT_WIFI_SSID);
  wifiPass = preferences.getString("pass", DEFAULT_WIFI_PASSWORD);
  preferences.end();

  pinMode(SOIL_15CM_PIN, INPUT);
  pinMode(SOIL_40CM_PIN, INPUT);

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setTimeOut(1000);
  ds18b20.begin();
  ds18b20.setWaitForConversion(false);

  if (sht30.begin(0x44) || sht30.begin(0x45)) sht30_conectado = true;
  if (bmp.begin(0x76) || bmp.begin(0x77)) bmp280_conectado = true;

  colaFirebase = xQueueCreate(20, sizeof(LecturaNodo));

  // 1. Inicializar BLE
  iniciarBLE();

  // 2. Inicializar LoRa
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI);
  LoRa.setPins(LORA_CS, LORA_RST, LORA_DIO0);

  if (LoRa.begin(BAND)) {
    LoRa.setSyncWord(0xF3);
    LoRa.enableCrc();
    lora_conectado = true;
    Serial.println("[OK] Receptor LoRa listo (433 MHz | Sync 0xF3 | CRC Activo)");
  } else {
    Serial.println("[ERROR] No se detecto modulo LoRa en el Nodo Central");
  }

  // 3. Conexión Wi-Fi
  conectarWiFiActual();

  // 4. Configurar Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.timeout.serverResponse = 10 * 1000;
  firebaseData.setBSSLBufferSize(8192, 2048);
  firebaseData.setResponseSize(4096);

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  xTaskCreatePinnedToCore(tareaFirebase, "TaskFirebase", 16384, NULL, 1, NULL, 1);
  Serial.println("--- INICIO COMPLETADO ---\n");
}

void loop() {
  // Si llegaron nuevas credenciales por BLE, reconectar Wi-Fi desde el loop principal
  if (reconectarWifi) {
    reconectarWifi = false;
    conectarWiFiActual();
  }

  if (lora_conectado) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      String payload = "";
      while (LoRa.available()) {
        payload += (char)LoRa.read();
      }
      int rssi = LoRa.packetRssi();
      Serial.print("[LoRa RX] [RSSI: ");
      Serial.print(rssi);
      Serial.println(" dBm]: " + payload);

      encolarTrama(payload, rssi);
    }
  }

  unsigned long currentMillis = millis();
  if (currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;
    leerYEncolarSensoresLocales();
  }

  delay(10);
}
