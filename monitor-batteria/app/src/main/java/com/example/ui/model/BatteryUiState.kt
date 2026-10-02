package com.example.ui.model

/**
 * UI State Contracts for the 4 core screens
 * Designed to be consumed by Jetpack Compose screens and emitted by ViewModels/Services.
 */

data class ChargingUiState(
    val currentMa: Int = 0,               // e.g. +2850 mA
    val voltageMv: Int = 4200,            // e.g. 4210 mV
    val powerWatts: Float = 0f,           // e.g. 11.9 W
    val temperatureCelsius: Float = 28.5f,// e.g. 28.5 °C
    val batteryLevel: Int = 65,           // 0-100%
    val isCharging: Boolean = true,
    val chargingSpeedType: String = "Rapida (Fast)", // Lenta, Standard, Rapida, Super Fast
    val estimatedTimeToTarget: String = "24 min rimanenti",
    val targetCutoffPercent: Int = 80,
    val sonoffConnected: Boolean = true,
    val sonoffRelayOn: Boolean = true,
    val sonoffDeviceName: String = "Presa Studio"
)

data class DischargingUiState(
    val dischargeRatePerHour: Float = 8.4f, // %/h
    val currentDrainMa: Int = 340,           // mA average
    val estimatedTimeRemaining: String = "7h 45m",
    val screenOnTimeSec: Long = 10800L,     // e.g. 3h 00m
    val screenOnDrainPercent: Float = 32f,  // % of battery used
    val screenOffTimeSec: Long = 25200L,    // e.g. 7h 00m
    val screenOffDrainPercent: Float = 8f,  // % of battery used
    val deepSleepTimeSec: Long = 21600L,    // e.g. 6h 00m
    val deepSleepDrainPercent: Float = 3f   // % of battery used
) {
    val totalStandbySec: Long get() = screenOffTimeSec
    val deepSleepRatio: Float get() = if (screenOffTimeSec > 0) deepSleepTimeSec.toFloat() / screenOffTimeSec else 0f
}

data class HealthUiState(
    val estimatedCapacityMah: Int = 4450,
    val designCapacityMah: Int = 5000,
    val healthPercentage: Int = 89,
    val totalCyclesTracked: Float = 0f,
    val recentSessions: List<ChargingSessionSummary> = emptyList(),
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val plugType: String = ""
)

data class ChargingSessionSummary(
    val id: Long = 0,
    val timestamp: String = "Oggi, 14:30",
    val startLevel: Int = 20,
    val endLevel: Int = 80,
    val gainedMah: Int = 2670,
    val cycleWear: Float = 0.18f,
    val stoppedBySonoff: Boolean = true
)

data class AutomationUiState(
    val sonoffEnabled: Boolean = true,
    val cutoffThreshold: Int = 80,
    val resumeThreshold: Int = 30,
    val notificationSoundEnabled: Boolean = true,
    val sonoffApiKeyConfigured: Boolean = true,
    val sonoffDeviceStatus: String = "Pronto e collegato"
)
