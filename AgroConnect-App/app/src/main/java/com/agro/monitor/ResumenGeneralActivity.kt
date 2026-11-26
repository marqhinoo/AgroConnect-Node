package com.agro.monitor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ResumenNodoItem(
    val nodoId: String,
    val comentario: String,
    val tempAire: Double,
    val humAire: Double,
    val presion: Double,
    val hum15: Int,
    val hum40: Int,
    val tempSuelo: Double,
    val vpd: Double,
    val bateria: Double,
    val fechaLegible: String,
    val problemas: List<String>
)

class ResumenGeneralActivity : AppCompatActivity() {

    private lateinit var dbLecturasRef: DatabaseReference
    private lateinit var dbConfigRef: DatabaseReference
    private lateinit var dbInfoNodosRef: DatabaseReference

    private var lecturasListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null
    private var infoNodosListener: ValueEventListener? = null

    private var ultimoSnapshotLecturas: DataSnapshot? = null
    private val mapaComentarios = mutableMapOf<String, String>()
    private val listaResumenActual = mutableListOf<ResumenNodoItem>()

    // Umbrales configurados
    private var tempMaxConfig: Double? = null
    private var tempMinConfig: Double? = null
    private var vpdMaxConfig: Double? = null
    private var vpdMinConfig: Double? = null
    private var humSueloMinConfig: Double? = null
    private var tempRadicularMinConfig: Double? = null
    private var tempRadicularMaxConfig: Double? = null

    // Vistas
    private lateinit var tvTotalNodos: TextView
    private lateinit var cardEstadoAlertas: MaterialCardView
    private lateinit var tvTituloAlertasGeneral: TextView
    private lateinit var tvDetalleAlertasGeneral: TextView
    private lateinit var tvPromVpd: TextView
    private lateinit var tvPromTempAire: TextView
    private lateinit var tvPromHumAire: TextView
    private lateinit var tvPromTempSuelo: TextView
    private lateinit var tvPromHum15: TextView
    private lateinit var tvPromHum40: TextView
    private lateinit var tvPromPresion: TextView
    private lateinit var tvPromBateria: TextView
    private lateinit var contenedorNodosResumen: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resumen_general)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid)
        dbLecturasRef = userRef.child("lecturas")
        dbConfigRef = userRef.child("configuracion")
        dbInfoNodosRef = userRef.child("nodos_info")

        tvTotalNodos = findViewById(R.id.tvTotalNodos)
        cardEstadoAlertas = findViewById(R.id.cardEstadoAlertas)
        tvTituloAlertasGeneral = findViewById(R.id.tvTituloAlertasGeneral)
        tvDetalleAlertasGeneral = findViewById(R.id.tvDetalleAlertasGeneral)
        tvPromVpd = findViewById(R.id.tvPromVpd)
        tvPromTempAire = findViewById(R.id.tvPromTempAire)
        tvPromHumAire = findViewById(R.id.tvPromHumAire)
        tvPromTempSuelo = findViewById(R.id.tvPromTempSuelo)
        tvPromHum15 = findViewById(R.id.tvPromHum15)
        tvPromHum40 = findViewById(R.id.tvPromHum40)
        tvPromPresion = findViewById(R.id.tvPromPresion)
        tvPromBateria = findViewById(R.id.tvPromBateria)
        contenedorNodosResumen = findViewById(R.id.contenedorNodosResumen)

        findViewById<ImageView>(R.id.btnAjustesGeneral).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        findViewById<Button>(R.id.btnIrDetalleNodos).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.btnExportarExcel).setOnClickListener {
            exportarNodosAExcel()
        }

        // Configurar apertura de gráfico detallado por variable al tocar cada tarjeta
        configurarClicksTarjetasVariables()
        iniciarListenersFirebase()
    }

    private fun configurarClicksTarjetasVariables() {
        findViewById<MaterialCardView>(R.id.cardPromVpd).setOnClickListener {
            abrirGraficoGeneralVariable("vpd", "DÉFICIT DE PRESIÓN DE VAPOR (VPD)")
        }
        findViewById<MaterialCardView>(R.id.cardPromTempAire).setOnClickListener {
            abrirGraficoGeneralVariable("tempAire", "TEMPERATURA AIRE")
        }
        findViewById<MaterialCardView>(R.id.cardPromHumAire).setOnClickListener {
            abrirGraficoGeneralVariable("humAire", "HUMEDAD AIRE")
        }
        findViewById<MaterialCardView>(R.id.cardPromTempSuelo).setOnClickListener {
            abrirGraficoGeneralVariable("tempSuelo", "TEMPERATURA SUELO")
        }
        findViewById<MaterialCardView>(R.id.cardPromHum15).setOnClickListener {
            abrirGraficoGeneralVariable("hum15", "HUMEDAD SUELO 15CM")
        }
        findViewById<MaterialCardView>(R.id.cardPromHum40).setOnClickListener {
            abrirGraficoGeneralVariable("hum40", "HUMEDAD SUELO 40CM")
        }
        findViewById<MaterialCardView>(R.id.cardPromPresion).setOnClickListener {
            abrirGraficoGeneralVariable("presion", "PRESIÓN ATMOSFÉRICA")
        }
        findViewById<MaterialCardView>(R.id.cardPromBateria).setOnClickListener {
            abrirGraficoGeneralVariable("bateria", "VOLTAJE BATERÍA")
        }
    }

    private fun abrirGraficoGeneralVariable(variable: String, titulo: String) {
        val intent = Intent(this, DetalleGraficoActivity::class.java).apply {
            putExtra("VARIABLE", variable)
            putExtra("TITULO", titulo)
            putExtra("NODO_ID", "TODOS")
        }
        startActivity(intent)
    }

    private fun iniciarListenersFirebase() {
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tempMaxConfig = snapshot.child("tempMax").value?.toString()?.toDoubleOrNull()
                tempMinConfig = snapshot.child("tempMin").value?.toString()?.toDoubleOrNull()
                vpdMaxConfig = snapshot.child("vpdMax").value?.toString()?.toDoubleOrNull()
                vpdMinConfig = snapshot.child("vpdMin").value?.toString()?.toDoubleOrNull()
                humSueloMinConfig = snapshot.child("humSueloMin").value?.toString()?.toDoubleOrNull()
                tempRadicularMinConfig = snapshot.child("tempRadicularMin").value?.toString()?.toDoubleOrNull()
                tempRadicularMaxConfig = snapshot.child("tempRadicularMax").value?.toString()?.toDoubleOrNull()
                procesarYActualizarVista()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ResumenGeneral", "Error config: ${error.message}")
            }
        }
        dbConfigRef.addValueEventListener(configListener!!)

        infoNodosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mapaComentarios.clear()
                for (nodo in snapshot.children) {
                    val id = nodo.key ?: continue
                    val comentario = nodo.child("comentario").value?.toString() ?: ""
                    mapaComentarios[id] = comentario
                }
                procesarYActualizarVista()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ResumenGeneral", "Error info nodos: ${error.message}")
            }
        }
        dbInfoNodosRef.addValueEventListener(infoNodosListener!!)

        lecturasListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimoSnapshotLecturas = snapshot
                procesarYActualizarVista()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ResumenGeneral", "Error lecturas: ${error.message}")
            }
        }
        dbLecturasRef.addValueEventListener(lecturasListener!!)
    }

    private fun procesarYActualizarVista() {
        val snapshot = ultimoSnapshotLecturas ?: return
        val locale = Locale.getDefault()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale)

        listaResumenActual.clear()

        for (nodoSnap in snapshot.children) {
            val idNodo = nodoSnap.key ?: continue
            val ultimaLectura = nodoSnap.children.lastOrNull() ?: continue

            val tempAire = ultimaLectura.child("tempAire").value?.toString()?.toDoubleOrNull() ?: 0.0
            val humAire = ultimaLectura.child("humAire").value?.toString()?.toDoubleOrNull() ?: 0.0
            val presion = ultimaLectura.child("presion").value?.toString()?.toDoubleOrNull() ?: 0.0
            val hum15 = ultimaLectura.child("hum15").value?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val hum40 = ultimaLectura.child("hum40").value?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val tempSuelo = ultimaLectura.child("tempSuelo").value?.toString()?.toDoubleOrNull() ?: 0.0
            val vpd = ultimaLectura.child("vpd").value?.toString()?.toDoubleOrNull() ?: 0.0
            val bateria = ultimaLectura.child("bateria").value?.toString()?.toDoubleOrNull() ?: 0.0

            val millis = extraerTimestampLectura(ultimaLectura)
            val fechaLegible = sdf.format(Date(millis))
            val comentario = mapaComentarios[idNodo].orEmpty()

            val problemas = evaluarProblemasNodo(tempAire, humAire, hum15, tempSuelo, vpd, bateria)

            listaResumenActual.add(
                ResumenNodoItem(
                    nodoId = idNodo,
                    comentario = comentario,
                    tempAire = tempAire,
                    humAire = humAire,
                    presion = presion,
                    hum15 = hum15,
                    hum40 = hum40,
                    tempSuelo = tempSuelo,
                    vpd = vpd,
                    bateria = bateria,
                    fechaLegible = fechaLegible,
                    problemas = problemas
                )
            )
        }

        if (listaResumenActual.isEmpty()) {
            tvTotalNodos.text = "Sin nodos registrados"
            contenedorNodosResumen.removeAllViews()
            return
        }

        // 1. Actualizar Promedios Globales en las 8 tarjetas
        val n = listaResumenActual.size
        tvTotalNodos.text = "$n nodo(s) monitoreado(s)"
        tvPromVpd.text = String.format(locale, "%.2f kPa", listaResumenActual.sumOf { it.vpd } / n)
        tvPromTempAire.text = String.format(locale, "%.1f °C", listaResumenActual.sumOf { it.tempAire } / n)
        tvPromHumAire.text = String.format(locale, "%.0f %%", listaResumenActual.sumOf { it.humAire } / n)
        tvPromTempSuelo.text = String.format(locale, "%.1f °C", listaResumenActual.sumOf { it.tempSuelo } / n)
        tvPromHum15.text = String.format(locale, "%.0f %%", listaResumenActual.sumOf { it.hum15.toDouble() } / n)
        tvPromHum40.text = String.format(locale, "%.0f %%", listaResumenActual.sumOf { it.hum40.toDouble() } / n)
        tvPromPresion.text = String.format(locale, "%.0f hPa", listaResumenActual.sumOf { it.presion } / n)
        tvPromBateria.text = String.format(locale, "%.2f V", listaResumenActual.sumOf { it.bateria } / n)

        // 2. Actualizar Banner de Diagnóstico de Problemas
        val nodosConFallas = listaResumenActual.filter { it.problemas.isNotEmpty() }
        if (nodosConFallas.isEmpty()) {
            cardEstadoAlertas.setCardBackgroundColor(Color.parseColor("#1A2421"))
            cardEstadoAlertas.strokeColor = Color.parseColor("#2E7D5B")
            tvTituloAlertasGeneral.setTextColor(Color.parseColor("#6BE3A0"))
            tvTituloAlertasGeneral.text = "✅ TODOS LOS NODOS EN RANGO ÓPTIMO"
            tvDetalleAlertasGeneral.text = "Ningún parámetro supera los umbrales máximos o mínimos configurados."
        } else {
            cardEstadoAlertas.setCardBackgroundColor(Color.parseColor("#2B191B"))
            cardEstadoAlertas.strokeColor = Color.parseColor("#E55353")
            tvTituloAlertasGeneral.setTextColor(Color.parseColor("#FF7B7B"))
            tvTituloAlertasGeneral.text = "⚠️ ${nodosConFallas.size} NODO(S) CON PROBLEMAS DETECTADOS"

            val resumenFallos = nodosConFallas.joinToString("\n") { item ->
                val etiqueta = if (item.comentario.isNotBlank()) "${item.nodoId} (${item.comentario})" else item.nodoId
                "• $etiqueta: ${item.problemas.joinToString(", ")}"
            }
            tvDetalleAlertasGeneral.text = resumenFallos
        }

        // 3. Renderizar Tarjetas Individuales en el Resumen
        renderizarTarjetasNodos(locale)
    }

    private fun evaluarProblemasNodo(
        tempAire: Double,
        humAire: Double,
        hum15: Int,
        tempSuelo: Double,
        vpd: Double,
        bateria: Double
    ): List<String> {
        val fallos = mutableListOf<String>()
        tempMaxConfig?.let { if (tempAire > it) fallos.add("Temp. aire alta (${tempAire}°C > ${it}°C)") }
        tempMinConfig?.let { if (tempAire < it) fallos.add("Riesgo helada (${tempAire}°C < ${it}°C)") }
        humSueloMinConfig?.let { if (hum15 < it) fallos.add("Suelo seco 15cm ($hum15% < ${it.toInt()}%)") }
        vpdMaxConfig?.let { if (vpd > it) fallos.add("VPD alto ($vpd > $it kPa)") }
        vpdMinConfig?.let { if (vpd < it && humAire > 90.0) fallos.add("Riesgo fúngico (VPD $vpd kPa)") }
        tempRadicularMinConfig?.let { if (tempSuelo < it) fallos.add("Suelo frío (${tempSuelo}°C < ${it}°C)") }
        tempRadicularMaxConfig?.let { if (tempSuelo > it) fallos.add("Suelo caliente (${tempSuelo}°C > ${it}°C)") }
        if (bateria in 0.1..3.4) fallos.add("Batería baja (${bateria}V)")
        return fallos
    }

    private fun renderizarTarjetasNodos(locale: Locale) {
        contenedorNodosResumen.removeAllViews()
        val densidad = resources.displayMetrics.density
        val margenInferior = (10 * densidad).toInt()
        val paddingCard = (14 * densidad).toInt()

        for (item in listaResumenActual) {
            val tieneProblema = item.problemas.isNotEmpty()

            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = margenInferior
                }
                radius = 12 * densidad
                setCardBackgroundColor(Color.parseColor("#1A1F26"))
                strokeWidth = (1 * densidad).toInt()
                strokeColor = Color.parseColor(if (tieneProblema) "#E55353" else "#2A3441")
                setOnClickListener {
                    val intent = Intent(this@ResumenGeneralActivity, MainActivity::class.java).apply {
                        putExtra("NODO_ID", item.nodoId)
                    }
                    startActivity(intent)
                }
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(paddingCard, paddingCard, paddingCard, paddingCard)
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tvNombre = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = item.nodoId
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val tvEstado = TextView(this).apply {
                text = if (tieneProblema) "⚠️ ALERTA" else "✅ NORMAL"
                setTextColor(Color.parseColor(if (tieneProblema) "#FF7B7B" else "#6BE3A0"))
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            headerRow.addView(tvNombre)
            headerRow.addView(tvEstado)
            layout.addView(headerRow)

            val tvFuncion = TextView(this).apply {
                text = if (item.comentario.isNotBlank()) "📌 Función: ${item.comentario}" else "📌 Sin comentario de función asignado"
                setTextColor(Color.parseColor(if (item.comentario.isNotBlank()) "#A3B8CC" else "#5A6978"))
                textSize = 13f
                setPadding(0, (4 * densidad).toInt(), 0, (6 * densidad).toInt())
            }
            layout.addView(tvFuncion)

            val tvMetricas = TextView(this).apply {
                text = String.format(
                    locale,
                    "Aire: %.1f°C | Hum: %.0f%% | VPD: %.2f kPa | Suelo 15cm: %d%%",
                    item.tempAire, item.humAire, item.vpd, item.hum15
                )
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
            }
            layout.addView(tvMetricas)

            if (tieneProblema) {
                val tvFallo = TextView(this).apply {
                    text = "Motivo: ${item.problemas.joinToString(" • ")}"
                    setTextColor(Color.parseColor("#FF7B7B"))
                    textSize = 12f
                    setPadding(0, (4 * densidad).toInt(), 0, 0)
                }
                layout.addView(tvFallo)
            }

            card.addView(layout)
            contenedorNodosResumen.addView(card)
        }
    }

    private fun extraerTimestampLectura(lectura: DataSnapshot): Long {
        val tsRaw = lectura.child("timestamp").value?.toString()?.toLongOrNull()
        if (tsRaw != null && tsRaw >= 1_000_000_000_000L) {
            return tsRaw
        }
        return decodificarTimestampPushId(lectura.key ?: "") ?: System.currentTimeMillis()
    }

    private fun exportarNodosAExcel() {
        val snapshot = ultimoSnapshotLecturas
        if (snapshot == null || listaResumenActual.isEmpty()) {
            Toast.makeText(this, "No hay datos de nodos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val locale = Locale.getDefault()
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale)
            val sb = StringBuilder()

            // BOM UTF-8 para que Microsoft Excel reconozca tildes y símbolos (°C)
            sb.append("\uFEFF")

            // =================================================================
            // SECCIÓN 1: ESTADO ACTUAL POR NODO Y PROMEDIO GLOBAL
            // =================================================================
            sb.appendLine("RESUMEN ACTUAL DE LA RED")
            sb.appendLine("Nodo ID;Funcion / Comentario;Estado;Problemas Detectados;Temp Aire (C);Hum Aire (%);VPD (kPa);Presion (hPa);Hum Suelo 15cm (%);Hum Suelo 40cm (%);Temp Suelo (C);Bateria (V);Ultima Lectura")

            for (item in listaResumenActual) {
                val estado = if (item.problemas.isEmpty()) "NORMAL" else "ALERTA"
                val detalleProblemas = if (item.problemas.isEmpty()) "Ninguno" else item.problemas.joinToString(" | ")
                val comentarioLimpio = item.comentario.replace(";", ",").replace("\n", " ")

                sb.appendLine(
                    String.format(
                        locale,
                        "%s;%s;%s;%s;%.1f;%.0f;%.2f;%.0f;%d;%d;%.1f;%.2f;%s",
                        item.nodoId,
                        comentarioLimpio,
                        estado,
                        detalleProblemas,
                        item.tempAire,
                        item.humAire,
                        item.vpd,
                        item.presion,
                        item.hum15,
                        item.hum40,
                        item.tempSuelo,
                        item.bateria,
                        item.fechaLegible
                    )
                )
            }

            val n = listaResumenActual.size
            sb.appendLine(
                String.format(
                    locale,
                    "PROMEDIO GLOBAL;Todos los nodos (%d);-;-;%.1f;%.0f;%.2f;%.0f;%.0f;%.0f;%.1f;%.2f;-",
                    n,
                    listaResumenActual.sumOf { it.tempAire } / n,
                    listaResumenActual.sumOf { it.humAire } / n,
                    listaResumenActual.sumOf { it.vpd } / n,
                    listaResumenActual.sumOf { it.presion } / n,
                    listaResumenActual.sumOf { it.hum15.toDouble() } / n,
                    listaResumenActual.sumOf { it.hum40.toDouble() } / n,
                    listaResumenActual.sumOf { it.tempSuelo } / n,
                    listaResumenActual.sumOf { it.bateria } / n
                )
            )

            // Separación entre el resumen y la tabla histórica
            sb.appendLine()
            sb.appendLine()

            // =================================================================
            // SECCIÓN 2: HISTORIAL COMPLETO DE LECTURAS DE TODOS LOS NODOS
            // =================================================================
            sb.appendLine("HISTORIAL COMPLETO DE LECTURAS")
            sb.appendLine("Nodo ID;Funcion / Comentario;Fecha y Hora;Estado;Problemas Detectados;Temp Aire (C);Hum Aire (%);VPD (kPa);Presion (hPa);Hum Suelo 15cm (%);Hum Suelo 40cm (%);Temp Suelo (C);Bateria (V)")

            var totalRegistrosHistorial = 0

            for (nodoSnap in snapshot.children) {
                val idNodo = nodoSnap.key ?: continue
                val comentarioLimpio = (mapaComentarios[idNodo] ?: "")
                    .replace(";", ",")
                    .replace("\n", " ")

                // Ordenar cronológicamente todas las lecturas del nodo
                val lecturasOrdenadas = nodoSnap.children
                    .map { lectura -> Pair(extraerTimestampLectura(lectura), lectura) }
                    .sortedBy { it.first }

                for ((millis, lectura) in lecturasOrdenadas) {
                    val tempAire = lectura.child("tempAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val humAire = lectura.child("humAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val presion = lectura.child("presion").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val hum15 = lectura.child("hum15").value?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                    val hum40 = lectura.child("hum40").value?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                    val tempSuelo = lectura.child("tempSuelo").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val vpd = lectura.child("vpd").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val bateria = lectura.child("bateria").value?.toString()?.toDoubleOrNull() ?: 0.0

                    val fechaLectura = sdf.format(Date(millis))
                    val problemas = evaluarProblemasNodo(tempAire, humAire, hum15, tempSuelo, vpd, bateria)
                    val estado = if (problemas.isEmpty()) "NORMAL" else "ALERTA"
                    val detalleProblemas = if (problemas.isEmpty()) "Ninguno" else problemas.joinToString(" | ")

                    sb.appendLine(
                        String.format(
                            locale,
                            "%s;%s;%s;%s;%s;%.1f;%.0f;%.2f;%.0f;%d;%d;%.1f;%.2f",
                            idNodo,
                            comentarioLimpio,
                            fechaLectura,
                            estado,
                            detalleProblemas,
                            tempAire,
                            humAire,
                            vpd,
                            presion,
                            hum15,
                            hum40,
                            tempSuelo,
                            bateria
                        )
                    )
                    totalRegistrosHistorial++
                }
            }

            val marcaTiempo = SimpleDateFormat("yyyyMMdd_HHmm", locale).format(Date())
            val carpetaExport = File(cacheDir, "reportes").apply { mkdirs() }
            val archivoCsv = File(carpetaExport, "AgroConnect_Historial_Completo_$marcaTiempo.csv")

            FileOutputStream(archivoCsv).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }

            Toast.makeText(
                this,
                "Exportados $totalRegistrosHistorial registros históricos",
                Toast.LENGTH_SHORT
            ).show()

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivoCsv)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Completo AgroConnect ($totalRegistrosHistorial lecturas)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Abrir o compartir Excel completo"))

        } catch (e: Exception) {
            Log.e("ResumenGeneral", "Error al exportar Excel completo: ${e.message}")
            Toast.makeText(this, "Error al generar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun decodificarTimestampPushId(pushId: String): Long? {
        if (pushId.length < 8) return null
        val chars = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
        var timestamp = 0L
        for (i in 0 until 8) {
            val index = chars.indexOf(pushId[i])
            if (index == -1) return null
            timestamp = timestamp * 64L + index
        }
        return timestamp
    }

    override fun onDestroy() {
        super.onDestroy()
        lecturasListener?.let { dbLecturasRef.removeEventListener(it) }
        configListener?.let { dbConfigRef.removeEventListener(it) }
        infoNodosListener?.let { dbInfoNodosRef.removeEventListener(it) }
    }
}