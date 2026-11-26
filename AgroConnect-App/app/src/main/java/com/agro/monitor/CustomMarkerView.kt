package com.agro.monitor

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomMarkerView(
    context: Context,
    layoutResource: Int,
    private var primerTimestamp: Long,
    private var unidad: String,
    private var horasFiltro: Int = 24
) : MarkerView(context, layoutResource) {

    private val tvTime: TextView = findViewById(R.id.tvMarkerTime)
    private val tvValue: TextView = findViewById(R.id.tvMarkerValue)

    fun actualizarBaseData(timestampBase: Long, nuevaUnidad: String, filtro: Int) {
        this.primerTimestamp = timestampBase
        this.unidad = nuevaUnidad
        this.horasFiltro = filtro
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return
        val timestampReal = e.x.toLong() + primerTimestamp
        val patronFecha = if (horasFiltro >= 168) "dd/MM HH:mm" else "HH:mm:ss"
        val sdf = SimpleDateFormat(patronFecha, Locale.getDefault())

        tvTime.text = sdf.format(Date(timestampReal))
        tvValue.text = "${String.format("%.1f", e.y)} $unidad"

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 20f)
    }
}