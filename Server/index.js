const express = require("express");
const bodyParser = require("body-parser");
const fs = require("fs-extra");
const path = require("path");
const cors = require("cors");
const jwt = require("jsonwebtoken");

const app = express();
const PORT = 3000;
const DB_FOLDER = path.join(__dirname, "db_usuarios"); // Carpeta para los datos
const SECRET_KEY = "tu_clave_secreta_super_segura";

app.use(cors());
app.use(bodyParser.json());
app.use(express.static("public"));

// Base de datos de usuarios (puedes agregar más aquí)
const usuarios = [
    { email: "admin@agro.com", password: "123", nombre: "Admin Principal" },
    { email: "cliente1@campo.com", password: "456", nombre: "Juan Perez" }
];

// Asegurar que la carpeta de base de datos existe
fs.ensureDirSync(DB_FOLDER);

// --- LOGIN ---
app.post("/api/login", (req, res) => {
    const { email, pass } = req.body;
    const usuario = usuarios.find(u => u.email === email && u.password === pass);

    if (usuario) {
        const token = jwt.sign({ email: usuario.email }, SECRET_KEY, { expiresIn: '24h' });
        res.json({ token });
    } else {
        res.status(401).json({ error: "Credenciales incorrectas" });
    }
});

// --- MIDDLEWARE DE PROTECCIÓN ---
const verificarToken = (req, res, next) => {
    const header = req.headers['authorization'];
    const token = header && header.split(' ')[1];
    if (!token) return res.status(403).send("Acceso denegado");

    jwt.verify(token, SECRET_KEY, (err, user) => {
        if (err) return res.status(403).send("Sesión expirada");
        req.user = user;
        next();
    });
};

// --- RUTA POST (RECIBE DEL ESP32) ---
// El ESP32 debe enviar en el JSON: "usuario": "email_del_dueño"
app.post("/update", async (req, res) => {
    try {
        const d = req.body;
        const userEmail = d.usuario; // El ESP32 nos dice de quién es el dato

        if (!userEmail) return res.status(400).send("Falta el campo 'usuario'");

        const userFile = path.join(DB_FOLDER, `${userEmail}.json`);
        
        const newData = {
            fecha: new Date().toLocaleString("es-AR", { timeZone: "America/Argentina/Buenos_Aires" }),
            hora: new Date().toLocaleTimeString("es-AR", { hour: '2-digit', minute: '2-digit' }),
            vpd: d.vpd || "0.00",
            tempAire: d.tempAire || 0,
            humAire: d.humAire || 0,
            presion: d.presion || 0,
            hum15: d.hum15 || 0,
            hum40: d.hum40 || 0,
            tempSuelo: d.tempSuelo || 0,
            bateria: d.bateria || "0.00",
            wifiCon: d.wifiCon || 0,
            loraCon: d.loraCon || 0 
        };

        // Leer o crear el historial del usuario específico
        let historial = [];
        if (await fs.pathExists(userFile)) {
            historial = await fs.readJson(userFile);
        }

        historial.unshift(newData);
        if (historial.length > 100) historial.pop();

        await fs.writeJson(userFile, historial);
        console.log(`📡 Dato recibido para: ${userEmail} [${newData.hora}]`);
        res.status(200).send("OK");
    } catch (err) {
        console.error(err);
        res.status(500).send("Error de servidor");
    }
});

// --- RUTA GET (ENTREGA DATOS A LA WEB) ---
app.get("/api/datos", verificarToken, async (req, res) => {
    try {
        const userFile = path.join(DB_FOLDER, `${req.user.email}.json`);
        
        if (await fs.pathExists(userFile)) {
            const datos = await fs.readJson(userFile);
            res.json(datos);
        } else {
            res.json([]); // Si el usuario no tiene datos aún
        }
    } catch (err) {
        res.json([]);
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 SERVIDOR AGROCONNECT ACTIVO EN http://192.168.100.66:${PORT}`);
});