package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.service.OganessonService
import com.example.ui.CustomShortcutsScreen
import com.example.ui.MainScreen
import com.example.ui.OganessonViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen {
    MAIN,
    SETTINGS,
    CUSTOM_SHORTCUTS
}

class MainActivity : ComponentActivity() {

    private val viewModel: OganessonViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            checkAndStartBackgroundServiceIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proactively prompt permissions on initial launch
        checkAndPromptInitialPermissions()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    OganessonApp(
                        viewModel = viewModel,
                        onRequestPermissions = { requestAllAssistantPermissions() },
                        onRequestBatteryOptimization = { requestIgnoreBatteryOptimizations() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkAndStartBackgroundServiceIfEnabled()
    }

    override fun onStop() {
        super.onStop()
        // Stop in-app mic listening to prevent hardware contention and thermal strain
        viewModel.stopListening()
        // Ensure background service settles into quiet low-power dormant state
        OganessonService.enterDormantState(this)
    }

    private fun checkAndStartBackgroundServiceIfEnabled() {
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            lifecycleScope.launch {
                val wakeEnabled = viewModel.backgroundWakeWord.first()
                val persistentListening = viewModel.persistentBackgroundListening.first()
                if (wakeEnabled || persistentListening) {
                    OganessonService.start(this@MainActivity)
                }
            }
        }
    }

    private fun checkAndPromptInitialPermissions() {
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            requestAllAssistantPermissions()
        } else {
            checkAndStartBackgroundServiceIfEnabled()
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) != true) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun requestAllAssistantPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())

        // Also check and prompt for SYSTEM_ALERT_WINDOW (Appear on top)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(overlayIntent)
            } catch (e: Exception) {
                // Fallback to general manage overlay screen if package-specific fails
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                } catch (_: Exception) {}
            }
        }
    }
}

@Composable
fun OganessonApp(
    viewModel: OganessonViewModel,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == Screen.SETTINGS || targetState == Screen.CUSTOM_SHORTCUTS) {
                slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
            } else {
                slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            Screen.MAIN -> {
                MainScreen(
                    viewModel = viewModel,
                    onOpenSettings = { currentScreen = Screen.SETTINGS },
                    onOpenCustomShortcuts = { currentScreen = Screen.CUSTOM_SHORTCUTS },
                    onRequestPermissions = onRequestPermissions,
                    onRequestBatteryOptimization = onRequestBatteryOptimization
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.MAIN },
                    onOpenCustomShortcuts = { currentScreen = Screen.CUSTOM_SHORTCUTS },
                    onRequestPermissions = onRequestPermissions,
                    onRequestBatteryOptimization = onRequestBatteryOptimization
                )
            }
            Screen.CUSTOM_SHORTCUTS -> {
                CustomShortcutsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.SETTINGS }
                )
            }
        }
    }
}
