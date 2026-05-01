const express = require("express");
const bodyParser = require("body-parser");
const fs = require("fs-extra");
const path = require("path");
const cors = require("cors");

const app = express();
const PORT = 3000;
const DATA_FILE = path.join(__dirname, "historial_campo.json");

app.use(cors());
app.use(bodyParser.json());
app.use(express.static("public"));

// Asegurar que el archivo existe
const inicializarArchivo = async () => {
    if (!(await fs.pathExists(DATA_FILE))) {
        await fs.writeJson(DATA_FILE, []);
    }
};
inicializarArchivo();

// RUTA POST: Recibe datos del ESP32
app.post("/update", async (req, res) => {
    try {
        const d = req.body; // Atajo para los datos recibidos

        const newData = {
            // Tiempo
            fecha: new Date().toLocaleString("es-AR", { timeZone: "America/Argentina/Buenos_Aires" }),
            hora: new Date().toLocaleTimeString("es-AR", { hour: '2-digit', minute: '2-digit' }),
            
            // Atmosfera (SHT30 + BMP280)
            vpd: d.vpd || "0.00",
            tempAire: d.tempAire || 0,
            humAire: d.humAire || 0,
            presion: d.presion || 0,

            // Suelo (Capacitivos + DS18B20)
            hum15: d.hum15 || 0,
            hum40: d.hum40 || 0,
            tempSuelo: d.tempSuelo || 0,
            // Salinidad eliminada según pedido

            // Telemetría y Red
            bateria: d.bateria || "0.00",
            senal: d.senal || 0,
            wifiCon: d.wifiCon || 0, // 1 = Conectado, 0 = Desconectado
            loraCon: d.loraCon || 0  // 1 = Conectado, 0 = Desconectado
        };

        let historial = await fs.readJson(DATA_FILE);
        historial.unshift(newData);

        // Limitamos a 100 registros para no pesas demasiado
        if (historial.length > 100) historial.pop();

        await fs.writeJson(DATA_FILE, historial);
        
        // Log de consola simplificado
        console.log(`✅ Registro: ${newData.hora} | VPD: ${newData.vpd} | WiFi: ${newData.wifiCon} | LoRa: ${newData.loraCon}`);
        res.status(200).send("OK - Dato guardado");

    } catch (err) {
        console.error("❌ Error:", err);
        res.status(500).send("Error en el servidor");
    }
});

// RUTA GET: Entrega datos a la web
app.get("/api/datos", async (req, res) => {
    try {
        const datos = await fs.readJson(DATA_FILE);
        res.json(datos);
    } catch (err) {
        res.json([]);
    }
});

app.listen(PORT, "0.0.0.0", () => {
    console.log(`🚀 SERVIDOR AGROCONNECT ACTIVO EN PUERTO ${PORT}`);
});