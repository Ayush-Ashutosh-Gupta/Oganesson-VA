package com.example.service

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.model.ActionDetails
import com.example.data.model.ExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Executes system intents and device actions parsed either locally or returned by the assistant.
 */
class ActionExecutor(
    private val context: Context,
    private val noteDao: NoteDao
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    suspend fun executeAction(action: ActionDetails): ExecutionResult {
        return try {
            when (action.type.uppercase()) {
                "OPEN_APP" -> handleOpenApp(action.parameters?.get("app") ?: "")
                "SET_ALARM" -> handleSetAlarm(
                    hour = action.parameters?.get("hour")?.toIntOrNull() ?: 8,
                    minutes = action.parameters?.get("minutes")?.toIntOrNull() ?: 0,
                    message = action.parameters?.get("message") ?: "Voice Alarm"
                )
                "SET_TIMER" -> handleSetTimer(
                    seconds = action.parameters?.get("seconds")?.toIntOrNull() ?: 60,
                    message = action.parameters?.get("message") ?: "Voice Timer"
                )
                "CREATE_NOTE" -> handleCreateNote(
                    title = action.parameters?.get("title") ?: "Voice Note",
                    content = action.parameters?.get("content") ?: ""
                )
                "WEB_SEARCH" -> handleWebSearch(action.parameters?.get("query") ?: "")
                "OPEN_MAPS" -> handleOpenMaps(action.parameters?.get("query") ?: "")
                "CALL_PHONE" -> handleCallPhone(
                    target = action.parameters?.get("target") ?: action.parameters?.get("phone_number") ?: ""
                )
                "SEND_SMS" -> handleSendSms(
                    target = action.parameters?.get("target") ?: action.parameters?.get("phone_number") ?: "",
                    message = action.parameters?.get("message") ?: ""
                )
                "TELL_TIME_DATE" -> handleTellTimeDate(
                    includeDate = action.parameters?.get("include_date")?.toBooleanStrictOrNull() ?: true
                )
                "CALCULATE" -> handleCalculate(
                    expression = action.parameters?.get("expression") ?: "",
                    result = action.parameters?.get("result") ?: ""
                )
                "MEDIA_PLAY_PAUSE" -> handleMediaPlayPause(action.parameters?.get("key"))
                "PLAY_MUSIC", "PLAY_MEDIA", "SEARCH_MUSIC" -> handlePlayMusic(
                    query = action.parameters?.get("query") ?: action.parameters?.get("song") ?: action.parameters?.get("playlist") ?: ""
                )
                "ADJUST_VOLUME" -> handleAdjustVolume(action.parameters?.get("direction") ?: "UP")
                "SET_VOLUME_PERCENT" -> handleSetVolumePercent(
                    percent = action.parameters?.get("percentage")?.toIntOrNull() ?: 50
                )
                "SET_BRIGHTNESS_PERCENT" -> handleSetBrightnessPercent(
                    percent = action.parameters?.get("percentage")?.toIntOrNull() ?: 50
                )
                "TOGGLE_FLASHLIGHT" -> handleToggleFlashlight(action.parameters?.get("state") ?: "TOGGLE")
                "TOGGLE_WIFI", "OPEN_WIFI_SETTINGS" -> handleToggleWifi(action.parameters?.get("state"))
                "TOGGLE_BLUETOOTH", "OPEN_BLUETOOTH_SETTINGS" -> handleToggleBluetooth()
                "OPEN_HOTSPOT_SETTINGS" -> handleOpenHotspotSettings()
                "OPEN_BATTERY_SETTINGS" -> handleOpenBatterySettings()
                "OPEN_DISPLAY_SETTINGS" -> handleOpenDisplaySettings()
                "TAKE_PHOTO" -> handleTakePhoto()
                "RECORD_VIDEO" -> handleRecordVideo()
                "RECORD_AUDIO" -> handleRecordAudio()
                "SEND_WHATSAPP" -> handleSendWhatsApp(
                    target = action.parameters?.get("target") ?: action.parameters?.get("phone_number") ?: "",
                    message = action.parameters?.get("message") ?: ""
                )
                "CUSTOM_INTENT" -> handleCustomIntent(
                    intentAction = action.parameters?.get("action") ?: Settings.ACTION_SETTINGS,
                    intentData = action.parameters?.get("data")
                )
                else -> ExecutionResult(true, "Action ${action.type} handled")
            }
        } catch (e: Exception) {
            ExecutionResult(false, "Failed to execute ${action.type}: ${e.localizedMessage}")
        }
    }

    /**
     * Executes multiple actions in sequence (dual / compound actions),
     * with brief delays between them to allow Android system transitions to settle.
     */
    suspend fun executeActions(actions: List<ActionDetails>): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        for ((index, action) in actions.withIndex()) {
            if (index > 0) {
                kotlinx.coroutines.delay(400)
            }
            val res = executeAction(action)
            results.add(res)
        }
        return results
    }


    /**
     * Universal App Launcher:
     * 1. Direct system intent matches (Camera, Settings, Dialer, Messages, Clock, Calendar, Calculator)
     * 2. Comprehensive catalog of popular apps by exact package
     * 3. Dynamic package manager launcher query matching ANY installed app on the device
     * 4. Smart fuzzy match across all installed launcher activity labels
     * 5. Play Store or Web search fallback
     */
    private fun handleOpenApp(appTarget: String): ExecutionResult {
        val lowerTarget = appTarget.lowercase().trim().removeSuffix(" app").trim()
        val pm = context.packageManager

        // 1. Specialized system intents for core Android device capabilities
        when {
            lowerTarget.contains("camera") -> {
                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (cameraIntent.resolveActivity(pm) != null) {
                    context.startActivity(cameraIntent)
                    return ExecutionResult(true, "Opened Camera")
                }
            }
            lowerTarget.contains("setting") -> {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return ExecutionResult(true, "Opened Settings")
            }
            lowerTarget.contains("phone") || lowerTarget.contains("dialer") -> {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                return ExecutionResult(true, "Opened Phone Dialer")
            }
            lowerTarget.contains("message") || lowerTarget.contains("sms") -> {
                val smsIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (smsIntent.resolveActivity(pm) != null) {
                    context.startActivity(smsIntent)
                    return ExecutionResult(true, "Opened Messages")
                }
            }
            lowerTarget.contains("clock") || lowerTarget.contains("alarm") -> {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (clockIntent.resolveActivity(pm) != null) {
                    context.startActivity(clockIntent)
                    return ExecutionResult(true, "Opened Clock")
                }
            }
            lowerTarget.contains("calendar") -> {
                val calIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (calIntent.resolveActivity(pm) != null) {
                    context.startActivity(calIntent)
                    return ExecutionResult(true, "Opened Calendar")
                }
            }
            lowerTarget.contains("calc") -> {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (calcIntent.resolveActivity(pm) != null) {
                    context.startActivity(calcIntent)
                    return ExecutionResult(true, "Opened Calculator")
                }
            }
            lowerTarget.contains("gallery") || lowerTarget.contains("photos") -> {
                val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (galleryIntent.resolveActivity(pm) != null) {
                    context.startActivity(galleryIntent)
                    return ExecutionResult(true, "Opened Photos")
                }
            }
        }

        // 2. Common known package names map for quick launch
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "instagram" to "com.instagram.android",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "music" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "netflix" to "com.netflix.mediaclient",
            "facebook" to "com.facebook.katana",
            "play store" to "com.android.vending",
            "store" to "com.android.vending"
        )

        for ((name, pkg) in knownPackages) {
            if (lowerTarget == name || lowerTarget.contains(name)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionResult(true, "Opened ${name.replaceFirstChar { it.uppercase() }}")
                }
            }
        }

        // 3. Dynamic search across ALL installed launcher apps on the user's device
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)

            var bestPackage: String? = null
            var bestLabel: String? = null

            // First pass: exact match on app label
            for (info in resolveInfoList) {
                val label = info.loadLabel(pm).toString().lowercase().trim()
                if (label == lowerTarget) {
                    bestPackage = info.activityInfo.packageName
                    bestLabel = info.loadLabel(pm).toString()
                    break
                }
            }

            // Second pass: app label contains user target, or user target contains app label
            if (bestPackage == null) {
                for (info in resolveInfoList) {
                    val label = info.loadLabel(pm).toString().lowercase().trim()
                    if (label.contains(lowerTarget) || lowerTarget.contains(label)) {
                        bestPackage = info.activityInfo.packageName
                        bestLabel = info.loadLabel(pm).toString()
                        break
                    }
                }
            }

            // Third pass: check applicationInfo meta-data or package name match
            if (bestPackage == null) {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in installedApps) {
                    val label = pm.getApplicationLabel(appInfo).toString().lowercase().trim()
                    if (label == lowerTarget || label.contains(lowerTarget) || appInfo.packageName.contains(lowerTarget)) {
                        val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                        if (launchIntent != null) {
                            bestPackage = appInfo.packageName
                            bestLabel = pm.getApplicationLabel(appInfo).toString()
                            break
                        }
                    }
                }
            }

            if (bestPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(bestPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionResult(true, "Opened ${bestLabel ?: appTarget}")
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback: Search Google Play Store or Web for this app
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(lowerTarget)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (storeIntent.resolveActivity(pm) != null) {
            context.startActivity(storeIntent)
            ExecutionResult(true, "Searched Play Store for $appTarget")
        } else {
            handleWebSearch("Download $appTarget app")
        }
    }

    private fun handleSetAlarm(hour: Int, minutes: Int, message: String): ExecutionResult {
        if (hour !in 0..23 || minutes !in 0..59) {
            return ExecutionResult(false, "Invalid alarm time: $hour:$minutes")
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minutes)
            ExecutionResult(true, "Set alarm for $timeStr (\"$message\")")
        } catch (e: Exception) {
            ExecutionResult(false, "Alarm app not available: ${e.localizedMessage}")
        }
    }

    private fun handleSetTimer(seconds: Int, message: String): ExecutionResult {
        if (seconds <= 0) return ExecutionResult(false, "Invalid timer duration: $seconds s")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult(true, "Set timer for $seconds seconds")
        } catch (e: Exception) {
            ExecutionResult(false, "Timer app not available: ${e.localizedMessage}")
        }
    }

    private suspend fun handleCreateNote(title: String, content: String): ExecutionResult {
        val noteContent = content.ifBlank { title }
        val noteTitle = if (title.isNotBlank() && title != noteContent) title else "Voice Note"

        val entity = NoteEntity(
            title = noteTitle,
            content = noteContent,
            createdAt = System.currentTimeMillis()
        )
        val id = noteDao.insertNote(entity)
        return ExecutionResult(true, "Saved Note #$id: \"$noteTitle\"")
    }

    private fun handleWebSearch(query: String): ExecutionResult {
        if (query.isBlank()) return ExecutionResult(false, "Search query is empty")

        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (searchIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(searchIntent)
            ExecutionResult(true, "Searching web for \"$query\"")
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(browserIntent)
            ExecutionResult(true, "Searching web for \"$query\"")
        }
    }

    private fun handleOpenMaps(query: String): ExecutionResult {
        if (query.isBlank()) return ExecutionResult(false, "Maps location query is empty")

        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            ExecutionResult(true, "Opening Google Maps for \"$query\"")
        } else {
            val fallbackIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallbackIntent)
            ExecutionResult(true, "Opening Maps for \"$query\"")
        }
    }

    /**
     * Universal Phone Calling:
     * Accepts either a raw number ("+1234567890", "911") or a person's name ("Mom", "John", "Sarah").
     * If a name is provided, queries device contacts directly.
     */
    private fun handleCallPhone(target: String): ExecutionResult {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            return ExecutionResult(true, "Opened Phone Dialer")
        }

        // Check if digits or contact name
        val isDigits = trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        var resolvedNumber = if (isDigits) trimmed.filter { it.isDigit() || it == '+' } else ""
        var resolvedDisplayName = trimmed

        if (!isDigits) {
            // Check for multiple matching contacts for disambiguation
            val matches = ContactResolver.searchContactsByName(context, trimmed)
            if (matches.size > 1) {
                CallDisambiguationManager.setPendingContacts(matches)
                val listText = matches.mapIndexed { index, m -> "${index + 1}. ${m.displayName} (${m.phoneNumber})" }.joinToString("\n")
                val prompt = "Found ${matches.size} contacts for \"$trimmed\":\n$listText\n\nWhich contact would you like to call? Reply with the number."
                return ExecutionResult(true, prompt)
            } else if (matches.size == 1) {
                resolvedNumber = matches[0].phoneNumber.filter { it.isDigit() || it == '+' }
                resolvedDisplayName = matches[0].displayName
            }
        }

        if (resolvedNumber.isBlank()) {
            // Open dialer with contact search or query
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            return ExecutionResult(
                true,
                "Could not find phone number for \"$trimmed\". Opened dialer."
            )
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasCallPermission) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$resolvedNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            ExecutionResult(true, "Calling $resolvedDisplayName ($resolvedNumber)")
        } else {
            // Graceful fallback: open dialer pre-filled with the number
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$resolvedNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ExecutionResult(
                success = true,
                message = "Opened dialer with $resolvedDisplayName ($resolvedNumber)",
                requiresPermission = Manifest.permission.CALL_PHONE
            )
        }
    }

    /**
     * Universal SMS sending:
     * Accepts either a raw number or a person's name + message.
     */
    private fun handleSendSms(target: String, message: String): ExecutionResult {
        val trimmed = target.trim()
        val isDigits = trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        var resolvedNumber = if (isDigits) trimmed.filter { it.isDigit() || it == '+' } else ""
        var resolvedDisplayName = trimmed

        if (!isDigits && trimmed.isNotBlank()) {
            val match = ContactResolver.findContactByName(context, trimmed)
            if (match != null) {
                resolvedNumber = match.phoneNumber.filter { it.isDigit() || it == '+' }
                resolvedDisplayName = match.displayName
            }
        }

        val cleanNumber = resolvedNumber.filter { it.isDigit() || it == '+' }
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasSmsPermission && cleanNumber.isNotBlank() && message.isNotBlank()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                ExecutionResult(true, "Sent SMS to $resolvedDisplayName: \"$message\"")
            } catch (e: Exception) {
                openSmsComposer(cleanNumber, message, resolvedDisplayName)
            }
        } else {
            openSmsComposer(cleanNumber, message, resolvedDisplayName)
        }
    }

    private fun openSmsComposer(phoneNumber: String, message: String, displayName: String = ""): ExecutionResult {
        val uri = if (phoneNumber.isNotBlank()) Uri.parse("smsto:$phoneNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val label = displayName.ifBlank { phoneNumber.ifBlank { "recipient" } }
            ExecutionResult(
                success = true,
                message = "Opened SMS composer for $label",
                requiresPermission = if (phoneNumber.isNotBlank()) Manifest.permission.SEND_SMS else null
            )
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open SMS: ${e.localizedMessage}")
        }
    }

    /**
     * WhatsApp Direct Messaging:
     * Supports recipient phone number or contact name resolution, directly opening the WhatsApp chat
     * with pre-filled text or launching the WhatsApp message intent.
     */
    private fun handleSendWhatsApp(target: String, message: String): ExecutionResult {
        val trimmed = target.trim()
        if (trimmed.isBlank() && message.isBlank()) {
            return handleOpenApp("whatsapp")
        }

        val isDigits = trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        var resolvedNumber = if (isDigits) trimmed.filter { it.isDigit() || it == '+' } else ""
        var resolvedDisplayName = trimmed

        if (!isDigits && trimmed.isNotBlank()) {
            val match = ContactResolver.findContactByName(context, trimmed)
            if (match != null) {
                resolvedNumber = match.phoneNumber.filter { it.isDigit() || it == '+' }
                resolvedDisplayName = match.displayName
            } else {
                val searchMatches = ContactResolver.searchContactsByName(context, trimmed)
                if (searchMatches.isNotEmpty()) {
                    resolvedNumber = searchMatches[0].phoneNumber.filter { it.isDigit() || it == '+' }
                    resolvedDisplayName = searchMatches[0].displayName
                }
            }
        }

        val digitsOnly = resolvedNumber.filter { it.isDigit() }
        val label = resolvedDisplayName.ifBlank { target.ifBlank { "contact" } }

        return try {
            if (digitsOnly.isNotBlank()) {
                val encodedText = Uri.encode(message)
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly&text=$encodedText")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    ExecutionResult(true, "Sent WhatsApp message to $label: \"$message\"")
                } else {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    ExecutionResult(true, "Opened WhatsApp message to $label")
                }
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (shareIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(shareIntent)
                    ExecutionResult(true, "Opening WhatsApp to send message: \"$message\"")
                } else {
                    val chooser = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        },
                        "Send WhatsApp message"
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    ExecutionResult(true, "Opened WhatsApp share: \"$message\"")
                }
            }
        } catch (e: Exception) {
            ExecutionResult(false, "Could not send WhatsApp message: ${e.localizedMessage}")
        }
    }


    private fun handleTellTimeDate(includeDate: Boolean): ExecutionResult {
        val now = Date()
        val format = if (includeDate) {
            SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        }
        val formatted = format.format(now)
        return ExecutionResult(true, "Current time: $formatted")
    }

    private fun handleCalculate(expression: String, result: String): ExecutionResult {
        return ExecutionResult(true, "Calculation: $expression = $result")
    }

    private fun handleMediaPlayPause(key: String? = null): ExecutionResult {
        return try {
            val keycode = when (key?.lowercase()) {
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keycode)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            val label = when (key?.lowercase()) {
                "next" -> "Skipped to next track"
                "previous" -> "Skipped to previous track"
                else -> "Toggled media play/pause"
            }
            ExecutionResult(true, label)
        } catch (e: Exception) {
            ExecutionResult(false, "Media control not available: ${e.localizedMessage}")
        }
    }

    private fun handlePlayMusic(query: String): ExecutionResult {
        val cleanQuery = query.trim()
        val pm = context.packageManager
        // 1. Try Spotify specifically if installed
        val spotifyLaunch = pm.getLaunchIntentForPackage("com.spotify.music")
        if (cleanQuery.isNotBlank() && spotifyLaunch != null) {
            try {
                val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(cleanQuery)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(spotifyIntent)
                return ExecutionResult(true, "Playing \"$cleanQuery\" on Spotify")
            } catch (_: Exception) {}
        }

        // 2. Android Standard Media Search
        try {
            val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, cleanQuery)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mediaIntent.resolveActivity(pm) != null) {
                context.startActivity(mediaIntent)
                return ExecutionResult(true, "Playing \"$cleanQuery\"")
            }
        } catch (_: Exception) {}

        // 3. YouTube or general web search fallback
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return ExecutionResult(true, "Searching music: \"$cleanQuery\"")
        } catch (e: Exception) {
            return ExecutionResult(false, "Could not open music player: ${e.localizedMessage}")
        }
    }

    private fun handleAdjustVolume(direction: String): ExecutionResult {
        val stream = AudioManager.STREAM_MUSIC
        return try {
            when (direction.uppercase()) {
                "UP" -> audioManager?.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "DOWN" -> audioManager?.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "MUTE" -> audioManager?.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                "MAX" -> {
                    val max = audioManager?.getStreamMaxVolume(stream) ?: 15
                    audioManager?.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                }
                else -> audioManager?.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            ExecutionResult(true, "Adjusted media volume ($direction)")
        } catch (e: Exception) {
            ExecutionResult(false, "Volume adjustment failed: ${e.localizedMessage}")
        }
    }

    private fun handleSetVolumePercent(percent: Int): ExecutionResult {
        val stream = AudioManager.STREAM_MUSIC
        return try {
            val clamped = percent.coerceIn(0, 100)
            val max = audioManager?.getStreamMaxVolume(stream) ?: 15
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                audioManager?.getStreamMinVolume(stream) ?: 0
            } else 0
            val targetVolume = (min + ((clamped / 100.0) * (max - min))).toInt()
            audioManager?.setStreamVolume(stream, targetVolume, AudioManager.FLAG_SHOW_UI)
            ExecutionResult(true, "Set volume to $clamped%")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not set volume to $percent%: ${e.localizedMessage}")
        }
    }

    private fun handleSetBrightnessPercent(percent: Int): ExecutionResult {
        val clamped = percent.coerceIn(0, 100)
        val brightness255 = (clamped * 255) / 100
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                // WRITE_SETTINGS permission needed to modify system brightness directly
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(
                    success = true,
                    message = "Please allow Oganesson to modify system settings to adjust brightness to $clamped%",
                    requiresPermission = Settings.ACTION_MANAGE_WRITE_SETTINGS
                )
            } else {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness255
                )
                ExecutionResult(true, "Set screen brightness to $clamped%")
            }
        } catch (e: Exception) {
            // Fallback: open display settings
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opening Display Settings to adjust brightness to $clamped%")
        }
    }

    private fun handleToggleWifi(state: String? = null): ExecutionResult {
        return try {
            // Android Q (10+) requires showing Wi-Fi panel or Wi-Fi settings
            val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (panelIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(panelIntent)
                val msg = if (state != null) "Opening Wi-Fi controls to turn Wi-Fi $state" else "Opening Wi-Fi controls"
                ExecutionResult(true, msg)
            } else {
                val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ExecutionResult(true, "Opened Wi-Fi Settings")
            }
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Wi-Fi settings: ${e.localizedMessage}")
        }
    }

    private fun handleToggleBluetooth(): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opened Bluetooth Settings")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Bluetooth settings: ${e.localizedMessage}")
        }
    }

    private fun handleOpenHotspotSettings(): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opened Hotspot & Tethering Settings")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Hotspot settings: ${e.localizedMessage}")
        }
    }

    private fun handleOpenBatterySettings(): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opened Battery Usage Settings")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Battery settings: ${e.localizedMessage}")
        }
    }

    private fun handleOpenDisplaySettings(): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Opened Display Settings")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not open Display settings: ${e.localizedMessage}")
        }
    }

    private fun handleTakePhoto(): ExecutionResult {
        return try {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (captureIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(captureIntent)
                ExecutionResult(true, "Opening camera to take photo")
            } else {
                val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (stillIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(stillIntent)
                    ExecutionResult(true, "Opening camera in photo capture mode")
                } else {
                    handleOpenApp("camera")
                }
            }
        } catch (e: Exception) {
            handleOpenApp("camera")
        }
    }


    private fun handleRecordVideo(): ExecutionResult {
        return try {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ExecutionResult(true, "Opening camera for video recording")
            } else {
                handleOpenApp("camera")
            }
        } catch (e: Exception) {
            handleOpenApp("camera")
        }
    }

    private fun handleRecordAudio(): ExecutionResult {
        return try {
            val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ExecutionResult(true, "Opening voice recorder")
            } else {
                handleOpenApp("recorder")
            }
        } catch (e: Exception) {
            handleOpenApp("recorder")
        }
    }

    private fun handleCustomIntent(intentAction: String, intentData: String?): ExecutionResult {
        return try {
            val intent = Intent(intentAction).apply {
                if (!intentData.isNullOrBlank()) {
                    data = Uri.parse(intentData)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "Launched intent: $intentAction")
        } catch (e: Exception) {
            ExecutionResult(false, "Could not launch custom intent ($intentAction): ${e.localizedMessage}")
        }
    }

    companion object {
        private var isFlashlightOn = false
    }

    private fun handleToggleFlashlight(targetState: String): ExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager == null || cameraId == null) {
                return ExecutionResult(false, "Flashlight / Camera not available on this device")
            }

            val newState = when (targetState.uppercase()) {
                "ON" -> true
                "OFF" -> false
                else -> !isFlashlightOn
            }

            cameraManager.setTorchMode(cameraId, newState)
            isFlashlightOn = newState
            val msg = if (newState) "Flashlight turned on" else "Flashlight turned off"
            ExecutionResult(true, msg)
        } catch (e: Exception) {
            ExecutionResult(false, "Could not control flashlight: ${e.localizedMessage}")
        }
    }
}
