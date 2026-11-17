package com.example.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "MeroWakeWordEngine"

class MeroWakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (trailingCommand: String?) -> Unit,
    private val onUnavailable: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldRun = false
    private var isListeningCycle = false
    private var partialWakeMatched = false

    private val _isListeningForWakeWord = MutableStateFlow(false)
    val isListeningForWakeWord: StateFlow<Boolean> = _isListeningForWakeWord.asStateFlow()

    private val restartRunnable = Runnable {
        if (shouldRun && !isListeningCycle) {
            beginRecognitionCycle()
        }
    }

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    }

    fun start() {
        mainHandler.post {
            if (!hasMicPermission()) {
                stopInternal()
                return@post
            }
            if (!isAvailable()) {
                stopInternal()
                onUnavailable("On-device speech recognition is unavailable on this device.")
                return@post
            }
            if (shouldRun && isListeningCycle) return@post

            shouldRun = true
            mainHandler.removeCallbacks(restartRunnable)
            beginRecognitionCycle()
        }
    }

    fun stop() {
        mainHandler.post {
            stopInternal()
        }
    }

    fun release() {
        mainHandler.post {
            stopInternal()
        }
    }

    private fun stopInternal() {
        shouldRun = false
        isListeningCycle = false
        partialWakeMatched = false
        mainHandler.removeCallbacks(restartRunnable)
        _isListeningForWakeWord.value = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun beginRecognitionCycle() {
        if (!shouldRun || !hasMicPermission()) return

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null

        val recognizer = createRecognizer() ?: run {
            shouldRun = false
            _isListeningForWakeWord.value = false
            onUnavailable("Unable to start wake-word speech recognizer.")
            return
        }

        partialWakeMatched = false
        recognizer.setRecognitionListener(createListener())
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }

        try {
            isListeningCycle = true
            _isListeningForWakeWord.value = true
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake-word listening", e)
            isListeningCycle = false
            _isListeningForWakeWord.value = false
            scheduleRestart(1500L)
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Primary recognizer creation failed, falling back", e)
            try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (fallbackErr: Exception) {
                Log.e(TAG, "Fallback recognizer creation failed", fallbackErr)
                null
            }
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListeningForWakeWord.value = shouldRun
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListeningCycle = false
        }

        override fun onError(error: Int) {
            isListeningCycle = false
            if (!shouldRun) return

            if (partialWakeMatched) {
                triggerWakeWord(trailingCommand = null)
                return
            }

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stopInternal()
                    onUnavailable("Microphone permission is required for wake-word detection.")
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    scheduleRestart(350L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    scheduleRestart(900L)
                }
                else -> {
                    Log.d(TAG, "Wake recognizer transient error code=$error")
                    scheduleRestart(1200L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListeningCycle = false
            if (!shouldRun) return

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val match = findWakeMatch(matches)
            if (match != null) {
                triggerWakeWord(match.trailingCommand)
            } else if (partialWakeMatched) {
                triggerWakeWord(trailingCommand = null)
            } else {
                scheduleRestart(350L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!shouldRun) return
            val partials = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val match = findWakeMatch(partials)
            if (match != null) {
                partialWakeMatched = true
                // If user said only the wake phrase without extra words, wait briefly for more words;
                // if they already spoke a full trailing command, let onResults capture the full phrase.
                if (match.trailingCommand.isNullOrBlank()) {
                    mainHandler.removeCallbacks(restartRunnable)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun triggerWakeWord(trailingCommand: String?) {
        stopInternal()
        onWakeWordDetected(trailingCommand?.takeIf { it.isNotBlank() })
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!shouldRun) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class WakeMatch(val trailingCommand: String?)

    private fun findWakeMatch(hypotheses: List<String>): WakeMatch? {
        for (raw in hypotheses) {
            val matchResult = WAKE_REGEX.find(raw.lowercase(Locale.US))
            if (matchResult != null) {
                val afterIndex = matchResult.range.last + 1
                val trailing = if (afterIndex < raw.length) {
                    raw.substring(afterIndex)
                        .trimStart(' ', ',', '.', ':', ';', '!', '?')
                        .trim()
                } else {
                    ""
                }
                return WakeMatch(trailingCommand = trailing.ifBlank { null })
            }
        }
        return null
    }

    companion object {
        // Matches "hey mero", "ok mero", "hi mero", standalone "mero", and close phonetic ASR spellings.
        private val WAKE_REGEX = Regex(
            """\b(?:(?:hey|hi|ok|okay|hello)\s+)?(?:mero|miro|merrow)\b"""
        )
    }
}
