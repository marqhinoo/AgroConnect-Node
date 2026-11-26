package com.agro.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var configRef: DatabaseReference
    private lateinit var infoNodosRef: DatabaseReference

    private var lecturaQuery: Query? = null
    private var lecturaListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null

    private var nodoSeleccionadoActual: String? = null
    private var nodoPreseleccionado: String? = null
    private val listaNodosActual = mutableListOf<String>()

    // Vistas principales
    private lateinit var spinnerNodos: Spinner
    private lateinit var imgWifi: ImageView
    private var etComentarioNodo: EditText? = null
    private lateinit var tvVPD: TextView
    private lateinit var tvTempAire: TextView
    private lateinit var tvHumAire: TextView
    private lateinit var tvPresion: TextView
    private lateinit var tvHum15: TextView
    private lateinit var tvHum40: TextView
    private lateinit var tvTempSuelo: TextView

    // Umbrales agronómicos
    private var tempMaxConfig: Double? = null
    private var tempMinConfig: Double? = null
    private var vpdMaxConfig: Double? = null
    private var vpdMinConfig: Double? = null
    private var humSueloMinConfig: Double? = null
    private var tempRadicularMinConfig: Double? = null
    private var tempRadicularMaxConfig: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"

        // Capturar nodo si viene seleccionado desde ResumenGeneralActivity
        nodoPreseleccionado = intent.getStringExtra("NODO_ID")

        // Referencias directas al UID activo del usuario logueado
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid)
        dbRef = userRef.child("lecturas")
        configRef = userRef.child("configuracion")
        infoNodosRef = userRef.child("nodos_info")

        crearCanalNotificaciones()
        solicitarPermisoNotificaciones()
        escucharConfiguracion()

        // Inicialización de componentes XML
        spinnerNodos = findViewById(R.id.spinnerNodos)
        imgWifi = findViewById(R.id.imgWifi)
        val btnAjustes = findViewById<ImageView>(R.id.btnAjustes)

        etComentarioNodo = findViewById(R.id.etComentarioNodo)
        val btnGuardarComentario = findViewById<Button>(R.id.btnGuardarComentario)

        tvVPD = findViewById(R.id.tvVPD)
        tvTempAire = findViewById(R.id.tvTempAire)
        tvHumAire = findViewById(R.id.tvHumAire)
        tvPresion = findViewById(R.id.tvPresion)
        tvHum15 = findViewById(R.id.tvHum15)
        tvHum40 = findViewById(R.id.tvHum40)
        tvTempSuelo = findViewById(R.id.tvTempSuelo)
        val btnIrHistorial = findViewById<Button>(R.id.btnIrHistorial)

        val cardVPD = findViewById<MaterialCardView>(R.id.cardVPD)
        val cardTempAire = findViewById<MaterialCardView>(R.id.cardTempAire)
        val cardHumAire = findViewById<MaterialCardView>(R.id.cardHumAire)
        val cardPresion = findViewById<MaterialCardView>(R.id.cardPresion)
        val cardHum15 = findViewById<MaterialCardView>(R.id.cardHum15)
        val cardHum40 = findViewById<MaterialCardView>(R.id.cardHum40)
        val cardTempSuelo = findViewById<MaterialCardView>(R.id.cardTempSuelo)

        btnGuardarComentario?.setOnClickListener {
            val idNodo = nodoSeleccionadoActual ?: return@setOnClickListener
            val textoComentario = etComentarioNodo?.text?.toString()?.trim() ?: ""
            infoNodosRef.child(idNodo).child("comentario").setValue(textoComentario)
                .addOnSuccessListener {
                    Toast.makeText(this, "Función guardada para $idNodo", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        cargarNodosEnSpinner()

        btnAjustes?.setOnClickListener {
            val intent = Intent(this, ConfiguracionActivity::class.java)
            startActivity(intent)
        }

        btnIrHistorial?.setOnClickListener {
            val intent = Intent(this, HistorialActivity::class.java)
            startActivity(intent)
        }

        cardVPD?.setOnClickListener { abrirDetalleGrafico("vpd", "ESTRÉS (VPD)") }
        cardTempAire?.setOnClickListener { abrirDetalleGrafico("tempAire", "TEMPERATURA AIRE") }
        cardHumAire?.setOnClickListener { abrirDetalleGrafico("humAire", "HUMEDAD AIRE") }
        cardPresion?.setOnClickListener { abrirDetalleGrafico("presion", "PRESIÓN ATMOSFÉRICA") }
        cardHum15?.setOnClickListener { abrirDetalleGrafico("hum15", "HUMEDAD 15 CM") }
        cardHum40?.setOnClickListener { abrirDetalleGrafico("hum40", "HUMEDAD 40 CM") }
        cardTempSuelo?.setOnClickListener { abrirDetalleGrafico("tempSuelo", "TEMPERATURA SUELO") }
    }

    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CANAL_ALERTAS_AGRO",
                "Alertas Agronómicas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de estrés hídrico, sanidad y mantenimiento de nodo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacionEstado(titulo: String, mensaje: String) {
        val builder = NotificationCompat.Builder(this, "CANAL_ALERTAS_AGRO")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun escucharConfiguracion() {
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    tempMaxConfig = snapshot.child("tempMax").value?.toString()?.toDoubleOrNull()
                    tempMinConfig = snapshot.child("tempMin").value?.toString()?.toDoubleOrNull()
                    vpdMaxConfig = snapshot.child("vpdMax").value?.toString()?.toDoubleOrNull()
                    vpdMinConfig = snapshot.child("vpdMin").value?.toString()?.toDoubleOrNull()
                    humSueloMinConfig = snapshot.child("humSueloMin").value?.toString()?.toDoubleOrNull()
                    tempRadicularMinConfig = snapshot.child("tempRadicularMin").value?.toString()?.toDoubleOrNull()
                    tempRadicularMaxConfig = snapshot.child("tempRadicularMax").value?.toString()?.toDoubleOrNull()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error al cargar configuración: ${error.message}")
            }
        }
        configRef.addValueEventListener(configListener as ValueEventListener)
    }

    private fun cargarNodosEnSpinner() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevosNodos = mutableListOf<String>()
                for (child in snapshot.children) {
                    child.key?.let { nuevosNodos.add(it) }
                }

                if (nuevosNodos.isNotEmpty()) {
                    // Si la lista de nodos es idéntica a la que ya está cargada, no reiniciamos el Spinner
                    if (nuevosNodos == listaNodosActual) return

                    listaNodosActual.clear()
                    listaNodosActual.addAll(nuevosNodos)

                    val adapter = object : ArrayAdapter<String>(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        listaNodosActual
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getView(position, convertView, parent)
                            (v as? TextView)?.apply {
                                setTextColor(Color.WHITE)
                                textSize = 16f
                            }
                            return v
                        }

                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getDropDownView(position, convertView, parent)
                            (v as? TextView)?.apply {
                                setTextColor(Color.WHITE)
                                setBackgroundColor(Color.parseColor("#121417"))
                                setPadding(20, 20, 20, 20)
                            }
                            return v
                        }
                    }

                    spinnerNodos.adapter = adapter

                    // Recuperar posición previa o seleccionar el nodo enviado desde ResumenGeneralActivity
                    val indicePrevio = listaNodosActual.indexOf(nodoSeleccionadoActual)
                    val indicePreseleccionado = nodoPreseleccionado?.let { listaNodosActual.indexOf(it) } ?: -1

                    if (indicePrevio >= 0) {
                        spinnerNodos.setSelection(indicePrevio, false)
                    } else if (indicePreseleccionado >= 0) {
                        val elegido = listaNodosActual[indicePreseleccionado]
                        nodoSeleccionadoActual = elegido
                        nodoPreseleccionado = null
                        spinnerNodos.setSelection(indicePreseleccionado, false)
                        escucharUltimaLectura(elegido)
                    } else {
                        nodoSeleccionadoActual = listaNodosActual[0]
                        spinnerNodos.setSelection(0, false)
                        escucharUltimaLectura(listaNodosActual[0])
                    }

                    spinnerNodos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val seleccionado = listaNodosActual[position]
                            if (seleccionado != nodoSeleccionadoActual) {
                                nodoSeleccionadoActual = seleccionado
                                escucharUltimaLectura(seleccionado)
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                } else {
                    Toast.makeText(this@MainActivity, "No hay nodos asignados a esta cuenta", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error al consultar Firebase: ${error.message}")
                Toast.makeText(this@MainActivity, "Error Firebase: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun cargarComentarioNodo(idNodo: String) {
        infoNodosRef.child(idNodo).child("comentario")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    etComentarioNodo?.setText(snapshot.value?.toString() ?: "")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainActivity", "Error al cargar comentario: ${error.message}")
                }
            })
    }

    private fun escucharUltimaLectura(idNodo: String) {
        cargarComentarioNodo(idNodo)

        lecturaListener?.let { listener ->
            lecturaQuery?.removeEventListener(listener)
        }

        lecturaQuery = dbRef.child(idNodo).orderByKey().limitToLast(1)
        lecturaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val tempAire = child.child("tempAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val humAire = child.child("humAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val presion = child.child("presion").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val hum15 = child.child("hum15").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val hum40 = child.child("hum40").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val tempSuelo = child.child("tempSuelo").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val vpd = child.child("vpd").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val bateria = child.child("bateria").value?.toString()?.toDoubleOrNull() ?: 0.0

                        tvVPD.text = String.format(Locale.getDefault(), "%.1f", vpd)
                        tvTempAire.text = String.format(Locale.getDefault(), "%.1f °C", tempAire)
                        tvHumAire.text = String.format(Locale.getDefault(), "%.0f %%", humAire)
                        tvPresion.text = String.format(Locale.getDefault(), "%.0f hPa", presion)
                        tvHum15.text = String.format(Locale.getDefault(), "%.0f%%", hum15)
                        tvHum40.text = String.format(Locale.getDefault(), "%.0f%%", hum40)
                        tvTempSuelo.text = String.format(Locale.getDefault(), "%.1f °C", tempSuelo)

                        val verdeNeon = ContextCompat.getColor(this@MainActivity, R.color.green_neon)
                        imgWifi.setColorFilter(verdeNeon)

                        val lecturaData = LecturaData(
                            bateria = bateria,
                            hum15 = hum15.toInt(),
                            hum40 = hum40.toInt(),
                            humAire = humAire,
                            presion = presion,
                            tempAire = tempAire,
                            tempSuelo = tempSuelo,
                            vpd = vpd
                        )
                        evaluarYGuardarAlertasAgronomicas(idNodo, lecturaData)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error en lecturas: ${error.message}")
                Toast.makeText(this@MainActivity, "Error en nodo $idNodo: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        lecturaQuery?.addValueEventListener(lecturaListener as ValueEventListener)
    }

    private fun evaluarYGuardarAlertasAgronomicas(idNodo: String, data: LecturaData) {
        tempMaxConfig?.let { max ->
            if (data.tempAire > max) {
                procesarAlerta("🚨 Alerta de Golpe de Calor", "Temp. Aire en $idNodo de ${data.tempAire}°C (> $max°C). Riesgo de aborto de flores.")
            }
        }
        tempMinConfig?.let { min ->
            if (data.tempAire < min) {
                procesarAlerta("❄️ Alerta Temprana de Helada", "Temp. Aire en $idNodo cayó a ${data.tempAire}°C (< $min°C). Activar protección.")
            }
        }

        humSueloMinConfig?.let { minHum ->
            if (data.hum15 < minHum) {
                procesarAlerta("💧 Alerta Humedad Crítica", "Humedad 15cm en $idNodo cayó a ${data.hum15}% (< $minHum%). Riego urgente requerido.")
            }
        }

        vpdMaxConfig?.let { maxVpd ->
            if (data.vpd > maxVpd) {
                procesarAlerta("☀️ Estrés Hídrico (VPD)", "VPD en $idNodo alcanzó ${data.vpd} kPa (> $maxVpd kPa). Cierre estomático activo.")
            }
        }

        vpdMinConfig?.let { minVpd ->
            if (data.vpd < minVpd && data.humAire > 90.0) {
                procesarAlerta("🍄 Alerta Sanitaria Fúngica", "VPD en $idNodo es ${data.vpd} kPa y humedad ${data.humAire}%. Monitorear hongos.")
            }
        }

        tempRadicularMinConfig?.let { minRad ->
            if (data.tempSuelo < minRad) {
                procesarAlerta("🥶 Bloqueo Nutricional", "Temp. de raíz en $idNodo es ${data.tempSuelo}°C (< $minRad°C). Asimilación suspendida.")
            }
        }
        tempRadicularMaxConfig?.let { maxRad ->
            if (data.tempSuelo > maxRad) {
                procesarAlerta("🔥 Calentamiento Radicular", "Temp. de raíz en $idNodo es ${data.tempSuelo}°C (> $maxRad°C). Riesgo de anoxia.")
            }
        }

        if (data.bateria in 0.1..3.4) {
            procesarAlerta("🔋 Alerta de Hardware / Batería", "Tensión en $idNodo bajo nivel crítico (${data.bateria}V). Limpiar panel solar.")
        }
    }

    private fun procesarAlerta(titulo: String, mensaje: String) {
        val prefs = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
        val historialPrevio = prefs.getString("lista_alertas", "") ?: ""

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaHora = formatoFecha.format(Date())
        val nuevaEntrada = "⚠️ [$fechaHora] $titulo: $mensaje"

        if (historialPrevio.contains(nuevaEntrada)) return

        val historialNuevo = if (historialPrevio.isEmpty()) {
            nuevaEntrada
        } else {
            "$historialPrevio|$nuevaEntrada"
        }

        prefs.edit().putString("lista_alertas", historialNuevo).apply()
        lanzarNotificacionEstado(titulo, mensaje)
    }

    private fun abrirDetalleGrafico(variable: String, titulo: String) {
        val intent = Intent(this, DetalleGraficoActivity::class.java).apply {
            putExtra("VARIABLE", variable)
            putExtra("TITULO", titulo)
            putExtra("NODO_ID", nodoSeleccionadoActual)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        lecturaListener?.let { listener ->
            lecturaQuery?.removeEventListener(listener)
        }
        configListener?.let { listener ->
            configRef.removeEventListener(listener)
        }
    }
}