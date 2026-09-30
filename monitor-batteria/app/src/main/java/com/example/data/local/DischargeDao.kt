package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DischargeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: DischargeSampleEntity): Long

    @Query("SELECT * FROM discharge_samples ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSamples(limit: Int): Flow<List<DischargeSampleEntity>>

    @Query("SELECT * FROM discharge_samples ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSample(): DischargeSampleEntity?
}
