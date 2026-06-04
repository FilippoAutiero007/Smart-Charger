package com.example.battery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalBatteryLog(
    val percentage: Int,
    val isCharging: Boolean,
    val timestamp: Long,
    val source: String
)

object LocalLogService {
    private const val PREFS_NAME = "local_battery_logs"
    private const val KEY_LOGS = "logs_json"

    fun saveLog(context: Context, percentage: Int, isCharging: Boolean, source: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logs = getLogs(context).toMutableList()
        
        // Verifica l'ultimo log per evitare duplicati identici consecutivi a breve distanza
        if (logs.isNotEmpty()) {
            val last = logs[0]
            if (last.percentage == percentage && last.isCharging == isCharging && (System.currentTimeMillis() - last.timestamp < 30000)) {
                return // evita spam dello stesso valore
            }
        }

        // Aggiungi in testa
        logs.add(0, LocalBatteryLog(percentage, isCharging, System.currentTimeMillis(), source))
        
        // Mantieni al massimo 50 log
        val trimmed = if (logs.size > 50) logs.take(50) else logs
        
        val jsonArray = JSONArray()
        for (log in trimmed) {
            val obj = JSONObject().apply {
                put("percentage", log.percentage)
                put("isCharging", log.isCharging)
                put("timestamp", log.timestamp)
                put("source", log.source)
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(KEY_LOGS, jsonArray.toString()).apply()
    }

    fun getLogs(context: Context): List<LocalBatteryLog> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        val list = mutableListOf<LocalBatteryLog>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LocalBatteryLog(
                        percentage = obj.getInt("percentage"),
                        isCharging = obj.getBoolean("isCharging"),
                        timestamp = obj.getLong("timestamp"),
                        source = obj.getString("source")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
