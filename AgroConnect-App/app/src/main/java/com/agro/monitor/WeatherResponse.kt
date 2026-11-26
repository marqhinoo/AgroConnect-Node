package com.agro.monitor

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("main") val main: MainWeatherData,
    @SerializedName("weather") val weather: List<WeatherDescription>,
    @SerializedName("name") val cityName: String
)

data class MainWeatherData(
    @SerializedName("temp") val temperature: Double,
    @SerializedName("humidity") val humidity: Int,
    @SerializedName("pressure") val pressure: Int
)

data class WeatherDescription(
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String
)