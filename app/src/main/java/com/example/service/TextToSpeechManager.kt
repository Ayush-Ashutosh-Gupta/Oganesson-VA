package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Enhanced Text-to-Speech manager that provides high quality natural speech synthesis,
 * smart voice selection (filtering for best natural/high-quality voices),
 * configurable persona voice profiles (Natural Neural, Warm Assistant, Deep/Commanding, Cheerful/Brisk),
 * and reliable utterance management.
 */
class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _currentVoiceName = MutableStateFlow<String>("")
    val currentVoiceName: StateFlow<String> = _currentVoiceName.asStateFlow()

    var onSpeechCompleted: (() -> Unit)? = null
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    private var currentRate = 1.0f
    private var currentPitch = 1.0f
    private var preferredVoiceName: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TTSManager", "Default language not supported, falling back to US English")
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
            setupProgressListener()
            updateAvailableVoices()
            applySettings()
        } else {
            Log.e("TTSManager", "TextToSpeech initialization failed with status $status")
            isInitialized = false
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onSpeechStarted?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeechCompleted?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeechError?.invoke("Failed to synthesize speech")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onSpeechError?.invoke("TTS error code: $errorCode")
            }
        })
    }

    private fun updateAvailableVoices() {
        try {
            val allVoices = tts?.voices?.toList() ?: emptyList()
            // Sort to prioritize higher quality / local voices
            val sorted = allVoices.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )
            _availableVoices.value = sorted

            // Automatically pick a natural default voice if none was selected
            if (preferredVoiceName.isNullOrBlank()) {
                val naturalCandidate = sorted.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    (name.contains("en-us") || name.contains("en_us") || voice.locale.language == "en") &&
                        !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                } ?: sorted.firstOrNull()

                if (naturalCandidate != null) {
                    preferredVoiceName = naturalCandidate.name
                    tts?.voice = naturalCandidate
                    _currentVoiceName.value = naturalCandidate.name
                }
            } else {
                val match = sorted.firstOrNull { it.name == preferredVoiceName }
                if (match != null) {
                    tts?.voice = match
                    _currentVoiceName.value = match.name
                }
            }
        } catch (e: Exception) {
            Log.w("TTSManager", "Could not fetch voices", e)
        }
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.5f, 2.0f)
        if (isInitialized) {
            tts?.setSpeechRate(currentRate)
        }
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        if (isInitialized) {
            tts?.setPitch(currentPitch)
        }
    }

    fun setLanguage(languageTag: String) {
        if (languageTag.isBlank()) return
        try {
            val targetLocale = Locale.forLanguageTag(languageTag)
            if (isInitialized) {
                val result = tts?.setLanguage(targetLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("TTSManager", "Language $languageTag not fully supported on device TTS")
                } else {
                    updateAvailableVoices()
                }
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Error setting TTS language: ${e.message}")
        }
    }

    fun setVoice(voiceName: String) {
        preferredVoiceName = voiceName
        _currentVoiceName.value = voiceName
        if (isInitialized && voiceName.isNotBlank()) {
            val match = _availableVoices.value.firstOrNull { it.name == voiceName }
                ?: tts?.voices?.firstOrNull { it.name == voiceName }
            if (match != null) {
                tts?.voice = match
            }
        }
    }

    /**
     * Preset natural voice personas to give Oganesson an immediate distinct, actual voice feel
     */
    fun applyPresetVoiceProfile(profileName: String) {
        when (profileName.lowercase()) {
            "natural", "balanced" -> {
                setSpeechRate(1.05f)
                setPitch(1.0f)
            }
            "warm" -> {
                setSpeechRate(0.95f)
                setPitch(0.92f)
            }
            "energetic", "brisk" -> {
                setSpeechRate(1.15f)
                setPitch(1.08f)
            }
            "deep", "commanding" -> {
                setSpeechRate(0.98f)
                setPitch(0.85f)
            }
        }
    }

    private fun applySettings() {
        tts?.setSpeechRate(currentRate)
        tts?.setPitch(currentPitch)
        if (!preferredVoiceName.isNullOrBlank()) {
            val match = tts?.voices?.firstOrNull { it.name == preferredVoiceName }
            if (match != null) {
                tts?.voice = match
            }
        }
    }

    fun speak(text: String, utteranceId: String = UUID.randomUUID().toString()) {
        if (!isInitialized) {
            Log.w("TTSManager", "TTS not initialized yet")
            return
        }
        if (text.isBlank()) return

        stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e("TTSManager", "Error shutting down TTS", e)
        }
    }
}
