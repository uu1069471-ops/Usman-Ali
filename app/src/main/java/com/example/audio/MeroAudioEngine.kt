package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MeroAudioEngine"
private const val RECORD_SAMPLE_RATE = 16000
private const val BAR_COUNT = 28
private const val MAX_RECORDING_MS = 45_000L
private const val VAD_SPEECH_THRESHOLD = 0.055f
private const val VAD_SILENCE_THRESHOLD = 0.032f
private const val VAD_SILENCE_HOLD_MS = 1250L

class MeroAudioEngine(private val context: Context) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _spectrum = MutableStateFlow(List(BAR_COUNT) { 0.08f })
    val spectrum: StateFlow<List<Float>> = _spectrum.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var recordedStream: ByteArrayOutputStream? = null

    @Volatile
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlayingFlag = false

    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false

    init {
        initFallbackTts()
    }

    private fun initFallbackTts() {
        try {
            fallbackTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = fallbackTts ?: return@TextToSpeech
                    tts.language = Locale.US
                    val femaleVoice = tts.voices?.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.US)
                        voice.locale.language == "en" &&
                            (name.contains("female") || name.contains("sfg") || name.contains("tpf") || name.contains("iol"))
                    }
                    if (femaleVoice != null) {
                        tts.voice = femaleVoice
                    }
                    tts.setPitch(1.06f)
                    tts.setSpeechRate(0.96f)
                    fallbackTtsReady = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback TTS initialization skipped", e)
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        autoSilenceStop: Boolean,
        onAutoSilenceTriggered: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasRecordPermission()) {
            onError("Microphone permission is required.")
            return
        }
        stopPlayback()
        cancelRecording()

        val minBuf = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(RECORD_SAMPLE_RATE / 5)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RECORD_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            onError("Unable to initialize microphone.")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Microphone hardware is unavailable.")
            return
        }

        val pcmStream = ByteArrayOutputStream()
        recordedStream = pcmStream
        audioRecord = recorder
        _recordingDurationMs.value = 0L

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord startRecording failed", e)
            recorder.release()
            audioRecord = null
            onError("Could not start microphone capture.")
            return
        }

        recordJob = engineScope.launch {
            val shortBuffer = ShortArray(1024)
            val byteBuffer = ByteArray(shortBuffer.size * 2)
            val startTime = System.currentTimeMillis()
            var hasHeardSpeech = false
            var silenceStartMs = 0L

            try {
                while (isActive && audioRecord != null) {
                    val readCount = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val sample = shortBuffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            val norm = sample / 32768.0
                            sumSquares += norm * norm
                        }
                        pcmStream.write(byteBuffer, 0, readCount * 2)

                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val scaledAmp = (rms * 3.8f).coerceIn(0f, 1f)
                        _amplitude.value = scaledAmp
                        _spectrum.value = buildSpectrum(shortBuffer, readCount, scaledAmp)

                        val elapsed = System.currentTimeMillis() - startTime
                        _recordingDurationMs.value = elapsed

                        if (scaledAmp >= VAD_SPEECH_THRESHOLD) {
                            hasHeardSpeech = true
                            silenceStartMs = 0L
                        } else if (autoSilenceStop && hasHeardSpeech && scaledAmp < VAD_SILENCE_THRESHOLD) {
                            if (silenceStartMs == 0L) {
                                silenceStartMs = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - silenceStartMs >= VAD_SILENCE_HOLD_MS) {
                                withContext(Dispatchers.Main) {
                                    onAutoSilenceTriggered()
                                }
                                break
                            }
                        }

                        if (elapsed >= MAX_RECORDING_MS) {
                            withContext(Dispatchers.Main) {
                                onAutoSilenceTriggered()
                            }
                            break
                        }
                    } else if (readCount < 0) {
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio capture", e)
            }
        }
    }

    suspend fun stopRecordingAndBuildWav(): ByteArray? = withContext(Dispatchers.IO) {
        recordJob?.cancel()
        recordJob = null

        val recorder = audioRecord
        audioRecord = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        _amplitude.value = 0f
        _spectrum.value = List(BAR_COUNT) { 0.08f }
        _recordingDurationMs.value = 0L

        val pcmBytes = recordedStream?.toByteArray()
        recordedStream = null
        if (pcmBytes == null || pcmBytes.size < 3200) {
            return@withContext null
        }
        wrapPcmAsWav(pcmBytes, RECORD_SAMPLE_RATE, 1, 16)
    }

    fun cancelRecording() {
        recordJob?.cancel()
        recordJob = null
        val recorder = audioRecord
        audioRecord = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recordedStream = null
        _amplitude.value = 0f
        _spectrum.value = List(BAR_COUNT) { 0.08f }
        _recordingDurationMs.value = 0L
    }

    suspend fun playPcmOrWav(bytes: ByteArray, mimeType: String?): Boolean = withContext(Dispatchers.IO) {
        stopPlayback()
        val parsed = parseAudioPayload(bytes, mimeType)
        val pcmData = parsed.pcmBytes
        if (pcmData.isEmpty()) return@withContext false

        val channelConfig = if (parsed.channels >= 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuffer = AudioTrack.getMinBufferSize(parsed.sampleRate, channelConfig, encoding)
            .coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(parsed.sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return@withContext false
        }

        audioTrack = track
        isPlayingFlag = true

        try {
            track.play()
            val chunkSize = 2048
            var offset = 0
            while (isActive && isPlayingFlag && offset < pcmData.size) {
                val toWrite = min(chunkSize, pcmData.size - offset)
                val written = track.write(pcmData, offset, toWrite)
                if (written <= 0) break

                updatePlaybackMeter(pcmData, offset, written)
                offset += written
            }
            // Allow buffered tail to finish playing cleanly if not interrupted
            if (isPlayingFlag && isActive) {
                val tailMs = ((minBuffer.toLong() * 1000L) / (parsed.sampleRate * 2L * parsed.channels))
                    .coerceIn(80L, 400L)
                delay(tailMs)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback failed", e)
            false
        } finally {
            isPlayingFlag = false
            _amplitude.value = 0f
            _spectrum.value = List(BAR_COUNT) { 0.08f }
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
            if (audioTrack === track) {
                audioTrack = null
            }
        }
    }

    suspend fun speakWithFallbackTts(text: String): Boolean = withContext(Dispatchers.Main) {
        val tts = fallbackTts
        if (!fallbackTtsReady || tts == null || text.isBlank()) return@withContext false

        stopPlayback()
        isPlayingFlag = true
        val utteranceId = UUID.randomUUID().toString()

        // Animate reactive core gently while fallback TTS speaks
        val animJob = engineScope.launch {
            var phase = 0f
            while (isActive && isPlayingFlag) {
                phase += 0.35f
                val amp = (0.32f + 0.25f * abs(sin(phase))).coerceIn(0f, 1f)
                _amplitude.value = amp
                _spectrum.value = List(BAR_COUNT) { idx ->
                    (0.18f + amp * 0.65f * abs(sin(phase + idx * 0.4f))).coerceIn(0.08f, 1f)
                }
                delay(45L)
            }
        }

        try {
            suspendCancellableCoroutine<Boolean> { cont ->
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) cont.resume(false)
                    }
                })
                cont.invokeOnCancellation {
                    tts.stop()
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        } finally {
            animJob.cancel()
            isPlayingFlag = false
            _amplitude.value = 0f
            _spectrum.value = List(BAR_COUNT) { 0.08f }
        }
    }

    fun stopPlayback() {
        isPlayingFlag = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            fallbackTts?.stop()
        } catch (_: Exception) {
        }
        _amplitude.value = 0f
        _spectrum.value = List(BAR_COUNT) { 0.08f }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
        try {
            fallbackTts?.shutdown()
        } catch (_: Exception) {
        }
        fallbackTts = null
    }

    private fun updatePlaybackMeter(pcm: ByteArray, offset: Int, length: Int) {
        val sampleCount = length / 2
        if (sampleCount <= 0) return
        var sumSq = 0.0
        val step = (sampleCount / BAR_COUNT).coerceAtLeast(1)
        val bars = FloatArray(BAR_COUNT)
        var barIdx = 0
        var i = 0
        while (i < sampleCount) {
            val byteIdx = offset + i * 2
            if (byteIdx + 1 >= pcm.size) break
            val low = pcm[byteIdx].toInt() and 0xFF
            val high = pcm[byteIdx + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            val norm = abs(sample / 32768.0).toFloat()
            sumSq += norm * norm
            if (barIdx < BAR_COUNT && i % step == 0) {
                bars[barIdx] = (0.12f + norm * 2.8f).coerceIn(0.08f, 1f)
                barIdx++
            }
            i++
        }
        val rms = sqrt(sumSq / sampleCount).toFloat()
        _amplitude.value = (rms * 3.4f).coerceIn(0f, 1f)
        _spectrum.value = bars.toList()
    }

    private fun buildSpectrum(samples: ShortArray, count: Int, amp: Float): List<Float> {
        val step = (count / BAR_COUNT).coerceAtLeast(1)
        return List(BAR_COUNT) { bar ->
            val idx = (bar * step).coerceAtMost(count - 1)
            val norm = abs(samples[idx] / 32768f)
            (0.10f + norm * 2.5f + amp * 0.25f).coerceIn(0.08f, 1f)
        }
    }

    private data class ParsedAudio(
        val pcmBytes: ByteArray,
        val sampleRate: Int,
        val channels: Int
    )

    private fun parseAudioPayload(bytes: ByteArray, mimeType: String?): ParsedAudio {
        // Detect RIFF/WAVE container
        if (bytes.size > 44 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var channels = 1
            var sampleRate = 24000
            var pos = 12
            while (pos + 8 <= bytes.size) {
                val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
                val chunkSize = buf.getInt(pos + 4).coerceAtLeast(0)
                if (chunkId == "fmt " && pos + 24 <= bytes.size) {
                    channels = buf.getShort(pos + 10).toInt().coerceIn(1, 2)
                    sampleRate = buf.getInt(pos + 12).coerceIn(8000, 48000)
                } else if (chunkId == "data") {
                    val dataStart = pos + 8
                    val dataEnd = min(bytes.size, dataStart + chunkSize)
                    return ParsedAudio(
                        pcmBytes = bytes.copyOfRange(dataStart, dataEnd),
                        sampleRate = sampleRate,
                        channels = channels
                    )
                }
                pos += 8 + chunkSize + (chunkSize and 1)
            }
        }

        // Parse raw PCM / L16 sample rate from MIME type if provided (e.g. "audio/L16;rate=24000")
        val rateMatch = mimeType?.let { Regex("rate=(\\d+)").find(it) }
        val sampleRate = rateMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(8000, 48000) ?: 24000
        return ParsedAudio(pcmBytes = bytes, sampleRate = sampleRate, channels = 1)
    }

    private fun wrapPcmAsWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk1 size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        return header + pcmData
    }
}
