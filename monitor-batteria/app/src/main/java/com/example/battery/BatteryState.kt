package com.example.battery

data class BatteryState(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val plugType: String = "Nessuno",
    val health: String = "Sconosciuto",
    val temperature: Float = 0f, // in °C
    val voltage: Int = 0, // in mV
    val status: String = "Sconosciuto"
)
