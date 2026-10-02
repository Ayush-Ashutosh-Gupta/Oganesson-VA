package com.example.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import com.example.R
import com.example.service.CallDisambiguationManager
import com.example.service.ContactResolver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayState {
    LISTENING,
    THINKING,
    REPLYING,
    ACTION_EXECUTED
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val mViewModelStore = ViewModelStore()

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onStop() {
        try {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            mViewModelStore.clear()
        } catch (_: Exception) {}
    }
}

/**
 * Manages the floating overlay window via Android WindowManager.
 * Displays a sleek Siri/Bixby style interactive bottom overlay on top of any active app or Home Screen.
 */
class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: ComposeView? = null
    private var isOverlayAttached = false
    private var currentLifecycleOwner: OverlayLifecycleOwner? = null

    private val _overlayState = MutableStateFlow(OverlayState.LISTENING)
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _userSpokenText = MutableStateFlow("")
    val userSpokenText: StateFlow<String> = _userSpokenText.asStateFlow()

    private val _assistantReplyText = MutableStateFlow("")
    val assistantReplyText: StateFlow<String> = _assistantReplyText.asStateFlow()

    private val _actionFeedbackText = MutableStateFlow<String?>(null)
    val actionFeedbackText: StateFlow<String?> = _actionFeedbackText.asStateFlow()

    private var autoDismissRunnable: Runnable? = null
    var onDismissRequested: (() -> Unit)? = null
    var onCallContactSelected: ((ContactResolver.ContactMatch) -> Unit)? = null
    var onMicTapRequested: (() -> Unit)? = null

    fun isOverlayActive(): Boolean = isOverlayAttached && overlayView != null

    companion object {
        private const val TAG = "FloatingOverlayManager"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    fun showOverlay(initialQuery: String? = null) {
        if (!canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        mainHandler.post {
            cancelAutoDismiss()
            _userSpokenText.value = initialQuery ?: ""
            _assistantReplyText.value = ""
            _actionFeedbackText.value = null
            _rmsLevel.value = 0f
            _overlayState.value = if (initialQuery.isNullOrBlank()) OverlayState.LISTENING else OverlayState.THINKING

            createAndAttachOverlay()
        }
    }

    private fun createAndAttachOverlay() {
        try {
            val oldView = overlayView
            val oldOwner = currentLifecycleOwner
            overlayView = null
            isOverlayAttached = false
            currentLifecycleOwner = null

            if (oldView != null) {
                try {
                    oldOwner?.onStop()
                    oldView.disposeComposition()
                    windowManager.removeViewImmediate(oldView)
                } catch (_: Exception) {
                    try {
                        windowManager.removeView(oldView)
                    } catch (_: Exception) {}
                }
            }

            val owner = OverlayLifecycleOwner()
            currentLifecycleOwner = owner

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 40 // Padding from bottom of screen
                windowAnimations = android.R.style.Animation_Dialog
            }

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setContent {
                    MyApplicationTheme(darkTheme = true) {
                        FloatingOverlayContent(
                            stateFlow = overlayState,
                            rmsLevelFlow = rmsLevel,
                            userTextFlow = userSpokenText,
                            replyTextFlow = assistantReplyText,
                            actionFeedbackFlow = actionFeedbackText,
                            onCallContact = { contact ->
                                onCallContactSelected?.invoke(contact)
                            },
                            onDismiss = {
                                dismissOverlay()
                                onDismissRequested?.invoke()
                            },
                            onMicTap = {
                                onMicTapRequested?.invoke()
                            }
                        )
                    }
                }
            }

            windowManager.addView(composeView, params)
            overlayView = composeView
            isOverlayAttached = true
            owner.onStart()
            Log.d(TAG, "Floating overlay successfully attached to WindowManager with fresh session")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay", e)
            isOverlayAttached = false
        }
    }

    fun updateRmsLevel(rms: Float) {
        _rmsLevel.value = rms.coerceIn(0f, 1f)
    }

    fun updateListeningState(partialUserText: String = "", clearPreviousUserText: Boolean = false) {
        mainHandler.post {
            cancelAutoDismiss()
            _overlayState.value = OverlayState.LISTENING
            if (clearPreviousUserText) {
                _userSpokenText.value = partialUserText
            } else if (partialUserText.isNotBlank()) {
                _userSpokenText.value = partialUserText
            }
        }
    }

    fun updateThinkingState(finalUserText: String) {
        mainHandler.post {
            cancelAutoDismiss()
            _overlayState.value = OverlayState.THINKING
            _rmsLevel.value = 0f
            _userSpokenText.value = finalUserText
        }
    }

    fun updateReplyState(reply: String, actionFeedback: String? = null) {
        mainHandler.post {
            cancelAutoDismiss()
            _overlayState.value = if (actionFeedback != null) OverlayState.ACTION_EXECUTED else OverlayState.REPLYING
            _assistantReplyText.value = reply
            _actionFeedbackText.value = actionFeedback
            _rmsLevel.value = 0f

            // Generous fallback auto-dismiss (45s) in case the user walks away from overlay
            scheduleAutoDismiss(45_000)
        }
    }

    fun dismissOverlay() {
        mainHandler.post {
            cancelAutoDismiss()
            val oldView = overlayView
            val oldOwner = currentLifecycleOwner
            overlayView = null
            isOverlayAttached = false
            currentLifecycleOwner = null

            if (oldView != null) {
                try {
                    oldOwner?.onStop()
                    oldView.disposeComposition()
                    windowManager.removeViewImmediate(oldView)
                } catch (e: Exception) {
                    try {
                        windowManager.removeView(oldView)
                    } catch (_: Exception) {}
                    Log.w(TAG, "Error removing overlay view: ${e.message}")
                }
            }
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        cancelAutoDismiss()
        val runnable = Runnable {
            dismissOverlay()
        }
        autoDismissRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let {
            mainHandler.removeCallbacks(it)
            autoDismissRunnable = null
        }
    }

    fun destroy() {
        cancelAutoDismiss()
        dismissOverlay()
    }
}

/**
 * Modern Siri/Bixby inspired floating assistant overlay:
 * - Left: Glowing App Orb / Icon that expands and contracts as the user speaks.
 * - Middle: Responsive audio sound wave / equalizer bars.
 * - Right: Stylized "OG" branded monogram badge & dismiss control.
 * - Bottom: Full un-truncated user command text and assistant response cards.
 */
@Composable
fun FloatingOverlayContent(
    stateFlow: StateFlow<OverlayState>,
    rmsLevelFlow: StateFlow<Float>,
    userTextFlow: StateFlow<String>,
    replyTextFlow: StateFlow<String>,
    actionFeedbackFlow: StateFlow<String?>,
    onCallContact: ((ContactResolver.ContactMatch) -> Unit)? = null,
    onDismiss: () -> Unit,
    onMicTap: (() -> Unit)? = null
) {
    val state by stateFlow.collectAsState()
    val rmsLevel by rmsLevelFlow.collectAsState()
    val userText by userTextFlow.collectAsState()
    val replyText by replyTextFlow.collectAsState()
    val actionFeedback by actionFeedbackFlow.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = DarkBackground.copy(alpha = 0.95f),
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        AtomicCyan.copy(alpha = 0.85f),
                        ElectricBlue.copy(alpha = 0.6f),
                        Color(0xFF7C4DFF).copy(alpha = 0.75f)
                    )
                )
            ),
            shadowElevation = 20.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Top Siri / Bixby style Assistant Interactive Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT: Siri/Bixby style expanding/contracting App Icon / Orb (Tap to speak next command)
                    SiriBixbyAppOrb(
                        rmsLevel = rmsLevel,
                        state = state,
                        onMicTap = onMicTap
                    )

                    // MIDDLE: Sound Wave equalizer bars
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SiriSoundWave(
                            state = state,
                            rmsLevel = rmsLevel
                        )
                    }

                    // RIGHT: "OG" Branded Monogram Badge, Mic button & Dismiss Icon
                    OgBrandBadge(
                        state = state,
                        onMicTap = onMicTap,
                        onDismiss = onDismiss
                    )
                }

                // Continuous Listening Status Pill (active follow-up indicator)
                if (state == OverlayState.LISTENING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AtomicCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Listening",
                                    tint = AtomicCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (userText.isNotBlank()) "Listening: \"$userText\"" else "Continuous Listening Active • Speak command",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AtomicCyan
                                )
                            }
                        }
                    }
                }

                // Spoken User Command Bubble (completely visible, no text cutoff)
                if (userText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurfaceVariant.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = AtomicCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "\"$userText\"",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                softWrap = true
                            )
                        }
                    }
                }

                // Assistant Spoken Reply Bubble (scrollable for comprehensive detailed responses)
                if (replyText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1E2E).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(0xFF7C4DFF).copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = Color(0xFF9D65FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = replyText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 20.sp,
                                    softWrap = true
                                )

                                if (!actionFeedback.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = Color(0xFF00E676).copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "✓ $actionFeedback",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF00E676),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            softWrap = true
                                        )
                                    }
                                }

                                // Interactive Contact Disambiguation Options (Numbered for easy speech or tap)
                                val pendingContacts = CallDisambiguationManager.getPendingContacts()
                                if (pendingContacts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        pendingContacts.forEachIndexed { idx, contact ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF22263D),
                                                border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        CallDisambiguationManager.clear()
                                                        onCallContact?.invoke(contact)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .background(AtomicCyan, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = contact.displayName,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = contact.phoneNumber,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFA0A5C0)
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.Call,
                                                        contentDescription = "Call",
                                                        tint = AtomicCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Siri/Bixby style animated App Icon / Orb.
 * Expands and contracts dynamically in real-time as the user speaks,
 * modulated by audio RMS level and assistant state.
 */
@Composable
fun SiriBixbyAppOrb(
    rmsLevel: Float,
    state: OverlayState,
    onMicTap: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Smooth animated scaling reacting to speech RMS or state
    val targetScale = when (state) {
        OverlayState.LISTENING -> {
            if (rmsLevel > 0.05f) {
                0.95f + (rmsLevel * 0.45f).coerceIn(0f, 0.45f)
            } else {
                idlePulse
            }
        }
        OverlayState.THINKING -> idlePulse * 1.05f
        OverlayState.REPLYING -> 1.05f
        OverlayState.ACTION_EXECUTED -> 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(120),
        label = "animatedScale"
    )

    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(animatedScale)
            .clickable(enabled = onMicTap != null) { onMicTap?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing aura ring
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AtomicCyan.copy(alpha = 0.45f),
                            Color(0xFF7C4DFF).copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // Middle glowing ring
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AtomicCyan,
                            ElectricBlue,
                            Color(0xFF7C4DFF)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Core Atom / App Icon emblem (Actual App Atom Icon)
            Image(
                painter = painterResource(id = R.drawable.ic_oganesson_logo),
                contentDescription = "Oganesson App Icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Animated sound wave equalizer bars in the center of the overlay.
 */
@Composable
fun SiriSoundWave(
    state: OverlayState,
    rmsLevel: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(440), RepeatMode.Reverse), label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "h4"
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(410), RepeatMode.Reverse), label = "h5"
    )

    // Modulate equalizer heights based on actual audio RMS speech level
    val multiplier = if (state == OverlayState.LISTENING) {
        if (rmsLevel > 0.05f) (0.8f + (rmsLevel * 1.6f)).coerceIn(0.8f, 2.2f) else 0.85f
    } else if (state == OverlayState.THINKING) {
        0.5f
    } else {
        0.75f
    }

    val barHeights = listOf(h1, h2, h3, h4, h5, h4, h3, h2, h1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEach { baseHeight ->
            val computedHeight = (baseHeight * multiplier).coerceIn(4f, 26f)
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .width(3.5.dp)
                    .height(computedHeight.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AtomicCyan,
                                ElectricBlue,
                                Color(0xFF7C4DFF)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun OgBrandBadge(
    state: OverlayState,
    onMicTap: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state != OverlayState.LISTENING && onMicTap != null) {
            IconButton(
                onClick = onMicTap,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to Speak Next Command",
                    tint = AtomicCyan,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        // "OG" Monogram Pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceVariant.copy(alpha = 0.9f),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(AtomicCyan.copy(alpha = 0.7f), Color(0xFF7C4DFF).copy(alpha = 0.7f))
                )
            ),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AtomicCyan,
                    letterSpacing = 1.sp
                )
            }
        }

        // Close / Dismiss button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss Assistant",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
