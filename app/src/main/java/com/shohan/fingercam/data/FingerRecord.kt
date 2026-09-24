package com.shohan.fingercam.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val RESULT_NONE = 0
const val RESULT_MATCH = 1
const val RESULT_MISMATCH = 2

@Entity(tableName = "finger_records")
data class FingerRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Base64 templates joined with "|" (one template per registration photo). */
    val templates: String,
    val lastResult: Int = RESULT_NONE,
    val lastScore: Int = 0,
    val lastCheckedAt: Long = 0L
)
