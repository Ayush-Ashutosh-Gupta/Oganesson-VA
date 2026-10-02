package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Resilient, low-power screen-on wake word detector.
 * Listens for "Hi Oganesson", variations, or user-defined custom wake phrases.
 * Includes thermal and battery-aware backoff throttling to prevent CPU overheating,
 * debounced voice triggers, and robust lifecycle recovery so crossing out or closing the
 * overlay reliably resumes listening without audio collisions.
 */
class WakeWordDetector(
    private val context: Context,
    val thermalBatteryManager: ThermalBatteryManager? = null,
    private val onWakeWordDetected: (trailingQuery: String?) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var watchdogJob: Job? = null

    private var isRunning = false
    private var isListening = false
    private var isHandlingDetection = false
    private var isScreenReceiverRegistered = false

    private var customWakeWord: String = ""
    private var lastDetectionTimestamp: Long = 0L

    var persistentBackgroundListening: Boolean = true

    private val _isWakeListening = MutableStateFlow(false)
    val isWakeListening: StateFlow<Boolean> = _isWakeListening.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!persistentBackgroundListening) {
                        Log.d(TAG, "Screen OFF: entering dormant low-power state.")
                        pauseForScreenOff()
                    } else {
                        Log.d(TAG, "Screen OFF with persistent listening: keeping throttled listener active.")
                        if (!isListening && !isHandlingDetection && isRunning) {
                            scheduleRestart(800L)
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Screen ON / User Present: Resuming wake word detector.")
                    resumeFromScreenOn()
                }
            }
        }
    }

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val DEBOUNCE_WINDOW_MS = 2500L

        val PREDEFINED_WAKE_PHRASES = listOf(
            "hi oganesson",
            "hey oganesson",
            "hello oganesson",
            "oganesson",
            "ok oganesson",
            "okay oganesson",
            "hi og",
            "hey og",
            "hi assistant",
            "hey assistant",
            "wake up",
            "hi organized",
            "hey organized",
            "hi organic",
            "hey organic",
            "organism",
            "organesson",
            "augustine"
        )
        val WAKE_PHRASES = PREDEFINED_WAKE_PHRASES
    }

    init {
        initWakeLock()
        registerScreenReceiver()
        startWatchdog()
        setupThermalObserver()
    }

    private fun setupThermalObserver() {
        thermalBatteryManager?.onThermalThrottlingChanged = { isThrottled, pollingDelayMs ->
            Log.d(TAG, "Thermal state observer triggered: throttled=$isThrottled, new polling interval=${pollingDelayMs}ms")
            if (isThrottled && isRunning && !isHandlingDetection) {
                // Calm down active polling immediately to dissipate heat
                restartJob?.cancel()
                scheduleRestart(pollingDelayMs)
            }
        }
    }

    private fun initWakeLock() {
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Oganesson::WakeWordTransientLock"
            )?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize transient wake lock: ${e.message}")
        }
    }

    /**
     * Acquire a short, bounded wake lock only while actively processing a wake trigger.
     * Prevents CPU overheating by never keeping permanent wake locks during dormant state.
     */
    private fun acquireTransientWakeLock(durationMs: Long = 4000L) {
        try {
            wakeLock?.acquire(durationMs)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun setCustomWakeWord(word: String) {
        customWakeWord = word.trim().lowercase(Locale.getDefault())
        Log.d(TAG, "Custom wake word updated: '$customWakeWord'")
    }

    private fun registerScreenReceiver() {
        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                context.registerReceiver(screenStateReceiver, filter)
                isScreenReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Error registering screen receiver", e)
            }
        }
    }

    private fun unregisterScreenReceiver() {
        if (isScreenReceiverRegistered) {
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen receiver: ${e.message}")
            }
            isScreenReceiverRegistered = false
        }
    }

    private fun isScreenInteractive(): Boolean {
        return powerManager?.isInteractive ?: true
    }

    /**
     * Synchronously creates a SpeechRecognizer instance on the Main Looper.
     * Guaranteed to return a non-null instance if speech recognition is supported.
     */
    private fun ensureRecognizerCreated(): Boolean {
        if (speechRecognizer != null) return true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device")
            return false
        }
        return try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating SpeechRecognizer instance", e)
            speechRecognizer = null
            false
        }
    }

    private fun canListenNow(): Boolean {
        if (!isRunning || isHandlingDetection) return false
        if (thermalBatteryManager?.shouldPauseVoiceProcessing() == true) {
            Log.w(TAG, "Thermal/Battery protection: pausing voice recognition to cool device")
            return false
        }
        return persistentBackgroundListening || isScreenInteractive()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        isHandlingDetection = false

        if (canListenNow()) {
            scheduleRestart(400L)
        }
    }

    fun stop() {
        isRunning = false
        isListening = false
        isHandlingDetection = false
        restartJob?.cancel()
        watchdogJob?.cancel()
        _isWakeListening.value = false
        releaseWakeLock()

        mainHandler.post {
            destroyRecognizerInternal()
        }
        unregisterScreenReceiver()
    }

    /**
     * Called when the wake word is detected or overlay opens.
     * Transitions detector into a quiet dormant state to release microphone and conserve CPU.
     */
    fun pause() {
        isListening = false
        restartJob?.cancel()
        _isWakeListening.value = false
        releaseWakeLock()

        mainHandler.post {
            destroyRecognizerInternal()
        }
    }

    private fun pauseForScreenOff() {
        isListening = false
        restartJob?.cancel()
        _isWakeListening.value = false
        releaseWakeLock()

        mainHandler.post {
            destroyRecognizerInternal()
        }
    }

    private fun resumeFromScreenOn() {
        if (canListenNow() && !isListening) {
            scheduleRestart(600L)
        }
    }

    /**
     * Called when overlay is dismissed or closed.
     * Ensures clean revival of wake listening after previous audio components yield the mic.
     */
    fun resume() {
        Log.d(TAG, "WakeWordDetector resume requested. Re-activating background listener in dormant state...")
        isRunning = true
        isHandlingDetection = false
        isListening = false
        _isWakeListening.value = false
        restartJob?.cancel()

        mainHandler.post {
            destroyRecognizerInternal()
            if (canListenNow()) {
                val cooldown = thermalBatteryManager?.getAdaptiveBackoffDelayMs(800L) ?: 800L
                scheduleRestart(cooldown)
            }
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
            isListening = false
            _isWakeListening.value = false
        }
    }

    private fun startListeningLoop() {
        mainHandler.post {
            if (!canListenNow()) {
                isListening = false
                _isWakeListening.value = false
                return@post
            }

            // Cleanly re-instantiate speech recognizer to guarantee responsive hardware channel
            destroyRecognizerInternal()
            if (!ensureRecognizerCreated()) {
                val backoff = thermalBatteryManager?.getAdaptiveBackoffDelayMs(2000L) ?: 2000L
                scheduleRestart(backoff)
                return@post
            }

            val isEco = thermalBatteryManager?.isEcoModeActive == true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, !isEco)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (!isEco) {
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                _isWakeListening.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Exception starting wake listener, scheduling restart", e)
                isListening = false
                _isWakeListening.value = false
                destroyRecognizerInternal()
                val backoff = thermalBatteryManager?.getAdaptiveBackoffDelayMs(1500L) ?: 1500L
                scheduleRestart(backoff)
            }
        }
    }

    private fun scheduleRestart(requestedDelayMs: Long = 1000L) {
        if (!canListenNow()) return
        restartJob?.cancel()

        val delayToUse = requestedDelayMs.coerceAtLeast(400L)

        restartJob = scope.launch {
            delay(delayToUse)
            if (canListenNow()) {
                startListeningLoop()
            }
        }
    }

    /**
     * Periodic watchdog running at low frequency (every 20 seconds) to revive inactive listeners.
     * Uses zero wake locks and imposes negligible CPU load.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(20_000L)
                if (canListenNow() && !isHandlingDetection && !isListening) {
                    Log.d(TAG, "Watchdog: Resurrecting dormant wake word detector...")
                    scheduleRestart(300L)
                }
            }
        }
    }

    private fun checkTextForWakeWord(rawText: String): Boolean {
        if (isHandlingDetection) return true

        val now = System.currentTimeMillis()
        if (now - lastDetectionTimestamp < DEBOUNCE_WINDOW_MS) {
            // Debounce rapid repeated triggers
            return false
        }

        val normalized = rawText.lowercase().trim()

        val allPhrases = mutableListOf<String>()
        if (customWakeWord.isNotBlank() && customWakeWord.length >= 2) {
            allPhrases.add(customWakeWord)
        }
        allPhrases.addAll(PREDEFINED_WAKE_PHRASES)

        for (phrase in allPhrases) {
            if (normalized.contains(phrase)) {
                lastDetectionTimestamp = now
                isHandlingDetection = true

                val index = normalized.indexOf(phrase)
                val remaining = normalized.substring(index + phrase.length)
                    .trim(' ', ',', '.', '!', '?')
                    .trim()
                val query = if (remaining.length >= 2) remaining else null

                Log.d(TAG, "Wake word matched: '$phrase', trailing query: '$query'")

                // Acquire transient 4-second wake lock to wake screen and display overlay
                acquireTransientWakeLock(4000L)
                try {
                    @Suppress("DEPRECATION")
                    val screenWake = powerManager?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "Oganesson::WakeupTurnScreenOn"
                    )
                    screenWake?.acquire(3500L)
                } catch (_: Exception) {}

                pause() // Release audio hardware so follow-up speech recognizer or action can work
                onWakeWordDetected(query)
                return true
            }
        }
        return false
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isWakeListening.value = true
                isListening = true
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                _isWakeListening.value = false
                if (!canListenNow()) return

                // If error is recognizer busy or client error, recreate speech recognizer cleanly
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_AUDIO
                ) {
                    destroyRecognizerInternal()
                }

                // Calm backoff on errors: longer in Eco mode to eliminate CPU heat without excessive dead-zones
                val isEco = thermalBatteryManager?.isEcoModeActive == true
                val baseDelay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isEco) 2500L else 900L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (isEco) 3500L else 1400L
                    else -> if (isEco) 4000L else 1600L
                }
                val adaptiveDelay = thermalBatteryManager?.getAdaptiveBackoffDelayMs(baseDelay) ?: baseDelay
                scheduleRestart(adaptiveDelay)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                _isWakeListening.value = false
                if (!canListenNow()) return

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                var detected = false
                if (matches != null) {
                    for (text in matches) {
                        if (checkTextForWakeWord(text)) {
                            detected = true
                            break
                        }
                    }
                }
                if (!detected && canListenNow()) {
                    // Safe duty-cycle pause between recognition passes to allow CPU to rest
                    val basePause = if (thermalBatteryManager?.isEcoModeActive == true) 2500L else 800L
                    val dutyCyclePause = thermalBatteryManager?.getAdaptiveBackoffDelayMs(basePause) ?: basePause
                    scheduleRestart(dutyCyclePause)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!canListenNow()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (text in matches) {
                        if (checkTextForWakeWord(text)) {
                            break
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
