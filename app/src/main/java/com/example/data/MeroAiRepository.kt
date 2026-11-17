package com.example.data

import android.util.Log
import com.example.model.MeroPersonaStyle
import com.example.model.MeroVoiceProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "MeroAiRepository"
private const val MODEL_BRAIN = "gemini-3.8-flash"
private const val MODEL_TTS = "gemini-3.1-flash-tts-preview"
private const val AI_TIMEOUT_MS = 60_000L

data class MeroTurnResult(
    val userTranscript: String,
    val replyText: String,
    val spokenScript: String,
    val audioBytes: ByteArray?,
    val audioMimeType: String?,
    val ttsWarning: String? = null
)

class MeroAiRepository {

    private val meroResponseSchema = Schema.obj(
        properties = mapOf(
            "userTranscript" to Schema.string(
                description = "Exact verbatim transcript of what the user spoke or asked"
            ),
            "replyText" to Schema.string(
                description = "Mero's witty, helpful, cinematic Jarvis-style response for screen display"
            ),
            "spokenScript" to Schema.string(
                description = "Natural, expressive spoken version of replyText optimized for dramatic female TTS delivery"
            )
        )
    )

    private var activeStyle: MeroPersonaStyle = MeroPersonaStyle.FILMY_CINEMATIC
    private var chatSession: Chat? = null

    private fun buildBrainModel(style: MeroPersonaStyle) = Firebase.ai.generativeModel(
        modelName = MODEL_BRAIN,
        generationConfig = generationConfig {
            temperature = 0.85f
            responseMimeType = "application/json"
            responseSchema = meroResponseSchema
        },
        systemInstruction = content {
            text(
                """
                ${style.systemPromptAddendum}
                
                Core identity rules:
                - Your name is Mero. You are a bespoke mobile Jarvis-style AI voice companion.
                - Address the user warmly and intelligently—confident, loyal, and cinematic, never robotic or overly verbose.
                - Keep spoken responses tight (usually 1 to 3 crisp, evocative sentences unless the user asks for deep detail).
                - If the user speaks in Hindi, Hinglish, or another language, respond naturally in that same language or blend.
                - Always transcribe the user's audio accurately into userTranscript. If the user sent text, echo it cleanly in userTranscript.
                """.trimIndent()
            )
        }
    )

    private val brainModels by lazy {
        mutableMapOf<MeroPersonaStyle, com.google.firebase.ai.GenerativeModel>()
    }

    private fun getOrCreateChat(style: MeroPersonaStyle): Chat {
        if (chatSession == null || activeStyle != style) {
            activeStyle = style
            val model = brainModels.getOrPut(style) { buildBrainModel(style) }
            chatSession = model.startChat()
        }
        return chatSession!!
    }

    fun resetSession() {
        chatSession = null
    }

    suspend fun processSpeechTurn(
        wavBytes: ByteArray,
        voiceProfile: MeroVoiceProfile,
        personaStyle: MeroPersonaStyle,
        generateSpeechAudio: Boolean
    ): MeroTurnResult {
        val chat = getOrCreateChat(personaStyle)
        val inputContent = content("user") {
            inlineData(wavBytes, "audio/wav")
            text("Listen to the user's voice message above, transcribe it verbatim in userTranscript, and reply in character as Mero.")
        }
        return executeTurn(
            chat = chat,
            inputContent = inputContent,
            fallbackUserText = "Voice message",
            voiceProfile = voiceProfile,
            personaStyle = personaStyle,
            generateSpeechAudio = generateSpeechAudio
        )
    }

    suspend fun processTextTurn(
        userText: String,
        voiceProfile: MeroVoiceProfile,
        personaStyle: MeroPersonaStyle,
        generateSpeechAudio: Boolean
    ): MeroTurnResult {
        val chat = getOrCreateChat(personaStyle)
        val inputContent = content("user") {
            text(userText)
        }
        return executeTurn(
            chat = chat,
            inputContent = inputContent,
            fallbackUserText = userText,
            voiceProfile = voiceProfile,
            personaStyle = personaStyle,
            generateSpeechAudio = generateSpeechAudio
        )
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun synthesizeSpeech(
        spokenScript: String,
        voiceProfile: MeroVoiceProfile,
        personaStyle: MeroPersonaStyle
    ): Pair<ByteArray?, String?> {
        val ttsModel = Firebase.ai.generativeModel(
            modelName = MODEL_TTS,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice(voiceProfile.voiceName)
                )
            }
        )

        val directedPrompt = "${personaStyle.vocalDirection}\n\nSay this naturally:\n$spokenScript"
        val response = withTimeout(AI_TIMEOUT_MS) {
            ttsModel.generateContent(directedPrompt)
        }

        val audioPart = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.firstOrNull()

        return Pair(audioPart?.inlineData, audioPart?.mimeType)
    }

    private suspend fun executeTurn(
        chat: Chat,
        inputContent: com.google.firebase.ai.type.Content,
        fallbackUserText: String,
        voiceProfile: MeroVoiceProfile,
        personaStyle: MeroPersonaStyle,
        generateSpeechAudio: Boolean
    ): MeroTurnResult {
        val response = try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessage(inputContent)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Mero brain timed out", e)
            throw IllegalStateException("Mero timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Mero brain call failed", e)
            throw e
        }

        val rawJson = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Mero returned an empty response.")

        val json = JSONObject(rawJson)
        val userTranscript = json.optString("userTranscript", fallbackUserText).ifBlank { fallbackUserText }
        val replyText = json.optString("replyText", "").ifBlank { rawJson }
        val spokenScript = json.optString("spokenScript", replyText).ifBlank { replyText }

        if (!generateSpeechAudio) {
            return MeroTurnResult(
                userTranscript = userTranscript,
                replyText = replyText,
                spokenScript = spokenScript,
                audioBytes = null,
                audioMimeType = null
            )
        }

        return try {
            val (audioBytes, mimeType) = synthesizeSpeech(
                spokenScript = spokenScript,
                voiceProfile = voiceProfile,
                personaStyle = personaStyle
            )
            MeroTurnResult(
                userTranscript = userTranscript,
                replyText = replyText,
                spokenScript = spokenScript,
                audioBytes = audioBytes,
                audioMimeType = mimeType,
                ttsWarning = if (audioBytes == null) "Gemini TTS returned no audio; using device voice." else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS synthesis failed; falling back to device TTS", e)
            MeroTurnResult(
                userTranscript = userTranscript,
                replyText = replyText,
                spokenScript = spokenScript,
                audioBytes = null,
                audioMimeType = null,
                ttsWarning = e.localizedMessage ?: "Cloud voice unavailable; using device voice."
            )
        }
    }
}
