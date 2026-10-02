package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0,
    val startLevel: Int,
    val endLevel: Int = startLevel,
    val gainedMah: Int = 0,
    val cycleWear: Float = 0f,
    val averageCurrentMa: Int = 0,
    val maxCurrentMa: Int = 0,
    val averageTemperature: Float = 0f,
    val voltageMv: Int = 0,
    val stoppedBySonoff: Boolean = false
)
