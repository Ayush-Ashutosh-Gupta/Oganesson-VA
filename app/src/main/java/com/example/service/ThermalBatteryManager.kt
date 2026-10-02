package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thermal and Battery Monitor for Oganesson Assistant.
 * Continuously observes device thermal status and battery power save mode.
 * Dynamically scales down voice recognition duty-cycle, throttles loops,
 * and recommends lightweight AI models to prevent overheating and conserve battery.
 */
class ThermalBatteryManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalBatteryManager"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _isThermalThrottled = MutableStateFlow(false)
    val isThermalThrottled: StateFlow<Boolean> = _isThermalThrottled.asStateFlow()

    private val _batteryTemperatureCelsius = MutableStateFlow(25f)
    val batteryTemperatureCelsius: StateFlow<Float> = _batteryTemperatureCelsius.asStateFlow()

    private val _thermalLevelName = MutableStateFlow("NORMAL")
    val thermalLevelName: StateFlow<String> = _thermalLevelName.asStateFlow()

    private val _batteryLevelPercent = MutableStateFlow(100)
    val batteryLevelPercent: StateFlow<Int> = _batteryLevelPercent.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var isReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val tempCelsius = tempTenths / 10f
                    _batteryTemperatureCelsius.value = tempCelsius

                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        _batteryLevelPercent.value = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                    }
                    updateThrottlingState()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    checkPowerSaveMode()
                }
            }
        }
    }

    init {
        checkPowerSaveMode()
        registerReceivers()
        setupThermalListener()
    }

    private fun checkPowerSaveMode() {
        val powerSave = powerManager?.isPowerSaveMode ?: false
        _isPowerSaveMode.value = powerSave
        updateThrottlingState()
    }

    private fun registerReceivers() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            try {
                context.registerReceiver(batteryReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Error registering battery/power receiver", e)
            }
        }
    }

    var onThermalThrottlingChanged: ((isThrottled: Boolean, pollingDelayMs: Long) -> Unit)? = null

    private fun setupThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    val (isThrottled, name) = when (status) {
                        PowerManager.THERMAL_STATUS_SEVERE,
                        PowerManager.THERMAL_STATUS_CRITICAL,
                        PowerManager.THERMAL_STATUS_EMERGENCY,
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> true to "CRITICAL"
                        PowerManager.THERMAL_STATUS_MODERATE -> true to "MODERATE"
                        PowerManager.THERMAL_STATUS_LIGHT -> false to "LIGHT"
                        else -> false to "NORMAL"
                    }
                    _isThermalThrottled.value = isThrottled
                    _thermalLevelName.value = name
                    Log.d(TAG, "Thermal status observer updated: $name, throttled=$isThrottled")

                    val pollingDelay = getAdaptiveBackoffDelayMs()
                    onThermalThrottlingChanged?.invoke(isThrottled, pollingDelay)
                }
                powerManager.addThermalStatusListener(context.mainExecutor, thermalListener!!)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register thermal status listener: ${e.message}")
            }
        }
    }

    private fun updateThrottlingState() {
        val temp = _batteryTemperatureCelsius.value
        val powerSave = _isPowerSaveMode.value
        val batteryLow = _batteryLevelPercent.value <= 15
        val tempThrottled = temp >= 38.5f
        val shouldThrottle = tempThrottled || powerSave || batteryLow || _thermalLevelName.value in listOf("MODERATE", "CRITICAL")
        _isThermalThrottled.value = shouldThrottle
    }

    var isEcoModeActive: Boolean = false

    /**
     * True if the device is showing elevated thermal strain or high temperature.
     */
    fun isOverheated(): Boolean {
        return _batteryTemperatureCelsius.value >= 39.5f ||
                _thermalLevelName.value in listOf("MODERATE", "CRITICAL")
    }

    /**
     * Determines whether active audio listening should temporarily pause
     * to prevent thermal emergency or shutoff.
     */
    fun shouldPauseVoiceProcessing(): Boolean {
        return _batteryTemperatureCelsius.value >= 42.0f ||
                _thermalLevelName.value == "CRITICAL" ||
                (_isPowerSaveMode.value && _batteryLevelPercent.value <= 10)
    }

    /**
     * Calculates the optimal backoff delay between recognizer restart cycles.
     * In Eco mode, significantly increases the interval to 4-6 seconds to drop CPU usage and eliminate heat.
     */
    fun getAdaptiveBackoffDelayMs(defaultDelayMs: Long = 1000L): Long {
        val temp = _batteryTemperatureCelsius.value
        if (isEcoModeActive) {
            return when {
                _thermalLevelName.value == "CRITICAL" || temp >= 41.5f -> 5000L
                isOverheated() -> 4000L
                _isThermalThrottled.value || _isPowerSaveMode.value -> 3200L
                else -> 2400L.coerceAtLeast(defaultDelayMs * 2)
            }
        }
        return when {
            _thermalLevelName.value == "CRITICAL" || temp >= 41.5f -> 4000L
            isOverheated() -> 2800L
            _isThermalThrottled.value || _isPowerSaveMode.value -> 2000L
            temp >= 37.5f -> 1500L
            else -> defaultDelayMs.coerceIn(700L, 1400L)
        }
    }

    /**
     * Returns true if device conditions warrant switching to an ultra-lightweight AI model
     * (e.g. open-mistral-7b) to save processing and network overhead.
     */
    fun shouldPreferLightweightModel(): Boolean {
        return isEcoModeActive || _isThermalThrottled.value || _isPowerSaveMode.value || isOverheated()
    }

    /**
     * Dynamically chooses the most energy/thermal-efficient model.
     * When device is hot or in battery-saver mode, automatically falls back to open-mistral-7b.
     */
    fun getEffectiveModel(preferredModel: String): String {
        return if (shouldPreferLightweightModel()) {
            Log.d(TAG, "Device thermal throttle or power save active: auto-switching to open-mistral-7b")
            "open-mistral-7b"
        } else {
            preferredModel.ifBlank { "mistral-small-latest" }
        }
    }

    fun cleanup() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null && powerManager != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
            thermalListener = null
        }
    }
}
