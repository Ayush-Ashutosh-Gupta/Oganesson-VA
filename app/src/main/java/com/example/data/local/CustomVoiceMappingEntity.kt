package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a user-defined custom voice shortcut.
 * Allows users to map any custom voice phrase directly to an Android intent or device action
 * (e.g., toggle Wi-Fi, open Wi-Fi settings, launch specific apps, toggle Bluetooth, toggle hotspot, open camera)
 * with ZERO AI round-trip.
 */
@Entity(tableName = "custom_voice_mappings")
data class CustomVoiceMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phrase: String,          // e.g. "turn on wifi", "disable wifi", "spotify time", "open camera"
    val actionType: String,      // e.g. "TOGGLE_WIFI", "OPEN_APP", "OPEN_WIFI_SETTINGS", "OPEN_BLUETOOTH_SETTINGS", etc.
    val target: String = "",     // e.g. package name, app label, or parameter like "ON"/"OFF"
    val spokenReply: String,     // e.g. "Opening Wi-Fi settings", "Launching Spotify"
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)
