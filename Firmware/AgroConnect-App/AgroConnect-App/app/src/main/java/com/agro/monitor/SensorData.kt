package com.agro.monitor

data class SensorData(
    val vpd: Double,
    val tempAire: Double,
    val humAire: Double,
    val presion: Double,
    val hum15: Int,
    val hum40: Int,
    val tempSuelo: Double,
    val wifiCon: Int,
    val loraCon: Int
)