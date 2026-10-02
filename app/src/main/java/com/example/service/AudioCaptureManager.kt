package com.example.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Handles direct low-level PCM audio recording using the Android [AudioRecord] API.
 * Captures 16kHz mono 16-bit PCM audio, calculates real-time RMS amplitudes,
 * and packages audio buffers into standard WAV or raw PCM format for offline
 * analysis or transcription via voice AI APIs.
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private var pcmOutputStream: ByteArrayOutputStream? = null

    /**
     * Start recording raw audio using Android's native AudioRecord API
     */
    @Synchronized
    fun startRecording(): Boolean {
        if (_isRecording.value) {
            Log.w(TAG, "Recording is already active")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for AudioRecord: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            pcmOutputStream = ByteArrayOutputStream()

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        pcmOutputStream?.write(buffer, 0, bytesRead)

                        // Calculate RMS amplitude for real-time visualization
                        var sum = 0.0
                        var count = 0
                        var i = 0
                        while (i < bytesRead - 1) {
                            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                            sum += sample * sample
                            count++
                            i += 2
                        }
                        if (count > 0) {
                            val rms = sqrt(sum / count).toFloat()
                            val normalized = (rms / 32768f).coerceIn(0f, 1f)
                            _audioAmplitude.value = normalized
                        }
                    }
                }
            }

            Log.d(TAG, "AudioRecord started successfully at ${SAMPLE_RATE}Hz Mono")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission for AudioRecord", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing or starting AudioRecord", e)
            return false
        }
    }

    /**
     * Stops the audio recording session and returns the captured audio as a WAV file
     */
    @Synchronized
    fun stopRecording(): File? {
        if (!_isRecording.value) return null

        _isRecording.value = false
        _audioAmplitude.value = 0f

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        val rawPcmData = pcmOutputStream?.toByteArray() ?: ByteArray(0)
        pcmOutputStream = null

        if (rawPcmData.isEmpty()) {
            Log.w(TAG, "No audio data was recorded")
            return null
        }

        // Write as valid RIFF WAV file for Audio / Speech APIs
        return try {
            val outputFile = File(context.cacheDir, "voice_input_${System.currentTimeMillis()}.wav")
            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(fos, rawPcmData.size.toLong())
                fos.write(rawPcmData)
            }
            Log.d(TAG, "WAV audio saved: ${outputFile.absolutePath} (${rawPcmData.size} bytes)")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving WAV audio file", e)
            null
        }
    }

    /**
     * Writes standard 44-byte RIFF WAV header for 16kHz mono 16-bit PCM
     */
    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val bitsPerSample = 16
        val byteRate = longSampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * (bitsPerSample / 8)).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    fun release() {
        stopRecording()
    }
}
