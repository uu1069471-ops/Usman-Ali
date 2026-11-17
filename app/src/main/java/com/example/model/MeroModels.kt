package com.example.model

enum class MeroState {
    STANDBY,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class MeroVoiceProfile(
    val voiceName: String,
    val displayName: String,
    val subtitle: String,
    val badge: String
) {
    AOEDE(
        voiceName = "Aoede",
        displayName = "Aoede • Filmy Muse",
        subtitle = "Warm, velvety & cinematic silver-screen cadence",
        badge = "FILMY"
    ),
    ZEPHYR(
        voiceName = "Zephyr",
        displayName = "Zephyr • Cyber Spark",
        subtitle = "Bright, crisp & high-tempo holographic co-pilot",
        badge = "CRISP"
    ),
    KORE(
        voiceName = "Kore",
        displayName = "Kore • Sovereign",
        subtitle = "Poised, commanding & regal Jarvis authority",
        badge = "REGAL"
    ),
    LEDA(
        voiceName = "Leda",
        displayName = "Leda • Velvet Star",
        subtitle = "Youthful, charismatic & expressive storytelling tone",
        badge = "LIVELY"
    )
}

enum class MeroPersonaStyle(
    val title: String,
    val description: String,
    val systemPromptAddendum: String,
    val vocalDirection: String
) {
    FILMY_CINEMATIC(
        title = "Filmy Heroine",
        description = "Charismatic, warm, poetic & dramatic silver-screen Jarvis flair",
        systemPromptAddendum = "You are Mero, a charismatic, cinematic female Jarvis-style AI assistant with warm, witty, dramatic 'filmy' elegance. Keep replies concise, vivid, and naturally conversational for spoken audio.",
        vocalDirection = "Speak as a charismatic, warm, expressive female AI companion in a cinematic film—poised, velvety, slightly dramatic, with natural pauses and charm."
    ),
    TACTICAL_JARVIS(
        title = "Tactical HUD",
        description = "Precision engineering clarity with dry futuristic wit",
        systemPromptAddendum = "You are Mero, a sharp, loyal female Jarvis-style tactical AI co-pilot. Deliver crystal-clear answers with refined dry wit and zero fluff.",
        vocalDirection = "Speak in a sleek, confident, poised female AI tone—articulate, intelligent, and subtly warm."
    ),
    POETIC_NOIR(
        title = "Velvet Noir",
        description = "Atmospheric late-night co-pilot with lyrical warmth",
        systemPromptAddendum = "You are Mero, an atmospheric, deeply intelligent female AI confidante with lyrical warmth and calm confidence.",
        vocalDirection = "Speak softly and expressively like a late-night cinema narrator—smooth, intimate, and resonant."
    ),
    PLAYFUL_SPARK(
        title = "Breezy Wit",
        description = "Upbeat, quick-witted, energetic conversational partner",
        systemPromptAddendum = "You are Mero, a quick-witted, playful female Jarvis-style AI sidekick who keeps every exchange lively, bright, and genuinely helpful.",
        vocalDirection = "Speak with bright, energetic, smiling female warmth and lively conversational rhythm."
    )
}

data class MeroPreferences(
    val voiceProfile: MeroVoiceProfile = MeroVoiceProfile.AOEDE,
    val personaStyle: MeroPersonaStyle = MeroPersonaStyle.FILMY_CINEMATIC,
    val handsFreeLoop: Boolean = true,
    val autoSilenceStop: Boolean = true,
    val spokenReplies: Boolean = true,
    val wakeWordEnabled: Boolean = true
)

data class MeroMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val spokenScript: String = text,
    val audioBytes: ByteArray? = null,
    val audioMimeType: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val latencyMs: Long? = null
)
