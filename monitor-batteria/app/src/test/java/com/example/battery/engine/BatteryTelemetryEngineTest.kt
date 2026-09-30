package com.example.battery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTelemetryEngineTest {

    @Test
    fun testPowerWattsCalculation() {
        val watts = BatteryTelemetryEngine.calculatePowerWatts(voltageMv = 4200, currentMa = 2850)
        // 4.2V * 2.85A = 11.97W -> rounded to 12.0W
        assertEquals(12.0f, watts, 0.1f)
    }

    @Test
    fun testCycleWearIntegralAccuracy() {
        // Charging from 0% to 100% must yield ~1.0 cycle of wear
        val fullCycle = BatteryTelemetryEngine.calculateCycleWear(0, 100)
        assertEquals(1.0f, fullCycle, 0.05f)

        // Charging in the safe zone 20% -> 80% (60% delta) has dramatically lower stress
        val safeZoneWear = BatteryTelemetryEngine.calculateCycleWear(20, 80)
        assertTrue("Wear 20-80% should be around 0.18-0.25, was $safeZoneWear", safeZoneWear in 0.15f..0.25f)

        // Charging in the saturation zone 80% -> 100% (only 20% delta) has high voltage stress
        val topZoneWear = BatteryTelemetryEngine.calculateCycleWear(80, 100)
        assertTrue("Wear 80-100% should be around 0.70-0.85, was $topZoneWear", topZoneWear in 0.70f..0.85f)

        // Top 20% wear must be more than 3x greater than bottom 60% wear
        assertTrue("Top 20% wear ($topZoneWear) must exceed safe zone wear ($safeZoneWear)", topZoneWear > safeZoneWear * 3)
    }

    @Test
    fun testTimeToTargetEstimation() {
        // 50% to 80% (30% of 5000 mAh = 1500 mAh) at 3000 mA = 0.5h = 30 min
        val estimate = BatteryTelemetryEngine.estimateTimeToTarget(
            currentLevel = 50,
            targetLevel = 80,
            currentMa = 3000,
            designCapacityMah = 5000
        )
        assertEquals("30 min rimanenti", estimate)
    }

    @Test
    fun testChargingSpeedClassification() {
        val ultra = BatteryTelemetryEngine.classifyChargingSpeed(powerWatts = 33.0f, currentMa = 3500)
        assertEquals("Super Fast (Ultra)", ultra)

        val fast = BatteryTelemetryEngine.classifyChargingSpeed(powerWatts = 18.0f, currentMa = 2000)
        assertEquals("Rapida (Fast)", fast)

        val standard = BatteryTelemetryEngine.classifyChargingSpeed(powerWatts = 8.0f, currentMa = 1600)
        assertEquals("Standard", standard)

        val slow = BatteryTelemetryEngine.classifyChargingSpeed(powerWatts = 2.0f, currentMa = 400)
        assertEquals("Lenta (Slow)", slow)
    }
}
