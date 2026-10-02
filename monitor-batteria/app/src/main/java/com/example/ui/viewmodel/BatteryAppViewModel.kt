package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.battery.BatteryCheckWorker
import com.example.battery.BatteryMonitorService
import com.example.battery.BatteryState
import com.example.battery.SonoffController
import com.example.battery.engine.BatteryTelemetryEngine
import com.example.battery.engine.StandbyTracker
import com.example.data.BatteryRepository
import com.example.ui.model.AutomationUiState
import com.example.ui.model.ChargingUiState
import com.example.ui.model.DischargingUiState
import com.example.ui.model.HealthUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryAppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = BatteryRepository(context)
    private val sonoffPrefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
    private val workerPrefs = context.getSharedPreferences(BatteryCheckWorker.PREFS_NAME, Context.MODE_PRIVATE)

    private val _automationState = MutableStateFlow(loadInitialAutomationState())
    val automationUiState: StateFlow<AutomationUiState> = _automationState.asStateFlow()

    private val _dischargingState = MutableStateFlow(loadInitialDischargeState())
    val dischargingUiState: StateFlow<DischargingUiState> = _dischargingState.asStateFlow()

    val chargingUiState: StateFlow<ChargingUiState> = combine(
        BatteryMonitorService.batteryStateFlow,
        _automationState
    ) { state: BatteryState, auto: AutomationUiState ->
        val designCap = BatteryTelemetryEngine.getDesignCapacityMah(context)
        val timeEst = BatteryTelemetryEngine.estimateTimeToTarget(
            currentLevel = state.percentage,
            targetLevel = auto.cutoffThreshold,
            currentMa = state.currentMa,
            designCapacityMah = designCap
        )
        val speedType = BatteryTelemetryEngine.classifyChargingSpeed(state.powerWatts, state.currentMa)

        val isRelayOn = sonoffPrefs.getString(SonoffController.KEY_LAST_COMMAND, "on") == "on"
        val devId = sonoffPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: ""

        ChargingUiState(
            currentMa = state.currentMa,
            voltageMv = state.voltage,
            powerWatts = state.powerWatts,
            temperatureCelsius = state.temperature,
            batteryLevel = state.percentage,
            isCharging = state.isCharging,
            chargingSpeedType = speedType,
            estimatedTimeToTarget = timeEst,
            targetCutoffPercent = auto.cutoffThreshold,
            sonoffConnected = devId.isNotEmpty(),
            sonoffRelayOn = isRelayOn,
            sonoffDeviceName = if (devId.isNotEmpty()) "Smart Plug ($devId)" else "Non configurato"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChargingUiState()
    )

    val healthUiState: StateFlow<HealthUiState> = combine(
        repository.getRecentSessionSummaries(10),
        repository.getTotalCycles()
    ) { sessions, totalCycles ->
        val designCap = BatteryTelemetryEngine.getDesignCapacityMah(context)
        val estimatedCap = repository.getEstimatedCapacity()
        val healthPct = ((estimatedCap.toFloat() / designCap.toFloat()) * 100f).roundToInt().coerceIn(1, 100)

        HealthUiState(
            estimatedCapacityMah = estimatedCap,
            designCapacityMah = designCap,
            healthPercentage = healthPct,
            totalCyclesTracked = totalCycles,
            recentSessions = sessions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HealthUiState()
    )

    init {
        refreshStandbyStats()
    }

    fun refreshStandbyStats() {
        val stats = StandbyTracker.getStandbyStats(context)
        val currentLevel = BatteryMonitorService.batteryStateFlow.value.percentage
        val autonomy = BatteryTelemetryEngine.estimateDischargeAutonomy(currentLevel, 7.8f)

        _dischargingState.value = DischargingUiState(
            dischargeRatePerHour = 7.8f,
            currentDrainMa = 310,
            estimatedTimeRemaining = autonomy,
            screenOnTimeSec = stats.screenOnSec,
            screenOnDrainPercent = stats.screenOnDrainPct,
            screenOffTimeSec = stats.screenOffSec,
            screenOffDrainPercent = stats.screenOffDrainPct,
            deepSleepTimeSec = stats.deepSleepSec,
            deepSleepDrainPercent = stats.deepSleepDrainPct
        )
    }

    fun updateCutoffThreshold(newThreshold: Int) {
        val clamped = newThreshold.coerceIn(50, 100)
        sonoffPrefs.edit().putInt(SonoffController.KEY_OFF_THRESHOLD, clamped).apply()
        _automationState.value = _automationState.value.copy(cutoffThreshold = clamped)
    }

    fun toggleSonoffAutomation(enabled: Boolean) {
        sonoffPrefs.edit().putBoolean(SonoffController.KEY_ENABLED, enabled).apply()
        _automationState.value = _automationState.value.copy(sonoffEnabled = enabled)
    }

    fun toggleNotificationSound(enabled: Boolean) {
        workerPrefs.edit().putBoolean(BatteryCheckWorker.KEY_ENABLED, enabled).apply()
        _automationState.value = _automationState.value.copy(notificationSoundEnabled = enabled)
    }

    private fun loadInitialAutomationState(): AutomationUiState {
        val enabled = sonoffPrefs.getBoolean(SonoffController.KEY_ENABLED, true)
        val cutoff = sonoffPrefs.getInt(SonoffController.KEY_OFF_THRESHOLD, 80)
        val resume = sonoffPrefs.getInt(SonoffController.KEY_ON_THRESHOLD, 30)
        val devId = sonoffPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: ""
        val token = sonoffPrefs.getString(SonoffController.KEY_ACCESS_TOKEN, "") ?: ""
        val sound = workerPrefs.getBoolean(BatteryCheckWorker.KEY_ENABLED, true)

        val isConfigured = devId.isNotEmpty() && token.isNotEmpty()
        val status = if (isConfigured) "Pronto e collegato" else "Credenziali mancanti"

        return AutomationUiState(
            sonoffEnabled = enabled,
            cutoffThreshold = cutoff,
            resumeThreshold = resume,
            notificationSoundEnabled = sound,
            sonoffApiKeyConfigured = isConfigured,
            sonoffDeviceStatus = status
        )
    }

    private fun loadInitialDischargeState(): DischargingUiState {
        return DischargingUiState(
            dischargeRatePerHour = 7.8f,
            currentDrainMa = 310,
            estimatedTimeRemaining = "11h 30m",
            screenOnTimeSec = 3600L * 2,
            screenOnDrainPercent = 18f,
            screenOffTimeSec = 3600L * 6,
            screenOffDrainPercent = 6f,
            deepSleepTimeSec = 3600L * 5,
            deepSleepDrainPercent = 2.5f
        )
    }
}
