package com.example.battery

import android.content.Context
import android.content.Intent
import android.os.BatteryManager

object BatteryMonitor {

    fun parseState(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        
        val percentage = if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            0
        }

        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || 
                         statusInt == BatteryManager.BATTERY_STATUS_FULL

        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "In carica"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "In scarica"
            BatteryManager.BATTERY_STATUS_FULL -> "Completamente carica"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Non in carica"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "Sconosciuto"
            else -> "Sconosciuto"
        }

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Presa di corrente"
            BatteryManager.BATTERY_PLUGGED_USB -> "Connessione USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Ricarica Wireless"
            else -> "Nessuna (Batteria)"
        }

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Ottima (Buona)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surriscaldata!"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Danneggiata"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Tensione eccessiva"
            BatteryManager.BATTERY_HEALTH_COLD -> "Troppo fredda"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Malfunzionamento"
            else -> "Sconosciuto"
        }

        // La temperatura viene restituita in decimi di grado Celsius (es. 295 = 29.5 °C)
        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperature = rawTemp / 10f

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return BatteryState(
            percentage = percentage,
            isCharging = isCharging,
            plugType = plugType,
            health = health,
            temperature = temperature,
            voltage = voltage,
            status = status
        )
    }
}
