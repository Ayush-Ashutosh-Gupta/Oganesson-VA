package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a cached voice command and its corresponding assistant response & action payload.
 * When the network is unstable or offline, or for rapid response, Oganesson can query this cache
 * to respond instantly without network roundtrips.
 */
@Entity(
    tableName = "command_cache",
    indices = [
        Index(value = ["normalizedQuery"], unique = true),
        Index(value = ["usageCount"]),
        Index(value = ["lastUsedAt"])
    ]
)
data class CommandCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawQuery: String,
    val normalizedQuery: String,
    val spokenReply: String,
    val actionType: String, // e.g. OPEN_APP, SET_TIMER, TELL_TIME_DATE, CALCULATE, ADJUST_VOLUME, NONE
    val actionParametersJson: String, // JSON serialized map of parameters
    val usageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
