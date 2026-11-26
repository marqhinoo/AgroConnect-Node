package com.agro.monitor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var configRef: DatabaseReference

    private lateinit var etTempMax: EditText
    private lateinit var etTempMin: EditText
    private lateinit var etVpdMax: EditText
    private lateinit var etVpdMin: EditText
    private lateinit var etHumSueloMin: EditText
    private lateinit var etTempRadicularMin: EditText
    private lateinit var etTempRadicularMax: EditText
    private lateinit var btnGuardarConfig: Button
    private lateinit var btnCerrarSesion: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"

        configRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("configuracion")

        etTempMax = findViewById(R.id.etTempMax)
        etTempMin = findViewById(R.id.etTempMin)
        etVpdMax = findViewById(R.id.etVpdMax)
        etVpdMin = findViewById(R.id.etVpdMin)
        etHumSueloMin = findViewById(R.id.etHumSueloMin)
        etTempRadicularMin = findViewById(R.id.etTempRadicularMin)
        etTempRadicularMax = findViewById(R.id.etTempRadicularMax)
        btnGuardarConfig = findViewById(R.id.btnGuardarConfig)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        cargarConfiguracionActual()

        btnGuardarConfig.setOnClickListener { guardarConfiguracion() }
        btnCerrarSesion.setOnClickListener { cerrarSesion() }
    }

    private fun cargarConfiguracionActual() {
        configRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val tempMax = snapshot.child("tempMax").value?.toString()?.toDoubleOrNull()
                    val tempMin = snapshot.child("tempMin").value?.toString()?.toDoubleOrNull()
                    val vpdMax = snapshot.child("vpdMax").value?.toString()?.toDoubleOrNull()
                    val vpdMin = snapshot.child("vpdMin").value?.toString()?.toDoubleOrNull()
                    val humSueloMin = snapshot.child("humSueloMin").value?.toString()?.toDoubleOrNull()
                    val tempRadicularMin = snapshot.child("tempRadicularMin").value?.toString()?.toDoubleOrNull()
                    val tempRadicularMax = snapshot.child("tempRadicularMax").value?.toString()?.toDoubleOrNull()

                    tempMax?.let { etTempMax.setText(it.toString()) }
                    tempMin?.let { etTempMin.setText(it.toString()) }
                    vpdMax?.let { etVpdMax.setText(it.toString()) }
                    vpdMin?.let { etVpdMin.setText(it.toString()) }
                    humSueloMin?.let { etHumSueloMin.setText(it.toString()) }
                    tempRadicularMin?.let { etTempRadicularMin.setText(it.toString()) }
                    tempRadicularMax?.let { etTempRadicularMax.setText(it.toString()) }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfiguracionActivity, "Error al cargar configuración", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun guardarConfiguracion() {
        val tempMax = etTempMax.text.toString().trim().toDoubleOrNull() ?: 35.0
        val tempMin = etTempMin.text.toString().trim().toDoubleOrNull() ?: 2.0
        val vpdMax = etVpdMax.text.toString().trim().toDoubleOrNull() ?: 1.5
        val vpdMin = etVpdMin.text.toString().trim().toDoubleOrNull() ?: 0.4
        val humSueloMin = etHumSueloMin.text.toString().trim().toDoubleOrNull() ?: 20.0
        val tempRadicularMin = etTempRadicularMin.text.toString().trim().toDoubleOrNull() ?: 12.0
        val tempRadicularMax = etTempRadicularMax.text.toString().trim().toDoubleOrNull() ?: 28.0

        val configMap = mapOf(
            "tempMax" to tempMax,
            "tempMin" to tempMin,
            "vpdMax" to vpdMax,
            "vpdMin" to vpdMin,
            "humSueloMin" to humSueloMin,
            "tempRadicularMin" to tempRadicularMin,
            "tempRadicularMax" to tempRadicularMax
        )

        configRef.setValue(configMap).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "✅ Parámetros agronómicos guardados", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "❌ Error al guardar datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cerrarSesion() {
        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}