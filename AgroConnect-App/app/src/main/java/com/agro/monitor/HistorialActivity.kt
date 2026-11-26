package com.agro.monitor

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        val contenedor = findViewById<LinearLayout>(R.id.contenedorAlertas)
        val btnBorrar = findViewById<Button>(R.id.btnBorrarHistorial)

        // Cargar y mostrar alertas de la memoria local
        cargarHistorialVisual(contenedor)

        // Botón para vaciar el historial
        btnBorrar.setOnClickListener {
            val prefs = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            contenedor.removeAllViews() // Vacía la pantalla al toque
        }
    }

    private fun cargarHistorialVisual(contenedor: LinearLayout) {
        val prefs = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
        // Recuperamos el string largo separado por caracteres "|"
        val historialRaw = prefs.getString("lista_alertas", "") ?: ""

        if (historialRaw.isEmpty()) {
            val tvVacio = TextView(this).apply {
                text = "No se registraron eventos de estrés en el cultivo."
                setTextColor(Color.GRAY)
                textSize = 14f
                setPadding(0, 20, 0, 0)
            }
            contenedor.addView(tvVacio)
            return
        }

        // Separamos cada alerta por su delimitador y las mostramos al revés (la más nueva arriba)
        val listaAlertas = historialRaw.split("|").filter { it.isNotEmpty() }.reversed()

        for (alerta in listaAlertas) {
            val tvAlerta = TextView(this).apply {
                text = alerta
                setTextColor(Color.WHITE)
                textSize = 14f
                setBackgroundResource(android.R.drawable.dialog_holo_dark_frame) // Un fondo sutil para separar
                setPadding(24, 24, 24, 24)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 16)
                layoutParams = params
            }
            contenedor.addView(tvAlerta)
        }
    }
}