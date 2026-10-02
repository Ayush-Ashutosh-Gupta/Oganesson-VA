package com.example.service

import com.example.data.model.ActionDetails
import com.example.data.model.AssistantActionPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Local Intent & Action Parser.
 * Decouples immediate actions from having to pass their text to the Mistral AI API.
 * Detects common voice commands immediately on device (zero latency, zero API quota, full offline capability):
 * - Torch / Flashlight (toggle/on/off)
 * - Volume control (volume up/down/mute/max)
 * - Media playback (pause, resume, play music, stop, next, previous)
 * - Timer setting (e.g. "set a timer for 5 minutes", "timer 30 seconds")
 * - Alarm setting (e.g. "set alarm for 7:30 am", "wake me up at 8")
 * - Note taking (e.g. "take a note buy milk", "note that parking is on floor 2")
 * - Universal App Launch (e.g. "open youtube", "launch camera", "open chrome", "start spotify", "open any app")
 * - Universal Phone Calling (e.g. "call mom", "call John", "dial 911", "call +123456789")
 * - Universal SMS sending (e.g. "text mom I will be late", "send sms to Alice hello")
 * - Quick Math / Calculator (e.g. "what is 25 times 4", "calculate 100 divided by 5")
 * - Time and Date queries (e.g. "what time is it", "what's today's date")
 * - Directions & Navigation (e.g. "directions to central park", "take me to coffee shop")
 * - Web Search (e.g. "search for kotlin compose", "google weather today")
 */
object ActionParser {

    data class LocalParseResult(
        val isHandledLocally: Boolean,
        val payload: AssistantActionPayload?
    )

    /**
     * Checks if the user spoken text corresponds to recognized system action(s).
     * Supports single actions, direct camera snap ("open camera and take a picture"),
     * WhatsApp messaging ("on WhatsApp message X Y"), and dual / compound action chaining
     * (e.g. "open youtube and turn volume to 75%").
     */
    fun parseLocalAction(userInput: String): LocalParseResult {
        val raw = userInput.trim()
        val lower = raw.lowercase(Locale.getDefault())

        if (lower.isBlank()) {
            return LocalParseResult(false, null)
        }

        // 1. Direct Combined Command: "open camera and take a picture" / "open camera and click a photo"
        val cameraPicPatterns = listOf(
            "open camera and take a picture",
            "open camera and take photo",
            "open camera and take picture",
            "open camera and click a picture",
            "open camera and click picture",
            "open camera and snap a picture",
            "open camera and snap photo",
            "launch camera and take a picture",
            "start camera and take a picture"
        )
        if (cameraPicPatterns.any { lower.contains(it) } || lower == "open camera and take a picture") {
            val photoAction = ActionDetails("TAKE_PHOTO", emptyMap())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Taking picture now.",
                    action = photoAction,
                    actions = listOf(photoAction)
                )
            )
        }

        // 2. Direct single action parse attempt first
        val directSingle = parseSingleLocalAction(raw)
        if (directSingle.isHandledLocally && directSingle.payload != null) {
            // If it matched a full single action (like WhatsApp message or direct note),
            // verify it's not a compound command unless it's a note or search or sms/whatsapp
            val singleType = directSingle.payload.action?.type?.uppercase()
            val isExemptFromSplitting = singleType == "CREATE_NOTE" ||
                    singleType == "WEB_SEARCH" ||
                    singleType == "SEND_SMS" ||
                    singleType == "SEND_WHATSAPP"

            if (isExemptFromSplitting || !lower.contains(" and ") && !lower.contains(" then ")) {
                return directSingle
            }
        }

        // 3. Comma / Semicolon / Multi-step Macro splitting:
        // Examples:
        // - "Open Spotify, play playlist X, set volume 50"
        // - "Turn off flashlight, set volume 20%, open clock"
        if (raw.contains(",") || raw.contains(";")) {
            val parts = raw.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val stepActions = mutableListOf<ActionDetails>()
                val replies = mutableListOf<String>()
                for (part in parts) {
                    val res = parseLocalAction(part)
                    if (res.isHandledLocally && res.payload != null) {
                        stepActions.addAll(res.payload.getAllActions())
                        res.payload.spokenReply?.let { replies.add(it.trim().removeSuffix(".")) }
                    }
                }
                if (stepActions.isNotEmpty()) {
                    val combinedReply = replies.joinToString(", ") + "."
                    return LocalParseResult(
                        true,
                        AssistantActionPayload(
                            spokenReply = combinedReply,
                            action = stepActions.firstOrNull(),
                            actions = stepActions
                        )
                    )
                }
            }
        }

        // 4. Dual / Compound action splitting:
        // Examples:
        // - "open youtube and turn volume to 75%"
        // - "turn on flashlight and open calculator"
        // - "open maps and set volume to 50%"
        // - "turn on wifi and open chrome"
        val conjunctions = listOf(" and then ", " then ", " and ")
        for (conj in conjunctions) {
            if (lower.contains(conj)) {
                val parts = raw.split(Regex(Regex.escape(conj), RegexOption.IGNORE_CASE), limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    val res1 = parseSingleLocalAction(parts[0].trim())
                    val res2 = parseSingleLocalAction(parts[1].trim())
                    if (res1.isHandledLocally && res1.payload != null && res2.isHandledLocally && res2.payload != null) {
                        val actions1 = res1.payload.getAllActions()
                        val actions2 = res2.payload.getAllActions()
                        val combinedActions = actions1 + actions2

                        if (combinedActions.isNotEmpty()) {
                            val reply1 = res1.payload.spokenReply?.trim()?.removeSuffix(".") ?: ""
                            val reply2 = res2.payload.spokenReply?.trim()?.removeSuffix(".")?.replaceFirstChar { it.lowercase() } ?: ""
                            val combinedReply = when {
                                reply1.isNotBlank() && reply2.isNotBlank() -> "$reply1 and $reply2."
                                reply1.isNotBlank() -> "$reply1."
                                else -> "$reply2."
                            }
                            return LocalParseResult(
                                true,
                                AssistantActionPayload(
                                    spokenReply = combinedReply,
                                    action = combinedActions.firstOrNull(),
                                    actions = combinedActions
                                )
                            )
                        }
                    }
                }
            }
        }

        return directSingle
    }

    /**
     * Parses a multi-step macro string (steps separated by comma, semicolon, newline, or 'and' / 'then')
     * into a list of executable ActionDetails.
     */
    fun parseMacroSteps(macroText: String): List<ActionDetails> {
        val delimiters = Regex("[,;\n]|\\s+and\\s+then\\s+|\\s+then\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE)
        val rawSteps = macroText.split(delimiters).map { it.trim() }.filter { it.isNotBlank() }
        val actions = mutableListOf<ActionDetails>()
        for (step in rawSteps) {
            val parsed = parseLocalAction(step)
            if (parsed.isHandledLocally && parsed.payload != null) {
                actions.addAll(parsed.payload.getAllActions())
            }
        }
        return actions
    }

    /**
     * Parses an individual clause for a single action on the device.
     */
    fun parseSingleLocalAction(userInput: String): LocalParseResult {
        val raw = userInput.trim()
        val lower = raw.lowercase(Locale.getDefault())

        if (lower.isBlank()) {
            return LocalParseResult(false, null)
        }

        // Direct Camera / Photo Snap
        if (lower == "take a picture" || lower == "take picture" || lower == "take a photo" || lower == "take photo" ||
            lower == "click a picture" || lower == "click picture" || lower == "click photo" || lower == "click a photo" ||
            lower == "capture photo" || lower == "snap a picture" || lower == "snap photo" || lower == "snap a photo"
        ) {
            val photoAction = ActionDetails("TAKE_PHOTO", emptyMap())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening camera to take photo.",
                    action = photoAction,
                    actions = listOf(photoAction)
                )
            )
        }

        // Direct Video Recording
        if (lower == "record video" || lower == "record a video" || lower == "take video" ||
            lower == "start video recording" || lower == "shoot video"
        ) {
            val videoAction = ActionDetails("RECORD_VIDEO", emptyMap())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening camera for video recording.",
                    action = videoAction,
                    actions = listOf(videoAction)
                )
            )
        }

        // Direct Audio Recording
        if (lower == "record audio" || lower == "start recording audio" || lower == "voice recording" ||
            lower == "record voice" || lower == "start audio recording"
        ) {
            val audioAction = ActionDetails("RECORD_AUDIO", emptyMap())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening voice recorder.",
                    action = audioAction,
                    actions = listOf(audioAction)
                )
            )
        }

        // WhatsApp Messaging
        // Examples:
        // - "on whatsapp message John I am on my way"
        // - "on whatsapp text Mom are you home"
        // - "send whatsapp message to Sarah where are you"
        // - "send a message on whatsapp to David hello"
        // - "whatsapp Mike call me when you can"
        // - "message Alex on whatsapp are we meeting today"
        val whatsappRegex1 = Regex("^(?:on\\s+whatsapp\\s+(?:message|text|send\\s+(?:a\\s+)?message\\s+to)|send\\s+(?:a\\s+)?whatsapp(?:\\s+message)?\\s+to|whatsapp)\\s+([a-zA-Z0-9+\\s]+?)\\s+(?:the\\s+message|saying|that)?\\s*([a-zA-Z0-9].*)$", RegexOption.IGNORE_CASE)
        val whatsappMatch1 = whatsappRegex1.find(raw)
        val whatsappRegex2 = Regex("^(?:message|text|send\\s+(?:a\\s+)?message\\s+to)\\s+([a-zA-Z0-9+\\s]+?)\\s+on\\s+whatsapp\\s+(?:the\\s+message|saying|that)?\\s*([a-zA-Z0-9].*)$", RegexOption.IGNORE_CASE)
        val whatsappMatch2 = whatsappRegex2.find(raw)
        val waMatch = whatsappMatch1 ?: whatsappMatch2
        if (waMatch != null) {
            val target = waMatch.groupValues[1].trim()
            val message = waMatch.groupValues[2].trim()
            if (target.isNotBlank() && message.isNotBlank()) {
                val waAction = ActionDetails(
                    "SEND_WHATSAPP",
                    mapOf("target" to target, "message" to message)
                )
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Sending WhatsApp message to $target: \"$message\".",
                        action = waAction,
                        actions = listOf(waAction)
                    )
                )
            }
        }

        // -1. Pending Call Disambiguation Selection
        // If the user was just presented with a numbered list of contacts to call, resolve their choice
        if (CallDisambiguationManager.hasPending()) {
            val chosen = CallDisambiguationManager.resolveChoice(userInput)
            if (chosen != null) {
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Calling ${chosen.displayName}.",
                        action = ActionDetails(
                            "CALL_PHONE",
                            mapOf(
                                "phone_number" to chosen.phoneNumber,
                                "contact_name" to chosen.displayName,
                                "target" to chosen.phoneNumber
                            )
                        )
                    )
                )
            }
        }

        // 0. Wi-Fi Control & Settings
        if (lower.contains("wi-fi") || lower.contains("wifi")) {
            val state = when {
                lower.contains("on") || lower.contains("enable") || lower.contains("activate") || lower.contains("connect") -> "ON"
                lower.contains("off") || lower.contains("disable") || lower.contains("deactivate") || lower.contains("disconnect") -> "OFF"
                else -> null
            }
            val reply = if (state != null) "Opening Wi-Fi controls to turn Wi-Fi $state." else "Opening Wi-Fi settings."
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = reply,
                    action = ActionDetails("TOGGLE_WIFI", if (state != null) mapOf("state" to state) else emptyMap())
                )
            )
        }

        // Bluetooth Settings
        if (lower.contains("bluetooth")) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening Bluetooth settings.",
                    action = ActionDetails("OPEN_BLUETOOTH_SETTINGS", emptyMap())
                )
            )
        }

        // Hotspot / Tethering
        if (lower.contains("hotspot") || lower.contains("tethering")) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening Hotspot settings.",
                    action = ActionDetails("OPEN_HOTSPOT_SETTINGS", emptyMap())
                )
            )
        }

        // Battery / Power
        if (lower.contains("battery") || lower.contains("power usage") || lower.contains("battery health")) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening Battery settings.",
                    action = ActionDetails("OPEN_BATTERY_SETTINGS", emptyMap())
                )
            )
        }

        // Direct Camera Actions (Click picture / take photo / snap picture)
        if (lower.contains("click picture") || lower.contains("click a picture") ||
            lower.contains("take picture") || lower.contains("take a picture") ||
            lower.contains("take photo") || lower.contains("take a photo") ||
            lower.contains("click photo") || lower.contains("click a photo") ||
            lower.contains("snap picture") || lower.contains("snap photo") ||
            lower.contains("capture picture") || lower.contains("capture photo")
        ) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening camera to capture picture.",
                    action = ActionDetails("TAKE_PHOTO", emptyMap())
                )
            )
        }

        // Direct Video Recording Actions (Start recording / record video / capture video)
        if (lower.contains("start recording") || lower.contains("record video") ||
            lower.contains("start video recording") || lower.contains("take video") ||
            lower.contains("capture video") || lower.contains("shoot video")
        ) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening camera for video recording.",
                    action = ActionDetails("RECORD_VIDEO", emptyMap())
                )
            )
        }

        // Direct Voice / Sound Recording Actions (Start voice recording / record audio)
        if (lower.contains("start voice recording") || lower.contains("record voice") ||
            lower.contains("record audio") || lower.contains("start audio recording") ||
            lower.contains("voice recorder") || lower.contains("voice memo") ||
            lower.contains("record a memo") || lower.contains("record sound")
        ) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Opening audio voice recorder.",
                    action = ActionDetails("RECORD_AUDIO", emptyMap())
                )
            )
        }

        // 1. Flashlight / Torch
        if (lower.contains("torch") || lower.contains("flashlight")) {
            val state = when {
                lower.contains("on") -> "ON"
                lower.contains("off") -> "OFF"
                else -> "TOGGLE"
            }
            val reply = when (state) {
                "ON" -> "Turning on flashlight."
                "OFF" -> "Turning off flashlight."
                else -> "Toggling flashlight."
            }
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = reply,
                    action = ActionDetails("TOGGLE_FLASHLIGHT", mapOf("state" to state))
                )
            )
        }

        // 2. Volume & Brightness Controls
        // 2a. Volume with specific percentage (e.g. "turn up volume 50%", "turn down volume to 30%", "volume 60%", "set volume to 80%")
        val volumePercentRegex = Regex("(?:turn\\s*(?:up|down)?\\s*volume|set\\s*volume|volume\\s*(?:up|down|to)?)\\s*(?:to\\s*)?(\\d{1,3})\\s*(?:%|percent)?", RegexOption.IGNORE_CASE)
        val volumePercentMatch = volumePercentRegex.find(lower)
        if (volumePercentMatch != null && (lower.contains("volume") || lower.contains("sound"))) {
            val percentVal = volumePercentMatch.groupValues[1].toIntOrNull()
            if (percentVal != null && percentVal in 0..100) {
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Setting volume to $percentVal percent.",
                        action = ActionDetails("SET_VOLUME_PERCENT", mapOf("percentage" to percentVal.toString()))
                    )
                )
            }
        }

        // 2b. Relative Volume Direction (Up / Down / Mute / Max)
        if (lower.contains("volume") || lower.contains("sound")) {
            val direction = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("louder") -> "UP"
                lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("quieter") -> "DOWN"
                lower.contains("mute") || lower.contains("silence") || lower.contains("zero") -> "MUTE"
                lower.contains("max") || lower.contains("maximum") || lower.contains("full") -> "MAX"
                else -> null
            }
            if (direction != null) {
                val reply = when (direction) {
                    "UP" -> "Turning volume up."
                    "DOWN" -> "Turning volume down."
                    "MUTE" -> "Muting volume."
                    "MAX" -> "Setting volume to maximum."
                    else -> "Adjusting volume."
                }
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = reply,
                        action = ActionDetails("ADJUST_VOLUME", mapOf("direction" to direction))
                    )
                )
            }
        }

        // 2c. Brightness with specific percentage (e.g. "turn up brightness 50%", "turn down brightness 40%", "set brightness to 70%", "brightness 60%")
        val brightnessPercentRegex = Regex("(?:turn\\s*(?:up|down)?\\s*brightness|set\\s*brightness|brightness\\s*(?:up|down|to)?)\\s*(?:to\\s*)?(\\d{1,3})\\s*(?:%|percent)?", RegexOption.IGNORE_CASE)
        val brightnessPercentMatch = brightnessPercentRegex.find(lower)
        if (brightnessPercentMatch != null && lower.contains("brightness")) {
            val percentVal = brightnessPercentMatch.groupValues[1].toIntOrNull()
            if (percentVal != null && percentVal in 0..100) {
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Setting screen brightness to $percentVal percent.",
                        action = ActionDetails("SET_BRIGHTNESS_PERCENT", mapOf("percentage" to percentVal.toString()))
                    )
                )
            }
        }

        // 2d. Relative Brightness (e.g. "turn up brightness", "increase brightness", "turn down brightness", "dim brightness")
        if (lower.contains("brightness") || lower.contains("screen light")) {
            val isDim = lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("dim")
            val targetPercent = if (isDim) 30 else 85
            val reply = if (isDim) "Dimming screen brightness." else "Increasing screen brightness."
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = reply,
                    action = ActionDetails("SET_BRIGHTNESS_PERCENT", mapOf("percentage" to targetPercent.toString()))
                )
            )
        }

        // 3. Media playback (Play, Pause, Resume, Stop, Next, Previous track)
        if (lower == "pause" || lower == "pause music" || lower == "stop music" ||
            lower == "play" || lower == "resume" || lower == "play music" ||
            lower == "toggle music" || lower.startsWith("media pause") || lower.startsWith("media play")
        ) {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Media playback toggled.",
                    action = ActionDetails("MEDIA_PLAY_PAUSE", emptyMap())
                )
            )
        }
        if (lower.startsWith("play ") && lower != "play" && lower != "play music") {
            val musicQuery = raw.substring(5).trim()
                .removeSuffix(" on spotify").removeSuffix(" in spotify")
                .removeSuffix(" on youtube").removeSuffix(" in youtube").trim()
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Playing $musicQuery.",
                    action = ActionDetails("PLAY_MUSIC", mapOf("query" to musicQuery))
                )
            )
        }
        if (lower == "next song" || lower == "next track" || lower == "skip" || lower == "skip song") {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Playing next track.",
                    action = ActionDetails("MEDIA_PLAY_PAUSE", mapOf("key" to "next"))
                )
            )
        }
        if (lower == "previous song" || lower == "previous track") {
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Playing previous track.",
                    action = ActionDetails("MEDIA_PLAY_PAUSE", mapOf("key" to "previous"))
                )
            )
        }

        // 4. Time & Date
        if (lower.contains("what time") || lower.contains("current time") || lower == "time") {
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "It is currently $timeStr.",
                    action = ActionDetails("TELL_TIME_DATE", mapOf("include_date" to "false"))
                )
            )
        }
        if (lower.contains("what date") || lower.contains("today's date") || lower.contains("what day is it") || lower == "date") {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Today is $dateStr.",
                    action = ActionDetails("TELL_TIME_DATE", mapOf("include_date" to "true"))
                )
            )
        }

        // 5. Timer
        val timerPattern = Pattern.compile("(?:set (?:a )?timer (?:for )?|timer (?:for )?)(\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours|hr)?(?:\\s*(?:for|named|called)\\s*(.+))?", Pattern.CASE_INSENSITIVE)
        val timerMatcher = timerPattern.matcher(lower)
        if (timerMatcher.find()) {
            val amount = timerMatcher.group(1)?.toIntOrNull() ?: 60
            val unit = timerMatcher.group(2)?.lowercase() ?: "minute"
            val label = timerMatcher.group(3)?.trim() ?: "Timer"

            val totalSeconds = when {
                unit.startsWith("sec") -> amount
                unit.startsWith("hr") || unit.startsWith("hour") -> amount * 3600
                else -> amount * 60 // minutes
            }
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Timer set for $amount $unit.",
                    action = ActionDetails("SET_TIMER", mapOf(
                        "seconds" to totalSeconds.toString(),
                        "message" to label
                    ))
                )
            )
        }

        // 6. Alarm
        val alarmPattern = Pattern.compile("(?:set (?:an )?alarm (?:for |at )?|wake me up at )(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?:\\s*(?:for|named|called)\\s*(.+))?", Pattern.CASE_INSENSITIVE)
        val alarmMatcher = alarmPattern.matcher(lower)
        if (alarmMatcher.find()) {
            var hour = alarmMatcher.group(1)?.toIntOrNull() ?: 8
            val minutes = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatcher.group(3)?.lowercase()
            val label = alarmMatcher.group(4)?.trim() ?: "Alarm"

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Alarm set for ${String.format(Locale.getDefault(), "%02d:%02d", hour, minutes)}.",
                    action = ActionDetails("SET_ALARM", mapOf(
                        "hour" to hour.toString(),
                        "minutes" to minutes.toString(),
                        "message" to label
                    ))
                )
            )
        }

        // 7. Notes
        val notePrefixes = listOf("take a note", "note that", "create note", "new note", "write note", "remember that", "save note")
        for (prefix in notePrefixes) {
            if (lower.startsWith(prefix)) {
                val content = raw.substring(prefix.length).trim().removePrefix(":").trim()
                if (content.isNotBlank()) {
                    val title = if (content.length > 30) content.take(27) + "..." else content
                    return LocalParseResult(
                        true,
                        AssistantActionPayload(
                            spokenReply = "Saved to notes.",
                            action = ActionDetails("CREATE_NOTE", mapOf(
                                "title" to title,
                                "content" to content
                            ))
                        )
                    )
                }
            }
        }

        // 8. Open / Launch App (e.g. "open whatsapp", "launch chrome", "start youtube", "open camera")
        val openPrefixes = listOf("open ", "launch ", "start ", "go to ", "switch to ")
        for (prefix in openPrefixes) {
            if (lower.startsWith(prefix)) {
                val appTarget = lower.substring(prefix.length).trim().removeSuffix(" app").trim()
                if (appTarget.isNotBlank()) {
                    return LocalParseResult(
                        true,
                        AssistantActionPayload(
                            spokenReply = "Opening ${appTarget.replaceFirstChar { it.uppercase() }}.",
                            action = ActionDetails("OPEN_APP", mapOf("app" to appTarget))
                        )
                    )
                }
            }
        }

        // 9. Phone Calls - Number OR Person's Name
        // Examples: "call 911", "call +123456", "call mom", "call John Doe", "dial Sarah", "phone Alice"
        val callRegex = Regex("^(?:call|dial|phone|make a call to)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val callMatch = callRegex.find(raw)
        if (callMatch != null) {
            val target = callMatch.groupValues[1].trim()
            if (target.isNotBlank()) {
                val isOnlyDigits = target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
                val targetLabel = if (isOnlyDigits) target else target.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Calling $targetLabel.",
                        action = ActionDetails("CALL_PHONE", mapOf(
                            "target" to target,
                            "phone_number" to target
                        ))
                    )
                )
            }
        }

        // 10. SMS - Number OR Person's Name + Message
        // Examples: "text mom I will be home soon", "send sms to 123456 Hello", "message John are you there"
        val smsRegex = Regex("^(?:send (?:an? )?sms|text|send message|message)\\s+(?:to\\s+)?([a-zA-Z0-9+\\s]+?)\\s+(?:saying|that)?\\s*(.+)$", RegexOption.IGNORE_CASE)
        val smsMatch = smsRegex.find(raw)
        if (smsMatch != null) {
            val recipient = smsMatch.groupValues[1].trim()
            val message = smsMatch.groupValues[2].trim()
            if (recipient.isNotBlank() && message.isNotBlank()) {
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Sending message to $recipient.",
                        action = ActionDetails("SEND_SMS", mapOf(
                            "target" to recipient,
                            "phone_number" to recipient,
                            "message" to message
                        ))
                    )
                )
            }
        }

        // 11. Quick Calculations (e.g. "what is 5 plus 10", "calculate 42 * 2")
        val mathPattern = Pattern.compile("(?:what is|calculate|solve)?\\s*(\\d+(?:\\.\\d+)?)\\s*(\\+|plus|\\-|minus|\\*|times|x|multiplied by|\\/|divided by)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        val mathMatcher = mathPattern.matcher(lower)
        if (mathMatcher.find()) {
            val n1 = mathMatcher.group(1)?.toDoubleOrNull()
            val op = mathMatcher.group(2)?.lowercase()
            val n2 = mathMatcher.group(3)?.toDoubleOrNull()

            if (n1 != null && n2 != null && op != null) {
                val res = when {
                    op == "+" || op == "plus" -> n1 + n2
                    op == "-" || op == "minus" -> n1 - n2
                    op == "*" || op == "times" || op == "x" || op == "multiplied by" -> n1 * n2
                    (op == "/" || op == "divided by") && n2 != 0.0 -> n1 / n2
                    else -> null
                }
                if (res != null) {
                    val cleanRes = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
                    return LocalParseResult(
                        true,
                        AssistantActionPayload(
                            spokenReply = "That's $cleanRes.",
                            action = ActionDetails("CALCULATE", mapOf(
                                "expression" to "$n1 $op $n2",
                                "result" to cleanRes
                            ))
                        )
                    )
                }
            }
        }

        // 12. Directions / Maps
        if (lower.startsWith("directions to ") || lower.startsWith("navigate to ") || lower.startsWith("take me to ")) {
            val destination = raw.substringAfter("to ").trim()
            return LocalParseResult(
                true,
                AssistantActionPayload(
                    spokenReply = "Navigating to $destination.",
                    action = ActionDetails("OPEN_MAPS", mapOf("query" to destination))
                )
            )
        }

        // 13. Web Search
        if (lower.startsWith("google ") || lower.startsWith("search for ") || lower.startsWith("search ") || lower.startsWith("look up ")) {
            val query = raw.replaceFirst(Regex("^(google|search for|search|look up)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (query.isNotBlank()) {
                return LocalParseResult(
                    true,
                    AssistantActionPayload(
                        spokenReply = "Searching for $query.",
                        action = ActionDetails("WEB_SEARCH", mapOf("query" to query))
                    )
                )
            }
        }

        return LocalParseResult(false, null)
    }
}
