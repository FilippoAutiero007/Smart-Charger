package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discharge_samples")
data class DischargeSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val screenOnDurationSec: Long,
    val screenOffDurationSec: Long,
    val deepSleepDurationSec: Long,
    val drainPercent: Float,
    val averageDrainMa: Int
)
