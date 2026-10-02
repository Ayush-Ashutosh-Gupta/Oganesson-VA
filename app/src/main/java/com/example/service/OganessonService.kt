package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.NoteDao
import com.example.data.local.OganessonDatabase
import com.example.data.local.PreferencesManager
import com.example.data.network.MistralClient
import com.example.data.repository.CommandCacheRepository
import com.example.data.repository.CustomVoiceMappingRepository
import com.example.data.model.ActionDetails
import com.example.ui.overlay.FloatingOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Background Foreground Service for Oganesson voice assistant.
 * Listens for "Hi Oganesson", custom wake commands, or variations when screen is ON.
 * When activated, shows a modern Siri/Bixby style floating overlay on top of any current app or home screen,
 * processes local actions instantly or via Mistral AI (Free Tier models), executes device actions, and speaks replies.
 */
class OganessonService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var mistralClient: MistralClient
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var noteDao: NoteDao
    private lateinit var commandCacheRepository: CommandCacheRepository
    private lateinit var customVoiceMappingRepository: CustomVoiceMappingRepository
    private lateinit var thermalBatteryManager: ThermalBatteryManager
    private var wakeWordDetector: WakeWordDetector? = null
    private var activeQueryRecognizer: SpeechManager? = null
    private var floatingOverlayManager: FloatingOverlayManager? = null
    private var persistenceModeEnabled: Boolean = true
    private var continuousModeEnabled: Boolean = true
    private var currentLanguageCode: String = "en-US"

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _assistantStatus = MutableStateFlow("Listening for 'Hi Oganesson'...")
    val assistantStatus: StateFlow<String> = _assistantStatus.asStateFlow()

    companion object {
        private const val TAG = "OganessonService"
        const val CHANNEL_ID = "oganesson_background_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_TRIGGER_WAKE = "com.example.action.TRIGGER_WAKE"
        const val ACTION_ENTER_DORMANT = "com.example.action.ENTER_DORMANT"

        fun start(context: Context) {
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasAudioPermission) {
                Log.w(TAG, "Cannot start OganessonService: RECORD_AUDIO permission not granted yet")
                return
            }

            val intent = Intent(context, OganessonService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OganessonService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun enterDormantState(context: Context) {
            val intent = Intent(context, OganessonService::class.java).apply {
                action = ACTION_ENTER_DORMANT
            }
            context.startService(intent)
        }

        fun triggerWake(context: Context) {
            val intent = Intent(context, OganessonService::class.java).apply {
                action = ACTION_TRIGGER_WAKE
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): OganessonService = this@OganessonService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OganessonService onCreate")

        val database = OganessonDatabase.getDatabase(applicationContext)
        noteDao = database.noteDao()
        commandCacheRepository = CommandCacheRepository(database.commandCacheDao())
        customVoiceMappingRepository = CustomVoiceMappingRepository(database.customVoiceMappingDao())
        preferencesManager = PreferencesManager(applicationContext)
        mistralClient = MistralClient()
        actionExecutor = ActionExecutor(applicationContext, noteDao)
        ttsManager = TextToSpeechManager(applicationContext)
        thermalBatteryManager = ThermalBatteryManager(applicationContext)
        VoiceAssistantWorker.schedule(applicationContext)

        floatingOverlayManager = FloatingOverlayManager(applicationContext).apply {
            onDismissRequested = {
                dismissOverlayAndEnterDormantState()
            }
            onCallContactSelected = { contact ->
                serviceScope.launch {
                    val action = ActionDetails(
                        type = "CALL_PHONE",
                        parameters = mapOf(
                            "phone_number" to contact.phoneNumber,
                            "contact_name" to contact.displayName,
                            "target" to contact.phoneNumber
                        )
                    )
                    actionExecutor.executeAction(action)
                    floatingOverlayManager?.updateReplyState("Calling ${contact.displayName}...", "Calling ${contact.displayName}")
                    ttsManager.speak("Calling ${contact.displayName}")
                }
            }
            onMicTapRequested = {
                ttsManager.stop()
                startOverlayContinuousListening()
            }
        }

        // Sync TTS settings from preferences
        serviceScope.launch {
            preferencesManager.ttsSpeed.collect { ttsManager.setSpeechRate(it) }
        }
        serviceScope.launch {
            preferencesManager.ttsPitch.collect { ttsManager.setPitch(it) }
        }
        serviceScope.launch {
            preferencesManager.ttsVoice.collect { ttsManager.setVoice(it) }
        }
        serviceScope.launch {
            preferencesManager.assistantLanguage.collect { lang ->
                currentLanguageCode = lang
                ttsManager.setLanguage(lang)
            }
        }
        serviceScope.launch {
            preferencesManager.customWakeWord.collect { customWord ->
                wakeWordDetector?.setCustomWakeWord(customWord)
            }
        }
        serviceScope.launch {
            preferencesManager.persistenceMode.collect { enabled ->
                persistenceModeEnabled = enabled
            }
        }
        serviceScope.launch {
            preferencesManager.continuousMode.collect { enabled ->
                continuousModeEnabled = enabled
            }
        }
        serviceScope.launch {
            preferencesManager.persistentBackgroundListening.collect { persistent ->
                wakeWordDetector?.persistentBackgroundListening = persistent
            }
        }

        serviceScope.launch {
            preferencesManager.powerMode.collect { mode ->
                thermalBatteryManager?.isEcoModeActive = (mode == PreferencesManager.POWER_MODE_ECO)
            }
        }

        ttsManager.onSpeechCompleted = {
            _assistantStatus.value = "Listening for 'Hi Oganesson'..."
            updateNotification(_assistantStatus.value)
            wakeWordDetector?.resume()
        }

        initWakeWordDetector()
        createNotificationChannel()
    }

    private fun initWakeWordDetector() {
        wakeWordDetector = WakeWordDetector(this, thermalBatteryManager) { trailingQuery ->
            handleWakeWordTriggered(trailingQuery)
        }
    }

    private fun handleWakeWordTriggered(trailingQuery: String?) {
        Log.d(TAG, "Wake word activated! Trailing query: '$trailingQuery'")
        wakeWordDetector?.pause()
        _assistantStatus.value = "Awake! Processing..."
        updateNotification("Awake! Listening to your voice...")

        // Present floating overlay on top of current screen
        floatingOverlayManager?.showOverlay(initialQuery = trailingQuery)

        // If query was spoken together with wake word, process immediately
        if (!trailingQuery.isNullOrBlank() && trailingQuery.length >= 3) {
            floatingOverlayManager?.updateThinkingState(trailingQuery)
            processAssistantQuery(trailingQuery)
        } else {
            // Listen for command without audio interruption
            listenForFollowUpQuery()
        }
    }

    private fun listenForFollowUpQuery() {
        _assistantStatus.value = "Listening to your request..."
        updateNotification("Listening to your request...")
        floatingOverlayManager?.updateListeningState()

        if (activeQueryRecognizer == null) {
            activeQueryRecognizer = SpeechManager(applicationContext).apply {
                serviceScope.launch {
                    rmsLevel.collect { rms ->
                        floatingOverlayManager?.updateRmsLevel(rms)
                    }
                }
            }
        }

        activeQueryRecognizer?.onSpeechPartial = { partial ->
            floatingOverlayManager?.updateListeningState(partial)
        }

        activeQueryRecognizer?.onSpeechResult = { query ->
            val clean = query.trim()
            val lower = clean.lowercase()
            val isTermination = lower in listOf(
                "stop", "cancel", "bye", "goodbye", "exit", "quit", "close", "shut up", "dismiss", "stop listening", "go away"
            ) || lower.startsWith("stop ") || lower == "turn off"

            if (isTermination) {
                Log.d(TAG, "Termination command spoken in overlay: '$clean'. Returning to dormant state.")
                finishOverlayAndResumeWakeWord()
            } else {
                _assistantStatus.value = "Thinking..."
                updateNotification("Thinking: \"$clean\"")
                floatingOverlayManager?.updateThinkingState(clean)
                processAssistantQuery(clean)
            }
        }

        activeQueryRecognizer?.onSpeechError = { error ->
            Log.w(TAG, "Follow-up speech error: $error")
            if (floatingOverlayManager?.isOverlayActive() == true) {
                startOverlayContinuousListening()
            } else {
                finishOverlayAndResumeWakeWord()
            }
        }

        activeQueryRecognizer?.startListening(
            preferOffline = false,
            languageCode = currentLanguageCode
        )
    }

    private var overlayContinuousJob: kotlinx.coroutines.Job? = null

    /**
     * Called when the TTS speech finishes speaking or fails.
     * If the floating overlay is currently showing, it enables continuous listening
     * so the user can speak follow-up commands without re-triggering the wake word.
     */
    private fun onSpeechFinishedOrSkipped() {
        if ((persistenceModeEnabled || continuousModeEnabled) && floatingOverlayManager?.isOverlayActive() == true) {
            startOverlayContinuousListening()
        } else {
            finishOverlayAndResumeWakeWord()
        }
    }

    private fun dismissOverlayAndEnterDormantState() {
        overlayContinuousJob?.cancel()
        ttsManager.stop()

        // 1. Immediately destroy active query recognizer so audio hardware is completely freed
        try {
            activeQueryRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up active query recognizer", e)
        }
        activeQueryRecognizer = null

        // 2. Hide overlay UI
        floatingOverlayManager?.dismissOverlay()

        // 3. Update status & persistent notification to low-power dormant state
        _assistantStatus.value = "Dormant • Ready for 'Hi Oganesson'"
        updateNotification(_assistantStatus.value)

        // 4. Safely resume wake word listener after mic hardware cleanly yields
        serviceScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(350L)
            wakeWordDetector?.resume()
        }
    }

    private fun finishOverlayAndResumeWakeWord() {
        dismissOverlayAndEnterDormantState()
    }

    private var continuousErrorRetries = 0

    private fun startOverlayContinuousListening() {
        overlayContinuousJob?.cancel()
        continuousErrorRetries = 0

        serviceScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            if (floatingOverlayManager?.isOverlayActive() != true) {
                finishOverlayAndResumeWakeWord()
                return@launch
            }

            // Adaptive delay after TTS audio completes so microphone audio focus is cleanly yielded
            val postTtsDelay = thermalBatteryManager?.getAdaptiveBackoffDelayMs(500L) ?: 500L
            kotlinx.coroutines.delay(postTtsDelay)

            _assistantStatus.value = "Listening for continuous commands..."
            updateNotification("Listening for continuous commands...")
            floatingOverlayManager?.updateListeningState(partialUserText = "", clearPreviousUserText = true)

            if (activeQueryRecognizer == null) {
                activeQueryRecognizer = SpeechManager(applicationContext).apply {
                    serviceScope.launch {
                        rmsLevel.collect { rms ->
                            floatingOverlayManager?.updateRmsLevel(rms)
                        }
                    }
                }
            }

            // Generous continuous listening window (20 seconds without speech before dismissing overlay)
            fun scheduleContinuousInactivityTimeout() {
                overlayContinuousJob?.cancel()
                overlayContinuousJob = serviceScope.launch {
                    kotlinx.coroutines.delay(20_000L)
                    Log.d(TAG, "Continuous listening window elapsed without speech. Dismissing overlay.")
                    finishOverlayAndResumeWakeWord()
                }
            }

            scheduleContinuousInactivityTimeout()

            activeQueryRecognizer?.onSpeechPartial = { partial ->
                continuousErrorRetries = 0
                // Extend/refresh inactivity timer while user is speaking
                scheduleContinuousInactivityTimeout()
                floatingOverlayManager?.updateListeningState(partialUserText = partial, clearPreviousUserText = false)
            }

            activeQueryRecognizer?.onSpeechResult = { nextQuery ->
                continuousErrorRetries = 0
                overlayContinuousJob?.cancel()
                val cleanNext = nextQuery.trim()
                if (cleanNext.isNotBlank()) {
                    val lower = cleanNext.lowercase()
                    if (lower == "stop" || lower == "cancel" || lower == "bye" || lower == "goodbye" || lower == "dismiss" || lower == "close" || lower == "exit") {
                        finishOverlayAndResumeWakeWord()
                    } else {
                        _assistantStatus.value = "Thinking..."
                        updateNotification("Thinking: \"$cleanNext\"")
                        floatingOverlayManager?.updateThinkingState(cleanNext)
                        processAssistantQuery(cleanNext)
                    }
                } else {
                    // Empty match, keep listening in continuous loop
                    if (floatingOverlayManager?.isOverlayActive() == true) {
                        scheduleContinuousInactivityTimeout()
                        activeQueryRecognizer?.startListening(
                            preferOffline = false,
                            languageCode = currentLanguageCode
                        )
                    } else {
                        finishOverlayAndResumeWakeWord()
                    }
                }
            }

            activeQueryRecognizer?.onSpeechError = { errorMsg ->
                Log.d(TAG, "SpeechRecognizer error in continuous window: $errorMsg")
                continuousErrorRetries++

                // If multiple errors occur or device is warm, gracefully return to dormant state
                if (continuousErrorRetries > 2 || thermalBatteryManager?.isOverheated() == true) {
                    Log.d(TAG, "Continuous loop finished due to inactivity or thermal threshold. Entering dormant state.")
                    finishOverlayAndResumeWakeWord()
                } else if (floatingOverlayManager?.isOverlayActive() == true && overlayContinuousJob?.isActive == true) {
                    serviceScope.launch {
                        val backoff = thermalBatteryManager?.getAdaptiveBackoffDelayMs(1200L) ?: 1200L
                        kotlinx.coroutines.delay(backoff)
                        if (floatingOverlayManager?.isOverlayActive() == true && overlayContinuousJob?.isActive == true) {
                            try {
                                activeQueryRecognizer?.startListening(
                                    preferOffline = false,
                                    languageCode = currentLanguageCode
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed restarting continuous recognizer", e)
                                finishOverlayAndResumeWakeWord()
                            }
                        }
                    }
                } else {
                    finishOverlayAndResumeWakeWord()
                }
            }

            activeQueryRecognizer?.startListening(
                preferOffline = false,
                languageCode = currentLanguageCode
            )
        }
    }

    private fun processAssistantQuery(query: String) {
        val lower = query.trim().lowercase()
        val isTermination = lower in listOf(
            "stop", "cancel", "bye", "goodbye", "exit", "quit", "close", "shut up", "dismiss", "stop listening", "go away"
        ) || lower == "stop" || lower == "turn off"

        if (isTermination) {
            Log.d(TAG, "Termination phrase in query: '$query'. Dismissing overlay.")
            finishOverlayAndResumeWakeWord()
            return
        }

        serviceScope.launch {
            // 0a. Custom Voice Shortcut Mappings (Zero AI Round-Trip):
            val customShortcut = customVoiceMappingRepository.findMatchingMapping(query)
            if (customShortcut != null) {
                var actionSummary: String? = null
                if (customShortcut.actionType == "MULTI_ACTION_MACRO") {
                    val macroActions = ActionParser.parseMacroSteps(customShortcut.target)
                    if (macroActions.isNotEmpty()) {
                        val results = actionExecutor.executeActions(macroActions)
                        actionSummary = results.joinToString(" • ") { it.message }
                    } else {
                        val fallback = ActionParser.parseLocalAction(customShortcut.target)
                        val actions = fallback.payload?.getAllActions() ?: emptyList()
                        if (actions.isNotEmpty()) {
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        }
                    }
                } else {
                    val action = ActionDetails(
                        type = customShortcut.actionType,
                        parameters = when (customShortcut.actionType) {
                            "OPEN_APP" -> mapOf("app" to customShortcut.target)
                            "TOGGLE_WIFI", "OPEN_WIFI_SETTINGS" -> if (customShortcut.target.isNotBlank()) mapOf("state" to customShortcut.target) else emptyMap()
                            "CUSTOM_INTENT" -> mapOf("action" to customShortcut.target)
                            else -> if (customShortcut.target.isNotBlank()) mapOf("state" to customShortcut.target, "query" to customShortcut.target) else emptyMap()
                        }
                    )
                    try {
                        val execResult = actionExecutor.executeAction(action)
                        actionSummary = execResult.message
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing custom shortcut: ${customShortcut.phrase}", e)
                    }
                }
                customVoiceMappingRepository.incrementUsage(customShortcut.id)

                val reply = customShortcut.spokenReply.ifBlank { actionSummary ?: "Shortcut \"${customShortcut.phrase}\" executed." }
                _assistantStatus.value = reply
                updateNotification(if (actionSummary != null) "$reply ($actionSummary)" else reply)
                floatingOverlayManager?.updateReplyState(reply, actionSummary)

                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(reply)
                return@launch
            }

            // 0b. Local Offline Action Parser (Zero AI Round-Trip, supports dual/multi actions):
            val localResult = ActionParser.parseLocalAction(query)
            if (localResult.isHandledLocally && localResult.payload != null) {
                val payload = localResult.payload
                val reply = payload.spokenReply ?: "Executing command."
                val actions = payload.getAllActions()

                var actionSummary: String? = null
                if (actions.isNotEmpty()) {
                    try {
                        if (actions.size > 1) {
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        } else if (actions[0].type.uppercase() != "NONE") {
                            val result = actionExecutor.executeAction(actions[0])
                            actionSummary = result.message
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing local action(s)", e)
                    }
                }

                _assistantStatus.value = reply
                updateNotification(if (actionSummary != null) "$reply ($actionSummary)" else reply)
                floatingOverlayManager?.updateReplyState(reply, actionSummary)

                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(reply)
                return@launch
            }

            // 1. Check local Room database cache (Zero-latency offline & rate-limit prevention)
            val cachedPayload = commandCacheRepository.getCachedPayload(query)
            val primaryKey = preferencesManager.mistralApiKey.first()
            val secondaryKey = preferencesManager.secondaryMistralApiKey.first()
            val model = preferencesManager.mistralModel.first()
            val maxTokensPref = preferencesManager.maxTokens.first()
            val effectiveKey = PreferencesManager.getEffectiveApiKey(primaryKey, secondaryKey)

            if (cachedPayload != null) {
                val reply = cachedPayload.spokenReply ?: "Done."
                val actions = cachedPayload.getAllActions()

                var actionSummary: String? = null
                if (actions.isNotEmpty()) {
                    try {
                        if (actions.size > 1) {
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        } else if (actions[0].type.uppercase() != "NONE") {
                            val execResult = actionExecutor.executeAction(actions[0])
                            actionSummary = execResult.message
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing cached action(s)", e)
                    }
                }

                _assistantStatus.value = reply
                updateNotification(if (actionSummary != null) "$reply ($actionSummary)" else reply)
                floatingOverlayManager?.updateReplyState(reply, actionSummary)
                commandCacheRepository.saveOrUpdateCommand(query, cachedPayload)

                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(reply)
                return@launch
            }

            if (effectiveKey.isBlank()) {
                val warn = "Please enter your Mistral AI API key in Oganesson settings."
                floatingOverlayManager?.updateReplyState(warn)
                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(warn)
                return@launch
            }

            // 2. Query Mistral AI with Dual Key Fallback & Thermal Adaptive Model
            val effectiveModel = thermalBatteryManager?.getEffectiveModel(model) ?: model
            val result = mistralClient.queryMistral(
                apiKey = primaryKey,
                secondaryApiKey = secondaryKey,
                model = effectiveModel,
                conversationHistory = emptyList(),
                userMessage = query,
                maxTokens = maxTokensPref
            )

            result.onSuccess { payload ->
                val reply = payload.spokenReply ?: "Done."
                val actions = payload.getAllActions()

                var actionSummary: String? = null
                if (actions.isNotEmpty()) {
                    try {
                        if (actions.size > 1) {
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        } else if (actions[0].type.uppercase() != "NONE") {
                            val execResult = actionExecutor.executeAction(actions[0])
                            actionSummary = execResult.message
                        }
                        Log.d(TAG, "Background action(s) executed, result: $actionSummary")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing background action(s)", e)
                    }
                }

                val notificationText = if (actionSummary != null) {
                    "$reply ($actionSummary)"
                } else {
                    reply
                }

                _assistantStatus.value = reply
                updateNotification(notificationText)

                // Update overlay UI with reply and action execution confirmation
                floatingOverlayManager?.updateReplyState(
                    reply = reply,
                    actionFeedback = actionSummary
                )

                // Save or update in Room cache
                commandCacheRepository.saveOrUpdateCommand(query, payload)

                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(reply)
            }.onFailure { error ->
                Log.e(TAG, "Error from Mistral AI in background", error)

                // Fallback to local Room cache if network is unstable/fails
                if (cachedPayload != null) {
                    val reply = cachedPayload.spokenReply ?: "Done."
                    val actions = cachedPayload.getAllActions()
                    var actionSummary: String? = null
                    if (actions.isNotEmpty()) {
                        try {
                            if (actions.size > 1) {
                                val results = actionExecutor.executeActions(actions)
                                actionSummary = results.joinToString(" • ") { it.message }
                            } else if (actions[0].type.uppercase() != "NONE") {
                                val execResult = actionExecutor.executeAction(actions[0])
                                actionSummary = execResult.message
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed executing fallback cached action", e)
                        }
                    }

                    _assistantStatus.value = reply
                    updateNotification(if (actionSummary != null) "$reply ($actionSummary)" else reply)
                    floatingOverlayManager?.updateReplyState(reply, actionSummary)
                    commandCacheRepository.saveOrUpdateCommand(query, cachedPayload)

                    ttsManager.onSpeechCompleted = {
                        onSpeechFinishedOrSkipped()
                    }
                    ttsManager.onSpeechError = {
                        onSpeechFinishedOrSkipped()
                    }
                    ttsManager.speak(reply)
                    return@launch
                }

                val errorMsg = "Sorry, I had trouble processing that with Mistral AI."
                floatingOverlayManager?.updateReplyState(errorMsg)
                ttsManager.onSpeechCompleted = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.onSpeechError = {
                    onSpeechFinishedOrSkipped()
                }
                ttsManager.speak(errorMsg)
            }
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundServiceInternal()
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundServiceInternal()
            }
            ACTION_TRIGGER_WAKE -> {
                if (!_isServiceActive.value) {
                    startForegroundServiceInternal()
                }
                handleWakeWordTriggered(null)
            }
            ACTION_ENTER_DORMANT -> {
                if (!_isServiceActive.value) {
                    startForegroundServiceInternal()
                }
                dismissOverlayAndEnterDormantState()
            }
            else -> {
                startForegroundServiceInternal()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            Log.w(TAG, "Cannot start foreground service: RECORD_AUDIO permission not granted")
            _isServiceActive.value = false
            stopSelf()
            return
        }

        val notification = buildNotification("Listening for 'Hi Oganesson'...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isServiceActive.value = true
            wakeWordDetector?.start()
            Log.d(TAG, "Foreground service started with microphone type")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service", e)
            _isServiceActive.value = false
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            _isServiceActive.value = false
            stopSelf()
        }
    }

    private fun stopForegroundServiceInternal() {
        wakeWordDetector?.stop()
        activeQueryRecognizer?.destroy()
        floatingOverlayManager?.destroy()
        ttsManager.stop()
        _isServiceActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Oganesson Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background listening service for hands-free wake word activation"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeIntent = Intent(this, OganessonService::class.java).apply {
            action = ACTION_TRIGGER_WAKE
        }
        val wakePendingIntent = PendingIntent.getService(
            this,
            2,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OganessonService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Oganesson Voice Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Wake", wakePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: App removed from open apps list. Ensuring background listening stays active.")
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            Log.w(TAG, "onTaskRemoved: RECORD_AUDIO permission not granted, skipping foreground restart")
            return
        }
        try {
            val notification = buildNotification("Listening for 'Hi Oganesson'...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isServiceActive.value = true
            wakeWordDetector?.start()

            // Schedule alarm fallback to revive service if system ever kills it
            val restartIntent = Intent(applicationContext, OganessonService::class.java).apply {
                action = ACTION_START_SERVICE
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                202,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in onTaskRemoved", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeWordDetector?.stop()
        activeQueryRecognizer?.destroy()
        floatingOverlayManager?.destroy()
        ttsManager.shutdown()
        Log.d(TAG, "OganessonService destroyed")
    }
}
