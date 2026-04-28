# AgroConnect 2.4G - Nodo de Monitoreo Agrícola

AgroConnect es un nodo de monitoreo de precisión diseñado para el agro, basado en el ecosistema ESP32. El sistema permite medir variables críticas como la humedad del suelo a diferentes profundidades y el estrés ambiental mediante el cálculo del **Déficit de Presión de Vapor (VPD)**.

Este proyecto destaca por su **ultra bajo consumo energético** (aprox. 24.5 mAh/día), permitiendo una autonomía total mediante un panel solar de 3W y una batería 18650.

## 🚀 Características Principales
- **Conectividad Dual:** Preparado para trabajar con Wi-Fi (usando antena de alta ganancia de 8dBi) y expansión para LoRa.
- **Eficiencia Energética:** Implementación de modo *Deep Sleep* y regulador LDO HT7333-A para maximizar la vida de la batería.
- **Cálculo de VPD:** Monitoreo de presión atmosférica, temperatura y humedad para detectar estrés transpirativo en tiempo real.
- **Sensores de Suelo:** Monitoreo capacitivo a dos profundidades (15cm y 40cm) para gestión de riego.

## 🛠️ Hardware Utilizado
| Componente | Función |
| :--- | :--- |
| **ESP32-WROOM-32U** | Microcontrolador con conector U.FL para antena externa. |
| **Antena TP-Link 8dBi** | Ganancia de señal Wi-Fi de 2.4GHz para cobertura en lote. |
| **BMP280** | Sensor de Presión Atmosférica y Temperatura. |
| **DHT22** | Sensor de Humedad y Temperatura ambiente. |
| **Capacitivos v1.2** | Sensores de humedad de suelo (resistentes a la corrosión). |
| **TP4056 + BMS** | Cargador de batería de litio con protección. |
| **Panel Solar 6V/3W** | Fuente de energía renovable. |

## 📐 Diagrama de Conexión (Resumen)
- **I2C (BMP280):** SDA -> GPIO 21 | SCL -> GPIO 22.
- **Sensores Humedad:** Entrada Analógica GPIO 32 y 34.
- **Alimentación:** Regulada a 3.3V mediante HT7333-A.
- **Antena:** Conectada vía Pigtail U.FL a RP-SMA.

## 📁 Estructura del Repositorio
- `/Firmware`: Código fuente para Arduino IDE / PlatformIO.
- `/Hardware`: Esquemas de conexión y diseño de PCB (Fritzing/EasyEDA).
- `/Docs`: Guías de calibración de sensores y protección contra lluvia (Radomos DIY).

## 🛡️ Protección Ambiental
Para garantizar la durabilidad en el campo:
1. **Electrónica:** Alojada en gabinete estanco con grado de protección IP65/66.
2. **Antena:** Protegida mediante radomo de PVC transparente a ondas de 2.4GHz.
3. **Sensores:** Instalación con protector de radiación solar y "bucle de goteo" para cables.

## 📄 Licencia
Este proyecto está bajo la Licencia MIT - mira el archivo [LICENSE](LICENSE) para detalles.

---
Desarrollado para la optimización hídrica y tecnológica del sector agropecuario.
