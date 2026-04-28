# AgroConnect - Nodo de Monitoreo Agrícola Profesional

AgroConnect es un sistema de monitoreo de precisión diseñado para el agro, basado en el microcontrolador **ESP32-WROOM-32U**. Este nodo está optimizado para recolectar datos críticos del suelo y del ambiente, procesarlos bajo una estrategia de **ultra bajo consumo** (24.5 mAh/día) y transmitirlos mediante **Wi-Fi de alta ganancia o LoRa**.

## 🚀 Características Principales
- **Conectividad Híbrida:** Wi-Fi mediante antena externa de 8dBi y soporte para LoRa (Sx1278).
- **Eficiencia Energética:** Gestión mediante panel solar de 3W, batería 18650 y regulador LDO de baja caída.
- **Monitoreo Ambiental Completo:** Cálculo de VPD utilizando presión atmosférica (BMP280) y sensor de alta precisión SHT30.
- **Análisis de Suelo:** Humedad a dos profundidades (Capacitivos v1.2) y temperatura de raíz (DS18B20).

## 🛠️ Lista de Componentes (Hardware)
| Componente | Función |
| :--- | :--- |
| **ESP32-WROOM-32U** | Microcontrolador central con conector U.FL. |
| **LoRa Sx1278** | Módulo de comunicación de largo alcance (433MHz). |
| **Sensor SHT30** | Humedad y temperatura ambiente (Alta precisión). |
| **Sensor BMP280** | Presión atmosférica y temperatura. |
| **2 x Humedad Suelo v1.2** | Medición capacitiva (anti-corrosión) a 15cm y 40cm. |
| **Sensor DS18B20** | Temperatura de raíz (sumergible). |
| **Panel Solar (6V/3W)** | Generación de energía. |
| **Módulo TP4056 + BMS** | Cargador de batería con protección integrada. |
| **Batería 18650** | Almacenamiento de energía (Litio). |
| **LDO HT7333-A** | Regulador a 3.3V de ultra bajo consumo. |
| **Diodo 1N5817** | Protección anti-retorno para el panel solar. |
| **Capacitor 10µF** | Estabilización de voltaje para picos de transmisión. |

## 📐 Diagrama de Conexión (I2C & SPI)
- **Protocolo I2C (Compartido):** SHT30 y BMP280 conectados a los pines GPIO 21 (SDA) y GPIO 22 (SCL).
- **Protocolo SPI (LoRa):** Conexión del módulo Sx1278 para transmisiones de larga distancia.
- **Entradas Analógicas:** Sensores capacitivos a GPIO 32 y 34.
- **One-Wire:** DS18B20 a GPIO 4 (con resistencia pull-up).

## 📁 Estructura del Repositorio
- `/Firmware`: Código fuente optimizado para Deep Sleep.
- `/Hardware`: Esquemas de conexión y lista de materiales (BOM).
- `/Docs`: Guías de protección (Radomos de PVC) y calibración.

## 📄 Notas de Implementación
Para garantizar que el sensor **SHT30** y el **BMP280** no se mojen, se deben instalar en un protector de radiación solar (Stevenson screen) en la parte exterior del gabinete IP65, asegurando que el cable de la antena de 8dBi esté correctamente sellado con cinta vulcanizada.

---
**AgroConnect** - Optimizando cada gota de agua mediante datos.
