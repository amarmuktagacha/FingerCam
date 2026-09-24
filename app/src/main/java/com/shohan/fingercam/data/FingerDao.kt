package com.shohan.fingercam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FingerDao {
    @Query("SELECT * FROM finger_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FingerRecord>>

    @Insert
    suspend fun insert(record: FingerRecord): Long

    @Query("UPDATE finger_records SET lastResult = :result, lastScore = :score, lastCheckedAt = :time WHERE id = :id")
    suspend fun updateResult(id: Long, result: Int, score: Int, time: Long)

    @Query("DELETE FROM finger_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
