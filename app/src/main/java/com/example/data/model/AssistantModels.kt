package com.example.data.model

import java.util.UUID

enum class SenderType {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class TranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val actionSummary: String? = null,
    val isActionExecuted: Boolean = false
)

enum class ParsedActionType {
    OPEN_APP,
    SET_ALARM,
    SET_TIMER,
    CREATE_NOTE,
    WEB_SEARCH,
    OPEN_MAPS,
    CALL_PHONE,
    SEND_SMS,
    TELL_TIME_DATE,
    CALCULATE,
    MEDIA_PLAY_PAUSE,
    ADJUST_VOLUME,
    NONE
}

data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val requiresPermission: String? = null
)

data class ActionDetails(
    val type: String,
    val parameters: Map<String, String> = emptyMap()
)

data class AssistantActionPayload(
    val spokenReply: String?,
    val action: ActionDetails? = null,
    val actions: List<ActionDetails> = emptyList()
) {
    fun getAllActions(): List<ActionDetails> {
        return when {
            actions.isNotEmpty() -> actions
            action != null -> listOf(action)
            else -> emptyList()
        }
    }
}

