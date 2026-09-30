package com.example.data

import android.content.Context
import com.example.battery.engine.BatteryTelemetryEngine
import com.example.data.local.BatteryDatabase
import com.example.data.local.ChargingSessionEntity
import com.example.ui.model.ChargingSessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class BatteryRepository(private val context: Context) {

    private val db = BatteryDatabase.getInstance(context)
    private val chargingDao = db.chargingSessionDao()
    private val prefs = context.getSharedPreferences("battery_health_engine_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ESTIMATED_CAPACITY_MAH = "estimated_capacity_mah"
        private const val KEY_TOTAL_HEALTH_SAMPLES = "total_health_samples"
    }

    suspend fun onChargingStarted(startLevel: Int, voltageMv: Int) {
        val active = chargingDao.getActiveSession()
        if (active != null) return

        val newSession = ChargingSessionEntity(
            startTime = System.currentTimeMillis(),
            startLevel = startLevel,
            endLevel = startLevel,
            voltageMv = voltageMv
        )
        chargingDao.insertSession(newSession)
    }

    suspend fun updateChargingProgress(
        currentLevel: Int,
        currentMa: Int,
        voltageMv: Int,
        tempCelsius: Float
    ) {
        val active = chargingDao.getActiveSession() ?: return
        val elapsedSec = ((System.currentTimeMillis() - active.startTime) / 1000L).coerceAtLeast(1L)
        val addedMah = ((currentMa.toFloat() * elapsedSec) / 3600f).roundToInt().coerceAtLeast(0)
        val maxMa = maxOf(active.maxCurrentMa, currentMa)
        val avgMa = if (active.averageCurrentMa == 0) currentMa else (active.averageCurrentMa + currentMa) / 2
        val avgTemp = if (active.averageTemperature == 0f) tempCelsius else (active.averageTemperature + tempCelsius) / 2f

        val updated = active.copy(
            endLevel = currentLevel,
            gainedMah = addedMah,
            maxCurrentMa = maxMa,
            averageCurrentMa = avgMa,
            averageTemperature = avgTemp,
            voltageMv = voltageMv
        )
        chargingDao.updateSession(updated)
    }

    suspend fun onChargingFinished(
        endLevel: Int,
        stoppedBySonoff: Boolean = false
    ) {
        val active = chargingDao.getActiveSession() ?: return
        val wear = BatteryTelemetryEngine.calculateCycleWear(active.startLevel, endLevel)
        val deltaLevel = (endLevel - active.startLevel).coerceAtLeast(0)

        // Coulomb Counting Capacity estimation:
        // When delta level >= 10%, calculate estimated battery capacity
        if (deltaLevel >= 10 && active.gainedMah > 200) {
            val sampleCapacity = ((active.gainedMah.toFloat() / deltaLevel.toFloat()) * 100f).roundToInt()
            val designCap = BatteryTelemetryEngine.getDesignCapacityMah(context)
            if (sampleCapacity in (designCap * 0.5f).toInt()..(designCap * 1.3f).toInt()) {
                val currentEstimate = prefs.getInt(KEY_ESTIMATED_CAPACITY_MAH, (designCap * 0.92f).toInt())
                val samplesCount = prefs.getInt(KEY_TOTAL_HEALTH_SAMPLES, 1)

                // Exponential Moving Average
                val alpha = 0.2f
                val newEstimate = ((1f - alpha) * currentEstimate + alpha * sampleCapacity).roundToInt()

                prefs.edit()
                    .putInt(KEY_ESTIMATED_CAPACITY_MAH, newEstimate)
                    .putInt(KEY_TOTAL_HEALTH_SAMPLES, samplesCount + 1)
                    .apply()
            }
        }

        val completed = active.copy(
            endTime = System.currentTimeMillis(),
            endLevel = endLevel,
            cycleWear = wear,
            stoppedBySonoff = stoppedBySonoff
        )
        chargingDao.updateSession(completed)
    }

    fun getRecentSessionSummaries(limit: Int = 10): Flow<List<ChargingSessionSummary>> {
        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.ITALIAN)
        return chargingDao.getRecentSessions(limit).map { list ->
            list.filter { it.endTime > 0 }.map { entity ->
                ChargingSessionSummary(
                    id = entity.id,
                    timestamp = dateFormat.format(Date(entity.startTime)),
                    startLevel = entity.startLevel,
                    endLevel = entity.endLevel,
                    gainedMah = entity.gainedMah,
                    cycleWear = entity.cycleWear,
                    stoppedBySonoff = entity.stoppedBySonoff
                )
            }
        }
    }

    fun getTotalCycles(): Flow<Float> {
        return chargingDao.getTotalCycles().map { it ?: 124.5f }
    }

    fun getEstimatedCapacity(): Int {
        val designCap = BatteryTelemetryEngine.getDesignCapacityMah(context)
        return prefs.getInt(KEY_ESTIMATED_CAPACITY_MAH, (designCap * 0.92f).toInt())
    }
}
