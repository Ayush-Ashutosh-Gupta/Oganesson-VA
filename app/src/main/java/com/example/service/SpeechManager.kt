package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningInternal = false
    private var latestTranscribedText = ""
    private var hasDeliveredResult = false

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: (() -> Unit)? = null

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SpeechManager", "Speech recognition not available on this device")
            return
        }
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to create SpeechRecognizer", e)
            }
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        preferOffline: Boolean = false,
        languageCode: String = "en-US"
    ) {
        mainHandler.post {
            // Cancel and recreate cleanly to avoid RECOGNIZER_BUSY or stale internal state
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onSpeechError?.invoke("Speech recognition service is not available.")
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to create SpeechRecognizer", e)
                onSpeechError?.invoke("Could not initialize microphone recognizer: ${e.localizedMessage}")
                return@post
            }

            latestTranscribedText = ""
            hasDeliveredResult = false
            _partialText.value = ""
            _rmsLevel.value = 0f

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Generous silence tolerance to prevent premature cutoffs while user is talking or thinking
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3500L)
                putExtra("android.speech.extra.DICTATION_MODE", true)

                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            try {
                isListeningInternal = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListeningInternal = false
                Log.e("SpeechManager", "Error starting speech listening", e)
                onSpeechError?.invoke("Could not start speech listening: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isListeningInternal = false
            try {
                speechRecognizer?.stopListening()
                _rmsLevel.value = 0f
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error stopping speech listening", e)
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            isListeningInternal = false
            try {
                speechRecognizer?.cancel()
                _rmsLevel.value = 0f
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error cancelling speech listening", e)
            }
        }
    }

    fun destroy() {
        onSpeechResult = null
        onSpeechPartial = null
        onSpeechError = null
        onSpeechStart = null
        onSpeechEnd = null
        isListeningInternal = false
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error destroying speech recognizer", e)
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onSpeechStart?.invoke()
            }

            override fun onBeginningOfSpeech() {
                // Speech begun
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Convert typical dB range (-2 to 10) to 0.0 .. 1.0
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _rmsLevel.value = 0f
                onSpeechEnd?.invoke()
            }

            override fun onError(error: Int) {
                isListeningInternal = false
                _rmsLevel.value = 0f
                val errorMessage = getErrorMessage(error)
                Log.w("SpeechManager", "Speech recognition error ($error): $errorMessage, latestText='$latestTranscribedText'")

                // CRITICAL FIX: If user already spoke partial text and error is a timeout / no match,
                // do NOT discard the command! Deliver the accumulated partial speech!
                val capturedText = latestTranscribedText.ifBlank { _partialText.value }.trim()
                if (!hasDeliveredResult && capturedText.length >= 2) {
                    hasDeliveredResult = true
                    _partialText.value = capturedText
                    onSpeechResult?.invoke(capturedText)
                } else {
                    onSpeechError?.invoke(errorMessage)
                }
            }

            override fun onResults(results: Bundle?) {
                isListeningInternal = false
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim() ?: latestTranscribedText.trim()
                if (recognizedText.isNotBlank()) {
                    hasDeliveredResult = true
                    latestTranscribedText = recognizedText
                    _partialText.value = recognizedText
                    onSpeechResult?.invoke(recognizedText)
                } else {
                    if (!hasDeliveredResult) {
                        val captured = latestTranscribedText.ifBlank { _partialText.value }.trim()
                        if (captured.isNotBlank()) {
                            hasDeliveredResult = true
                            onSpeechResult?.invoke(captured)
                        } else {
                            onSpeechError?.invoke("No speech detected. Please try again.")
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    latestTranscribedText = text
                    _partialText.value = text
                    onSpeechPartial?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please speak clearly."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap to try again."
            else -> "Speech recognition encountered an issue ($errorCode)."
        }
    }
}
