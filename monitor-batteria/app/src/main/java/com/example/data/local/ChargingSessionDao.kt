package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE endTime = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSessionEntity?

    @Query("SELECT SUM(cycleWear) FROM charging_sessions")
    fun getTotalCycles(): Flow<Float?>

    @Query("SELECT * FROM charging_sessions ORDER BY id DESC LIMIT 1")
    suspend fun getLastSession(): ChargingSessionEntity?
}
