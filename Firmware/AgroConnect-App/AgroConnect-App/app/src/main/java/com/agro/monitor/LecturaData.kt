package com.agro.monitor

data class LecturaData(
    val hum15: Int = 0,
    val hum40: Int = 0,
    val humAire: Double = 0.0,
    val tempAire: Double = 0.0,
    val tempSuelo: Double = 0.0,
    val bateria: Double = 0.0,
    val vpd: Double = 0.0,
    val presion: Double = 0.0,
    val fecha: String = ""
)