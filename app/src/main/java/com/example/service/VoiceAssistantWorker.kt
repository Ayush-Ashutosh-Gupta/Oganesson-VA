package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based job scheduler for Oganesson voice assistant background service.
 * Manages low-power dormant states, throttles background resource consumption,
 * respects battery constraints, and cleanly ensures service health without CPU overheating.
 */
class VoiceAssistantWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "VoiceAssistantWorker"
        private const val WORK_NAME = "OganessonAssistantHealthWork"
        private const val DORMANT_CLEANUP_WORK = "OganessonDormantCleanupWork"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<VoiceAssistantWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled VoiceAssistantWorker with low-battery protection")
        }

        fun onOverlayClosed(context: Context) {
            val cleanupRequest = androidx.work.OneTimeWorkRequestBuilder<VoiceAssistantWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                DORMANT_CLEANUP_WORK,
                androidx.work.ExistingWorkPolicy.REPLACE,
                cleanupRequest
            )
            Log.d(TAG, "Enqueued WorkManager dormant cleanup job on overlay close")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(DORMANT_CLEANUP_WORK)
            Log.d(TAG, "Cancelled VoiceAssistantWorker")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "VoiceAssistantWorker executing resource health & thermal check")
        try {
            val thermalBatteryManager = ThermalBatteryManager(context)
            val isOverheated = thermalBatteryManager.isOverheated()
            val shouldPause = thermalBatteryManager.shouldPauseVoiceProcessing()

            val preferencesManager = PreferencesManager(context)
            val wakeEnabled = preferencesManager.backgroundWakeWord.first()
            val persistentListening = preferencesManager.persistentBackgroundListening.first()

            if (shouldPause) {
                Log.w(TAG, "Device overheated or battery critical: instructing service to enter dormant state")
                OganessonService.enterDormantState(context)
            } else if (wakeEnabled || persistentListening) {
                // Ensure service is running in low-power dormant state
                OganessonService.start(context)
            }
            thermalBatteryManager.cleanup()
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in VoiceAssistantWorker", e)
            return Result.retry()
        }
    }
}
