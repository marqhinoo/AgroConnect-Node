# AgroConnect - Sistema de Monitoreo Agrícola Profesional

AgroConnect es un ecosistema IoT de monitoreo de precisión diseñado para el agro, basado en microcontroladores **ESP32-WROOM-32U** y una aplicación móvil nativa en **Android (Kotlin)**. El sistema recolecta variables críticas del suelo y del microclima, las procesa bajo una estrategia de **ultra bajo consumo** (~24.5 mAh/día en nodos remotos) y las sincroniza en tiempo real mediante **LoRa, Wi-Fi y Firebase**.

## 🚀 Características Principales
- **Arquitectura Multi-Nodo:** Nodos sensores remotos de largo alcance mediante LoRa (Sx1278) vinculados a un nodo central (Gateway) con conectividad Wi-Fi y provisión dinámica de red por **Bluetooth Low Energy (BLE)**.
- **Eficiencia Energética:** Gestión autónoma mediante panel solar de 3W, batería 18650 y regulador LDO de baja caída para operación continua en campo.
- **Monitoreo Ambiental Completo:** Cálculo de Déficit de Presión de Vapor (VPD) utilizando presión atmosférica (BMP280) y temperatura/humedad de alta precisión (SHT30).
- **Análisis de Suelo:** Medición de humedad capacitiva a dos profundidades (15 cm y 40 cm) y temperatura radicular (DS18B20).
- **App Móvil AgroConnect (Android):**
  - **Resumen General de Red:** Cálculo de promedios globales en vivo y diagnóstico automático de fallas según umbrales mínimos/máximos configurables.
  - **Gestión por Nodo:** Asignación de comentarios de función por lote/zona y telemetría individual.
  - **Análisis Histórico y Reportes:** Gráficos comparativos multi-nodo por variable, notificaciones push de alertas agronómicas y exportación completa a Excel (CSV).

## 🛠️ Lista de Componentes (Hardware por Nodo)
| Componente | Función |
| :--- | :--- |
| **ESP32-WROOM-32U** | Microcontrolador central con conector U.FL para antena externa. |
| **LoRa Sx1278** | Módulo de comunicación de largo alcance (433 MHz). |
| **Sensor SHT30** | Humedad y temperatura ambiente de alta precisión. |
| **Sensor BMP280** | Presión atmosférica y temperatura. |
| **2 x Humedad Suelo v1.2** | Medición capacitiva anti-corrosión a 15 cm y 40 cm. |
| **Sensor DS18B20** | Temperatura radicular (sonda sumergible). |
| **Panel Solar (6V/3W)** | Generación de energía solar. |
| **Módulo TP4056 + BMS** | Cargador de batería con protección de sobrecarga y descarga. |
| **Batería 18650** | Almacenamiento de energía (Litio). |
| **LDO HT7333-A** | Regulador de voltaje a 3.3V de ultra bajo consumo. |
| **Diodo 1N5817** | Protección anti-retorno para el panel solar. |
| **Capacitor 10µF** | Estabilización de voltaje frente a picos de transmisión RF. |

## 📐 Diagrama de Conexión (I2C, SPI y Analógico)
- **Protocolo I2C (Compartido):** SHT30 y BMP280 conectados a los pines `GPIO 21` (SDA) y `GPIO 22` (SCL).
- **Protocolo SPI (LoRa):** Conexión del módulo Sx1278 para enlaces inalámbricos de larga distancia.
- **Entradas Analógicas:** Sensores capacitivos de humedad de suelo conectados a `GPIO 32` (15 cm) y `GPIO 34` (40 cm).
- **One-Wire:** Sensor de temperatura de suelo DS18B20 conectado a `GPIO 4` (con resistencia pull-up de 4.7 kΩ).

## 📁 Estructura del Repositorio
- `/Firmware`: Código fuente para microcontroladores ESP32 (nodos remotos en Deep Sleep y gateway Wi-Fi/BLE) y proyecto Android Studio (`/Firmware/AgroConnect-App`).
- `/Hardware`: Esquemas eléctricos, diseño de PCB y lista de materiales (BOM).

## 📄 Notas de Implementación
Para garantizar que los sensores **SHT30** y **BMP280** no sufran daños por lluvia o condensación directa, deben instalarse dentro de un protector de radiación solar (*Stevenson screen*) en el exterior del gabinete IP65, sellando los conectores de antena externa con cinta vulcanizada.

---
**AgroConnect** - Optimizando cada gota de agua mediante datos.
