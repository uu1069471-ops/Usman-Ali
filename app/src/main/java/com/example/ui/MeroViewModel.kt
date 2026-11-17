package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MeroAudioEngine
import com.example.data.MeroAiRepository
import com.example.data.MeroPreferencesRepository
import com.example.data.MeroTurnResult
import com.example.model.MeroMessage
import com.example.model.MeroPersonaStyle
import com.example.model.MeroPreferences
import com.example.model.MeroState
import com.example.model.MeroVoiceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MeroViewModel"

data class MeroUiState(
    val meroState: MeroState = MeroState.STANDBY,
    val messages: List<MeroMessage> = emptyList(),
    val preferences: MeroPreferences = MeroPreferences(),
    val hasMicPermission: Boolean = false,
    val amplitude: Float = 0f,
    val spectrum: List<Float> = List(28) { 0.08f },
    val recordingDurationMs: Long = 0L,
    val errorMessage: String? = null,
    val statusNotice: String? = null,
    val playingMessageId: String? = null
)

class MeroViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = MeroAudioEngine(application.applicationContext)
    private val aiRepository = MeroAiRepository()
    private val prefsRepository = MeroPreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        MeroUiState(hasMicPermission = audioEngine.hasRecordPermission())
    )
    val uiState: StateFlow<MeroUiState> = _uiState.asStateFlow()

    private var activeTurnJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepository.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        viewModelScope.launch {
            audioEngine.amplitude.collect { amp ->
                _uiState.update { it.copy(amplitude = amp) }
            }
        }
        viewModelScope.launch {
            audioEngine.spectrum.collect { bars ->
                _uiState.update { it.copy(spectrum = bars) }
            }
        }
        viewModelScope.launch {
            audioEngine.recordingDurationMs.collect { ms ->
                _uiState.update { it.copy(recordingDurationMs = ms) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasMicPermission = granted,
                errorMessage = if (granted) null else it.errorMessage
            )
        }
    }

    fun refreshPermissionState() {
        _uiState.update { it.copy(hasMicPermission = audioEngine.hasRecordPermission()) }
    }

    fun onArcCoreTapped() {
        when (_uiState.value.meroState) {
            MeroState.STANDBY, MeroState.ERROR -> startListening()
            MeroState.LISTENING -> stopListeningAndProcess()
            MeroState.SPEAKING -> {
                // Barge-in: interrupt Mero and immediately listen
                activeTurnJob?.cancel()
                audioEngine.stopPlayback()
                _uiState.update { it.copy(playingMessageId = null) }
                startListening()
            }
            MeroState.THINKING -> {
                // Cancel in-flight request if user taps while thinking
                activeTurnJob?.cancel()
                _uiState.update {
                    it.copy(
                        meroState = MeroState.STANDBY,
                        statusNotice = "Cancelled."
                    )
                }
            }
        }
    }

    fun startListening() {
        if (!audioEngine.hasRecordPermission()) {
            _uiState.update {
                it.copy(
                    hasMicPermission = false,
                    meroState = MeroState.ERROR,
                    errorMessage = "Microphone permission is required for speech-to-speech."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                meroState = MeroState.LISTENING,
                errorMessage = null,
                statusNotice = null
            )
        }

        audioEngine.startRecording(
            autoSilenceStop = _uiState.value.preferences.autoSilenceStop,
            onAutoSilenceTriggered = {
                if (_uiState.value.meroState == MeroState.LISTENING) {
                    stopListeningAndProcess()
                }
            },
            onError = { msg ->
                _uiState.update {
                    it.copy(
                        meroState = MeroState.ERROR,
                        errorMessage = msg
                    )
                }
            }
        )
    }

    fun cancelListening() {
        audioEngine.cancelRecording()
        _uiState.update {
            it.copy(
                meroState = MeroState.STANDBY,
                statusNotice = null
            )
        }
    }

    fun stopListeningAndProcess() {
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    meroState = MeroState.THINKING,
                    errorMessage = null,
                    statusNotice = null
                )
            }

            val wavBytes = audioEngine.stopRecordingAndBuildWav()
            if (wavBytes == null) {
                _uiState.update {
                    it.copy(
                        meroState = MeroState.STANDBY,
                        statusNotice = "Didn't catch any audio. Tap the core and speak clearly."
                    )
                }
                return@launch
            }

            val prefs = _uiState.value.preferences
            val startMs = System.currentTimeMillis()

            try {
                val result = aiRepository.processSpeechTurn(
                    wavBytes = wavBytes,
                    voiceProfile = prefs.voiceProfile,
                    personaStyle = prefs.personaStyle,
                    generateSpeechAudio = prefs.spokenReplies
                )
                handleTurnSuccess(result, startMs, isVoiceTurn = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Speech turn failed", e)
                _uiState.update {
                    it.copy(
                        meroState = MeroState.ERROR,
                        errorMessage = e.localizedMessage ?: "Mero failed to process your voice."
                    )
                }
            }
        }
    }

    fun sendTextCommand(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        audioEngine.cancelRecording()
        audioEngine.stopPlayback()
        activeTurnJob?.cancel()

        activeTurnJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    meroState = MeroState.THINKING,
                    errorMessage = null,
                    statusNotice = null,
                    playingMessageId = null
                )
            }

            val prefs = _uiState.value.preferences
            val startMs = System.currentTimeMillis()

            try {
                val result = aiRepository.processTextTurn(
                    userText = text,
                    voiceProfile = prefs.voiceProfile,
                    personaStyle = prefs.personaStyle,
                    generateSpeechAudio = prefs.spokenReplies
                )
                handleTurnSuccess(result, startMs, isVoiceTurn = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Text turn failed", e)
                _uiState.update {
                    it.copy(
                        meroState = MeroState.ERROR,
                        errorMessage = e.localizedMessage ?: "Mero encountered an error."
                    )
                }
            }
        }
    }

    private suspend fun handleTurnSuccess(
        result: MeroTurnResult,
        startMs: Long,
        isVoiceTurn: Boolean
    ) {
        val latency = System.currentTimeMillis() - startMs
        val userMsg = MeroMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = result.userTranscript
        )
        val meroMsgId = UUID.randomUUID().toString()
        val meroMsg = MeroMessage(
            id = meroMsgId,
            isUser = false,
            text = result.replyText,
            spokenScript = result.spokenScript,
            audioBytes = result.audioBytes,
            audioMimeType = result.audioMimeType,
            latencyMs = latency
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + meroMsg,
                statusNotice = result.ttsWarning
            )
        }

        val prefs = _uiState.value.preferences
        if (prefs.spokenReplies) {
            playMessageAudio(meroMsg)
            // Hands-free continuous Jarvis loop after Mero finishes speaking
            if (isVoiceTurn && prefs.handsFreeLoop && audioEngine.hasRecordPermission()) {
                delay(250L)
                if (_uiState.value.meroState == MeroState.STANDBY) {
                    startListening()
                }
            }
        } else {
            _uiState.update { it.copy(meroState = MeroState.STANDBY) }
        }
    }

    fun replayMessage(message: MeroMessage) {
        if (message.isUser) return
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            if (_uiState.value.playingMessageId == message.id &&
                _uiState.value.meroState == MeroState.SPEAKING
            ) {
                audioEngine.stopPlayback()
                _uiState.update {
                    it.copy(meroState = MeroState.STANDBY, playingMessageId = null)
                }
                return@launch
            }

            // If audio wasn't cached yet, synthesize on demand
            val msgWithAudio = if (message.audioBytes == null) {
                _uiState.update { it.copy(meroState = MeroState.THINKING) }
                val prefs = _uiState.value.preferences
                try {
                    val (bytes, mime) = aiRepository.synthesizeSpeech(
                        spokenScript = message.spokenScript,
                        voiceProfile = prefs.voiceProfile,
                        personaStyle = prefs.personaStyle
                    )
                    val updated = message.copy(audioBytes = bytes, audioMimeType = mime)
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { if (it.id == message.id) updated else it })
                    }
                    updated
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "On-demand TTS synthesis failed", e)
                    message
                }
            } else {
                message
            }

            playMessageAudio(msgWithAudio)
        }
    }

    private suspend fun playMessageAudio(message: MeroMessage) {
        _uiState.update {
            it.copy(
                meroState = MeroState.SPEAKING,
                playingMessageId = message.id
            )
        }

        val playedCloudAudio = if (message.audioBytes != null) {
            audioEngine.playPcmOrWav(message.audioBytes, message.audioMimeType)
        } else {
            false
        }

        if (!playedCloudAudio) {
            audioEngine.speakWithFallbackTts(message.spokenScript)
        }

        _uiState.update {
            it.copy(
                meroState = MeroState.STANDBY,
                playingMessageId = null
            )
        }
    }

    fun setVoiceProfile(profile: MeroVoiceProfile) {
        viewModelScope.launch {
            prefsRepository.setVoiceProfile(profile)
        }
    }

    fun setPersonaStyle(style: MeroPersonaStyle) {
        viewModelScope.launch {
            prefsRepository.setPersonaStyle(style)
            aiRepository.resetSession()
        }
    }

    fun setHandsFreeLoop(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setHandsFreeLoop(enabled)
        }
    }

    fun setAutoSilenceStop(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoSilenceStop(enabled)
        }
    }

    fun setSpokenReplies(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setSpokenReplies(enabled)
        }
    }

    fun clearSession() {
        activeTurnJob?.cancel()
        audioEngine.cancelRecording()
        audioEngine.stopPlayback()
        aiRepository.resetSession()
        _uiState.update {
            it.copy(
                meroState = MeroState.STANDBY,
                messages = emptyList(),
                errorMessage = null,
                statusNotice = null,
                playingMessageId = null
            )
        }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                statusNotice = null,
                meroState = if (it.meroState == MeroState.ERROR) MeroState.STANDBY else it.meroState
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
