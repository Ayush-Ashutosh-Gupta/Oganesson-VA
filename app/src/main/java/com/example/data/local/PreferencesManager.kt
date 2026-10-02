package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oganesson_preferences")

data class MistralModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val category: String, // e.g. "Flagship (Free Tier)", "Fast & Lightweight", "Code & Reasoning"
    val contextWindow: String,
    val freeTierEligible: Boolean = true
)

data class LanguageOption(
    val code: String,          // e.g. "en-US", "en-IN", "hi-IN"
    val displayName: String,   // e.g. "English (United States)"
    val nativeName: String,    // e.g. "English (US)"
    val region: String         // e.g. "North America", "South Asia"
)

class PreferencesManager(private val context: Context) {

    companion object {
        fun sanitizeApiKey(raw: String): String {
            return raw
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .filter { it.code in 33..126 }
                .trim()
        }

        fun getEffectiveApiKey(primaryUserKey: String, secondaryUserKey: String = ""): String {
            val cleanPrimary = sanitizeApiKey(primaryUserKey)
            if (cleanPrimary.isNotBlank()) {
                return cleanPrimary
            }
            val cleanSecondary = sanitizeApiKey(secondaryUserKey)
            if (cleanSecondary.isNotBlank()) {
                return cleanSecondary
            }
            return sanitizeApiKey(BuildConfig.MISTRAL_FALLBACK_API_KEY)
        }

        val KEY_MISTRAL_API_KEY = stringPreferencesKey("mistral_api_key")
        val KEY_SECONDARY_MISTRAL_API_KEY = stringPreferencesKey("secondary_mistral_api_key")
        val KEY_MISTRAL_MODEL = stringPreferencesKey("mistral_model")
        val KEY_CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
        val KEY_PERSISTENCE_MODE = booleanPreferencesKey("persistence_mode")
        val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        val KEY_PREFER_OFFLINE = booleanPreferencesKey("prefer_offline_speech")
        val KEY_BACKGROUND_WAKE_WORD = booleanPreferencesKey("background_wake_word")
        val KEY_PERSISTENT_BACKGROUND_LISTENING = booleanPreferencesKey("persistent_background_listening")
        val KEY_BACKGROUND_PROMPT_SHOWN = booleanPreferencesKey("background_prompt_shown")
        val KEY_CUSTOM_WAKE_WORD = stringPreferencesKey("custom_wake_word")
        val KEY_ASSISTANT_LANGUAGE = stringPreferencesKey("assistant_language")
        val KEY_POWER_MODE = stringPreferencesKey("power_mode")
        val KEY_MAX_TOKENS = intPreferencesKey("mistral_max_tokens")

        const val POWER_MODE_BALANCED = "BALANCED"
        const val POWER_MODE_ECO = "ECO"
        const val DEFAULT_POWER_MODE = POWER_MODE_BALANCED

        const val DEFAULT_MAX_TOKENS = 8192

        // Default Free Tier Model on Mistral AI
        const val DEFAULT_MISTRAL_MODEL = "mistral-small-latest"
        const val DEFAULT_MODEL = DEFAULT_MISTRAL_MODEL

        /**
         * Catalog of official Mistral AI models available in the Free Tier (La Plateforme)
         */
        val AVAILABLE_MISTRAL_MODELS = listOf(
            MistralModelOption(
                id = "mistral-small-latest",
                displayName = "Mistral Small 3 (24B)",
                description = "Flagship free-tier model. State-of-the-art multilingual reasoning, tool-calling & conversational speed.",
                category = "Flagship (Free Tier)",
                contextWindow = "32k tokens"
            ),
            MistralModelOption(
                id = "open-mistral-nemo",
                displayName = "Mistral NeMo (12B)",
                description = "Co-developed with NVIDIA. High accuracy, massive 128k context, ultra-responsive for mobile assistants.",
                category = "Fast & Multilingual",
                contextWindow = "128k tokens"
            ),
            MistralModelOption(
                id = "open-mistral-7b",
                displayName = "Mistral 7B (v0.3)",
                description = "Lightweight foundational open model. Blazing fast responses for rapid commands and low latency.",
                category = "Ultra-Fast",
                contextWindow = "32k tokens"
            ),
            MistralModelOption(
                id = "codestral-latest",
                displayName = "Codestral (22B)",
                description = "Specialized code, mathematical & complex structured logic model. Available on free developer tier.",
                category = "Logic & Reasoning",
                contextWindow = "32k tokens"
            ),
            MistralModelOption(
                id = "open-mixtral-8x7b",
                displayName = "Mixtral 8x7B",
                description = "Sparse Mixture-of-Experts routing 12B active parameters per token. High throughput and general knowledge.",
                category = "Mixture of Experts",
                contextWindow = "32k tokens"
            )
        )

        /**
         * Supported assistant languages and localized accents
         */
        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("en-US", "English (United States)", "American English", "Americas"),
            LanguageOption("en-GB", "English (United Kingdom)", "British English", "Europe"),
            LanguageOption("en-IN", "English (India)", "Indian English", "South Asia"),
            LanguageOption("en-AU", "English (Australia)", "Australian English", "Oceania"),
            LanguageOption("en-CA", "English (Canada)", "Canadian English", "Americas"),
            LanguageOption("hi-IN", "Hindi (India)", "हिन्दी (भारत)", "South Asia"),
            LanguageOption("es-ES", "Spanish (Spain)", "Español (España)", "Europe"),
            LanguageOption("es-US", "Spanish (United States)", "Español (Estados Unidos)", "Americas"),
            LanguageOption("fr-FR", "French (France)", "Français (France)", "Europe"),
            LanguageOption("de-DE", "German (Germany)", "Deutsch (Deutschland)", "Europe"),
            LanguageOption("ja-JP", "Japanese (Japan)", "日本語 (日本)", "East Asia"),
            LanguageOption("it-IT", "Italian (Italy)", "Italiano (Italia)", "Europe"),
            LanguageOption("pt-BR", "Portuguese (Brazil)", "Português (Brasil)", "Americas"),
            LanguageOption("ko-KR", "Korean (South Korea)", "한국어 (대한민국)", "East Asia")
        )
    }

    val mistralApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_MISTRAL_API_KEY] ?: ""
        sanitizeApiKey(raw)
    }

    val secondaryMistralApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_SECONDARY_MISTRAL_API_KEY] ?: ""
        sanitizeApiKey(raw)
    }

    val mistralModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MISTRAL_MODEL] ?: DEFAULT_MISTRAL_MODEL
    }

    val assistantLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ASSISTANT_LANGUAGE] ?: "en-US"
    }

    val customWakeWord: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_WAKE_WORD] ?: ""
    }

    val continuousMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CONTINUOUS_MODE] ?: false
    }

    val persistenceMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PERSISTENCE_MODE] ?: true
    }

    val ttsSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_SPEED] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_PITCH] ?: 1.0f
    }

    val ttsVoice: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_VOICE] ?: ""
    }

    val preferOffline: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PREFER_OFFLINE] ?: true
    }

    val backgroundWakeWord: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BACKGROUND_WAKE_WORD] ?: true
    }

    val persistentBackgroundListening: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PERSISTENT_BACKGROUND_LISTENING] ?: true
    }

    val backgroundPromptShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BACKGROUND_PROMPT_SHOWN] ?: false
    }

    val powerMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_POWER_MODE] ?: DEFAULT_POWER_MODE
    }

    val maxTokens: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
    }

    suspend fun setPowerMode(mode: String) {
        val normalized = if (mode.equals(POWER_MODE_ECO, ignoreCase = true)) POWER_MODE_ECO else POWER_MODE_BALANCED
        context.dataStore.edit { preferences ->
            preferences[KEY_POWER_MODE] = normalized
        }
    }

    suspend fun setMaxTokens(tokens: Int) {
        val clamped = tokens.coerceIn(1024, 32768)
        context.dataStore.edit { preferences ->
            preferences[KEY_MAX_TOKENS] = clamped
        }
    }

    suspend fun setMistralApiKey(apiKey: String) {
        val clean = sanitizeApiKey(apiKey)
        context.dataStore.edit { preferences ->
            preferences[KEY_MISTRAL_API_KEY] = clean
        }
    }

    suspend fun setSecondaryMistralApiKey(apiKey: String) {
        val clean = sanitizeApiKey(apiKey)
        context.dataStore.edit { preferences ->
            preferences[KEY_SECONDARY_MISTRAL_API_KEY] = clean
        }
    }

    suspend fun setMistralModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MISTRAL_MODEL] = model
        }
    }

    suspend fun setAssistantLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ASSISTANT_LANGUAGE] = languageCode.trim()
        }
    }

    suspend fun setCustomWakeWord(wakeWord: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_WAKE_WORD] = wakeWord.trim()
        }
    }

    suspend fun setContinuousMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONTINUOUS_MODE] = enabled
        }
    }

    suspend fun setPersistenceMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERSISTENCE_MODE] = enabled
        }
    }

    suspend fun setTtsSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_PITCH] = pitch.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setTtsVoice(voiceName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_VOICE] = voiceName
        }
    }

    suspend fun setPreferOffline(offline: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PREFER_OFFLINE] = offline
        }
    }

    suspend fun setBackgroundWakeWord(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_WAKE_WORD] = enabled
        }
    }

    suspend fun setPersistentBackgroundListening(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERSISTENT_BACKGROUND_LISTENING] = enabled
        }
    }

    suspend fun setBackgroundPromptShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_PROMPT_SHOWN] = shown
        }
    }
}
