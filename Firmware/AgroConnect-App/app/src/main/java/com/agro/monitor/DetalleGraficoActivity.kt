package com.agro.monitor

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class DetalleGraficoActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var tvPromedio: TextView
    private lateinit var tvValorActual: TextView
    private lateinit var tvRango: TextView
    private lateinit var dbRef: DatabaseReference

    private var variableActual: String = "vpd"
    private var horasFiltro: Int = 24
    private var nodoIdActual: String? = null
    private var markerView: CustomMarkerView? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_grafico)

        variableActual = intent.getStringExtra("VARIABLE") ?: "vpd"
        val titulo = intent.getStringExtra("TITULO") ?: "DETALLE"
        nodoIdActual = intent.getStringExtra("NODO_ID")

        val tvTitulo = findViewById<TextView>(R.id.tvTituloGrafico)
        tvTitulo?.text = if (!nodoIdActual.isNullOrEmpty()) "$titulo\n$nodoIdActual" else titulo

        lineChart = findViewById(R.id.lineChart)
        tvPromedio = findViewById(R.id.tvPromedio)
        tvValorActual = findViewById(R.id.tvValorActual)
        tvRango = findViewById(R.id.tvRango)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"
        dbRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("lecturas")

        configurarGestosGrafico()
        configurarSpinner()

        runnable = object : Runnable {
            override fun run() {
                cargarDatosDesdeFirebase()
                handler.postDelayed(this, 10000)
            }
        }
        handler.post(runnable)
    }

    private fun configurarGestosGrafico() {
        lineChart.apply {
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true

            isDragDecelerationEnabled = true
            dragDecelerationFrictionCoef = 0.9f

            setBackgroundColor(Color.parseColor("#0F141C"))
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false

            axisLeft.apply {
                textColor = Color.parseColor("#5A6978")
                textSize = 10f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1F2938")
                gridLineWidth = 1f
                setDrawAxisLine(false)
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#5A6978")
                textSize = 10f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setAvoidFirstLastClipping(true)
            }
        }
    }

    private fun configurarSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerFiltro)
        val opciones = arrayOf(
            "Última 1h", "Últimas 6h", "Últimas 24h",
            "Últimos 7 días", "Último Mes (30d)", "Últimos 6 Meses", "Último Año"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        spinner?.adapter = adapter

        spinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                horasFiltro = when (position) {
                    0 -> 1
                    1 -> 6
                    2 -> 24
                    3 -> 168
                    4 -> 720
                    5 -> 4320
                    6 -> 8760
                    else -> 24
                }
                cargarDatosDesdeFirebase()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun cargarDatosDesdeFirebase() {
        val idNodo = nodoIdActual ?: return

        dbRef.child(idNodo).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaDatos = mutableListOf<HistorialDataMillis>()
                val ahoraMillis = System.currentTimeMillis()
                val limiteTiempoMillis = ahoraMillis - (horasFiltro * 3600L * 1000L)

                for (child in snapshot.children) {
                    val valor = child.child(variableActual).value?.toString()?.toDoubleOrNull() ?: 0.0
                    val timestampRaw = child.child("timestamp").value?.toString()
                    val pushKey = child.key ?: ""
                    val fechaRaw = child.child("fecha").value?.toString() ?: ""

                    val timeMillis = obtenerTimestampMillis(timestampRaw, pushKey, fechaRaw)

                    if (timeMillis >= limiteTiempoMillis) {
                        listaDatos.add(HistorialDataMillis(timestamp = timeMillis, valor = valor))
                    }
                }

                if (listaDatos.isNotEmpty()) {
                    dibujarGraficoReal(listaDatos)
                    actualizarMetricasCabecera(listaDatos)
                } else {
                    lineChart.clear()
                    tvValorActual?.text = "--"
                    tvPromedio?.text = "Prom. --"
                    tvRango?.text = "Rango --"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DetalleGrafico", "Error al cargar datos: ${error.message}")
            }
        })
    }

    private fun obtenerTimestampMillis(timestampStr: String?, pushKey: String, fechaStr: String): Long {
        // 1. Si el timestamp ya es Epoch real en milisegundos (> año 2001)
        val ts = timestampStr?.toLongOrNull()
        if (ts != null && ts >= 1_000_000_000_000L) {
            return ts
        }

        // 2. Si el ESP32 guardó millis() de encendido, recuperar la fecha real desde el Push ID de Firebase
        decodificarTimestampDePushId(pushKey)?.let {
            if (it >= 1_000_000_000_000L) return it
        }

        // 3. Respaldo por campo fecha en texto
        val sdfEntrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return try {
            sdfEntrada.parse(fechaStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun decodificarTimestampDePushId(pushId: String): Long? {
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

    private fun actualizarMetricasCabecera(lista: List<HistorialDataMillis>) {
        if (lista.isEmpty()) return
        val locale = Locale.getDefault()
        val unidad = obtenerUnidadVariable(variableActual)
        val ultimoValor = lista.last().valor
        val promedio = lista.sumOf { it.valor } / lista.size
        val minVal = lista.minOf { it.valor }
        val maxVal = lista.maxOf { it.valor }

        tvValorActual?.text = "${String.format(locale, "%.1f", ultimoValor)} $unidad"
        tvPromedio?.text = "Prom. ${String.format(locale, "%.1f", promedio)} $unidad"
        tvRango?.text = "Rango ${String.format(locale, "%.1f", minVal)}–${String.format(locale, "%.1f", maxVal)} $unidad"
    }

    private fun dibujarGraficoReal(listaRaw: List<HistorialDataMillis>) {
        val listaOrdenada = listaRaw.sortedBy { it.timestamp }
        if (listaOrdenada.isEmpty()) return

        val primerTimestamp = listaOrdenada.first().timestamp
        val entries = ArrayList<Entry>()

        for (data in listaOrdenada) {
            val xRelativo = (data.timestamp - primerTimestamp).toFloat()
            entries.add(Entry(xRelativo, data.valor.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Historial").apply {
            mode = LineDataSet.Mode.LINEAR
            color = Color.parseColor("#6BE3A0")
            lineWidth = 2.5f

            setDrawCircles(true)
            setCircleColor(Color.parseColor("#6BE3A0"))
            circleRadius = 5f
            setDrawCircleHole(true)
            circleHoleColor = Color.parseColor("#0F141C")
            circleHoleRadius = 2.5f

            setDrawFilled(false)

            isHighlightEnabled = true
            highLightColor = Color.parseColor("#3B4D66")
            highlightLineWidth = 1.2f
            setDrawHorizontalHighlightIndicator(true)
            setDrawVerticalHighlightIndicator(true)

            setDrawValues(false)
        }

        lineChart.data = LineData(dataSet)

        val unidad = obtenerUnidadVariable(variableActual)
        if (markerView == null) {
            markerView = CustomMarkerView(this, R.layout.marker_view, primerTimestamp, unidad, horasFiltro)
            lineChart.marker = markerView
        } else {
            markerView?.actualizarBaseData(primerTimestamp, unidad, horasFiltro)
        }

        val sdfSalida = SimpleDateFormat(if (horasFiltro >= 168) "dd/MM" else "HH:mm", Locale.getDefault())
        lineChart.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val timestampReal = value.toLong() + primerTimestamp
                    return sdfSalida.format(Date(timestampReal))
                }
            }
        }

        lineChart.apply {
            fitScreen()
            invalidate()

            if (entries.size > 4) {
                val rangoTotal = entries.last().x - entries.first().x
                val rangoVisible = (rangoTotal / (entries.size - 1)) * 3.5f

                setVisibleXRangeMaximum(rangoVisible)
                moveViewToX(entries.last().x)
            }
        }
    }

    private fun obtenerUnidadVariable(varName: String): String {
        return when (varName) {
            "vpd" -> "kPa"
            "tempAire", "tempSuelo" -> "°C"
            "humAire", "hum15", "hum40" -> "%"
            "presion" -> "hPa"
            "bateria" -> "V"
            else -> ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}

data class HistorialDataMillis(
    val timestamp: Long,
    val valor: Double
)