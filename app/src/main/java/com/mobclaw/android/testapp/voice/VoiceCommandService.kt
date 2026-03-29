package com.mobclaw.android.testapp.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that keeps the microphone listening for the
 * "hey claw" wake phrase. Once detected it captures the spoken command
 * and publishes it via [stateFlow] for the UI to pick up.
 *
 * SpeechRecognizer has a short silence timeout (~5 s), so the service
 * continuously destroys and recreates the recognizer to stay alive.
 */
class VoiceCommandService : Service() {

    companion object {
        private const val TAG = "VoiceCmd"
        private const val CHANNEL_ID = "mobclaw_voice"
        private const val NOTIFICATION_ID = 9001

        private const val MIN_RESTART_DELAY_MS = 250L
        private const val MAX_RESTART_DELAY_MS = 4000L
        private const val BACKOFF_MULTIPLIER = 1.5

        private val _stateFlow = MutableStateFlow(VoiceCommandEvent())
        val stateFlow: StateFlow<VoiceCommandEvent> = _stateFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, VoiceCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCommandService::class.java))
        }
    }

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentMode = VoiceMode.OFF
    private var toneGenerator: ToneGenerator? = null

    private var consecutiveErrors = 0
    private var currentRestartDelay = MIN_RESTART_DELAY_MS
    private var cycleCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        createNotificationChannel()
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed: ${e.message}")
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")
        startForegroundWithNotification("Listening for \"Hey Claw\"...")
        consecutiveErrors = 0
        currentRestartDelay = MIN_RESTART_DELAY_MS
        transitionTo(VoiceMode.STANDBY)
        startWakeWordListening()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy")
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        toneGenerator?.release()
        toneGenerator = null
        transitionTo(VoiceMode.OFF)
        super.onDestroy()
    }

    // ---- State transitions ----

    private fun transitionTo(mode: VoiceMode, transcription: String? = null, error: String? = null) {
        currentMode = mode
        _stateFlow.value = VoiceCommandEvent(mode, transcription, error)
        Log.d(TAG, "State -> $mode ${transcription?.let { "[$it]" } ?: ""} ${error?.let { "err=$it" } ?: ""}")

        val notifText = when (mode) {
            VoiceMode.OFF -> null
            VoiceMode.STANDBY -> "Listening for \"Hey Claw\"..."
            VoiceMode.ACTIVATED -> "Wake word detected!"
            VoiceMode.LISTENING -> "Speak your command..."
            VoiceMode.PROCESSING -> "Processing: ${transcription.orEmpty()}"
            VoiceMode.ERROR -> "Error: ${error.orEmpty()}"
        }
        if (notifText != null) updateNotification(notifText)
    }

    // ---- Wake word listening (STANDBY) ----

    private fun startWakeWordListening() {
        if (currentMode != VoiceMode.STANDBY) {
            Log.w(TAG, "startWakeWordListening skipped, mode=$currentMode")
            return
        }
        destroyRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            transitionTo(VoiceMode.ERROR, error = "Speech recognition not available")
            stopSelf()
            return
        }

        cycleCount++
        Log.d(TAG, "Starting wake-word cycle #$cycleCount (delay was ${currentRestartDelay}ms)")

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { rec ->
                rec.setRecognitionListener(wakeWordListener)
                rec.startListening(buildRecognizerIntent())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start recognizer: ${e.message}")
            scheduleRestart()
        }
    }

    private val wakeWordListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Wake: onReadyForSpeech")
            consecutiveErrors = 0
            currentRestartDelay = MIN_RESTART_DELAY_MS
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Wake: onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Wake: onEndOfSpeech")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val candidates = extractCandidates(partialResults)
            if (candidates.isEmpty()) return
            Log.d(TAG, "Wake partial: $candidates")

            val result = WakeWordDetector.check(candidates)
            if (result.detected) {
                Log.i(TAG, "WAKE WORD DETECTED in partial! trailing=${result.trailingCommand}")
                onWakeWordDetected(result.trailingCommand)
            }
        }

        override fun onResults(results: Bundle?) {
            val candidates = extractCandidates(results)
            Log.d(TAG, "Wake final: $candidates")

            val result = WakeWordDetector.check(candidates)
            if (result.detected) {
                Log.i(TAG, "WAKE WORD DETECTED in final! trailing=${result.trailingCommand}")
                onWakeWordDetected(result.trailingCommand)
            } else {
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            val name = errorName(error)
            Log.d(TAG, "Wake error: $error ($name)")

            if (isFatalError(error)) {
                transitionTo(VoiceMode.ERROR, error = "Recognition error: $name")
                return
            }
            consecutiveErrors++
            currentRestartDelay = (currentRestartDelay * BACKOFF_MULTIPLIER)
                .toLong()
                .coerceAtMost(MAX_RESTART_DELAY_MS)
            scheduleRestart()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---- Wake word detected -> command capture ----

    private fun onWakeWordDetected(trailingCommand: String?) {
        destroyRecognizer()
        transitionTo(VoiceMode.ACTIVATED)

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (_: Exception) {}

        if (!trailingCommand.isNullOrBlank()) {
            dispatchCommand(trailingCommand)
            return
        }

        handler.postDelayed({ startCommandListening() }, 300)
    }

    // ---- Command capture (LISTENING) ----

    private fun startCommandListening() {
        if (currentMode != VoiceMode.ACTIVATED && currentMode != VoiceMode.LISTENING) {
            Log.w(TAG, "startCommandListening skipped, mode=$currentMode")
            return
        }
        transitionTo(VoiceMode.LISTENING)
        destroyRecognizer()

        Log.d(TAG, "Starting command capture")
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { rec ->
                rec.setRecognitionListener(commandListener)
                rec.startListening(buildRecognizerIntent())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start command recognizer: ${e.message}")
            transitionTo(VoiceMode.STANDBY)
            scheduleRestart()
        }
    }

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Cmd: onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Cmd: onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "Cmd: onEndOfSpeech")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val candidates = extractCandidates(partialResults)
            if (candidates.isNotEmpty()) {
                Log.d(TAG, "Cmd partial: $candidates")
            }
        }

        override fun onResults(results: Bundle?) {
            val candidates = extractCandidates(results)
            Log.d(TAG, "Cmd final: $candidates")
            val best = candidates.firstOrNull()?.trim()

            if (!best.isNullOrBlank()) {
                Log.i(TAG, "Command captured: \"$best\"")
                dispatchCommand(best)
            } else {
                Log.w(TAG, "Empty command result, returning to standby")
                transitionTo(VoiceMode.STANDBY)
                startWakeWordListening()
            }
        }

        override fun onError(error: Int) {
            val name = errorName(error)
            Log.w(TAG, "Cmd error: $error ($name)")
            transitionTo(VoiceMode.STANDBY)
            scheduleRestart()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---- Dispatch command to UI ----

    private fun dispatchCommand(command: String) {
        Log.i(TAG, "Dispatching command: \"$command\"")
        transitionTo(VoiceMode.PROCESSING, transcription = command)

        handler.postDelayed({
            transitionTo(VoiceMode.STANDBY)
            startWakeWordListening()
        }, 2000)
    }

    // ---- Helpers ----

    /**
     * Extract all recognition candidate strings from a result bundle.
     * Checks both the stable results key and the unstable partial key
     * because different devices populate different keys.
     *
     * The unstable key can arrive as a LazyValue on some Android versions,
     * so we guard against ClassCastException.
     */
    private fun extractCandidates(bundle: Bundle?): List<String> {
        if (bundle == null) return emptyList()

        val stable = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: emptyList()

        val unstable: String? = try {
            bundle.getString("android.speech.extra.UNSTABLE_TEXT")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        // Combine stable with the first partial for a wider search surface.
        // Some devices put the evolving text in unstable while stable is empty.
        val combined = mutableListOf<String>()
        combined.addAll(stable)
        if (unstable != null) {
            combined.add(unstable)
            // Also try combining stable[0] + unstable as one sentence
            // because partial results split: stable = confirmed, unstable = pending
            if (stable.isNotEmpty()) {
                combined.add("${stable[0]} $unstable")
            }
        }
        return combined
    }

    private fun scheduleRestart() {
        if (currentMode == VoiceMode.OFF) return
        val delay = currentRestartDelay
        Log.d(TAG, "Scheduling restart in ${delay}ms (consecutiveErrors=$consecutiveErrors)")
        handler.postDelayed({
            if (currentMode != VoiceMode.OFF) {
                transitionTo(VoiceMode.STANDBY)
                startWakeWordListening()
            }
        }, delay)
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Force English recognition for the "hey claw" wake phrase
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            // Extend silence timeouts so the recognizer stays alive longer
            putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 10000L)
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 5000L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 3000L)
        }

    private fun isFatalError(errorCode: Int): Boolean = when (errorCode) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> true
        SpeechRecognizer.ERROR_CLIENT -> true
        else -> false
    }

    private fun errorName(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
        else -> "UNKNOWN($errorCode)"
    }

    // ---- Notification ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MobClaw Voice",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Voice command listening status"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MobClaw Voice")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("MobClaw Voice")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
