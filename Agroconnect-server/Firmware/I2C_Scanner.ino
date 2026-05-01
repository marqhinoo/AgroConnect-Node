#include <Wire.h>

void setup() {
  Wire.begin(21, 22); // SDA en pin 21, SCL en pin 22
  Serial.begin(115200);
  while (!Serial); 
  Serial.println("\nI2C Scanner - AgroConnect");
}

void loop() {
  byte error, address;
  int nDevices;
  Serial.println("Escaneando...");
  nDevices = 0;
  for (address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    error = Wire.endTransmission();
    if (error == 0) {
      Serial.print("Dispositivo encontrado en direccion 0x");
      if (address < 16) Serial.print("0");
      Serial.print(address, HEX);
      Serial.println(" !");
      nDevices++;
    }
  }
  if (nDevices == 0) Serial.println("No se encontraron dispositivos I2C\n");
  else Serial.println("Hecho.\n");
  delay(5000); 
}
