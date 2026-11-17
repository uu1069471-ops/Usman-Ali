package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.MeroPersonaStyle
import com.example.model.MeroPreferences
import com.example.model.MeroVoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.meroDataStore: DataStore<Preferences> by preferencesDataStore(name = "mero_prefs")

class MeroPreferencesRepository(private val context: Context) {

    private object Keys {
        val VOICE_PROFILE = stringPreferencesKey("voice_profile")
        val PERSONA_STYLE = stringPreferencesKey("persona_style")
        val HANDS_FREE_LOOP = booleanPreferencesKey("hands_free_loop")
        val AUTO_SILENCE_STOP = booleanPreferencesKey("auto_silence_stop")
        val SPOKEN_REPLIES = booleanPreferencesKey("spoken_replies")
    }

    val preferencesFlow: Flow<MeroPreferences> = context.meroDataStore.data.map { prefs ->
        val voice = prefs[Keys.VOICE_PROFILE]?.let { name ->
            MeroVoiceProfile.entries.firstOrNull { it.name == name }
        } ?: MeroVoiceProfile.AOEDE

        val style = prefs[Keys.PERSONA_STYLE]?.let { name ->
            MeroPersonaStyle.entries.firstOrNull { it.name == name }
        } ?: MeroPersonaStyle.FILMY_CINEMATIC

        MeroPreferences(
            voiceProfile = voice,
            personaStyle = style,
            handsFreeLoop = prefs[Keys.HANDS_FREE_LOOP] ?: true,
            autoSilenceStop = prefs[Keys.AUTO_SILENCE_STOP] ?: true,
            spokenReplies = prefs[Keys.SPOKEN_REPLIES] ?: true
        )
    }

    suspend fun setVoiceProfile(profile: MeroVoiceProfile) {
        context.meroDataStore.edit { it[Keys.VOICE_PROFILE] = profile.name }
    }

    suspend fun setPersonaStyle(style: MeroPersonaStyle) {
        context.meroDataStore.edit { it[Keys.PERSONA_STYLE] = style.name }
    }

    suspend fun setHandsFreeLoop(enabled: Boolean) {
        context.meroDataStore.edit { it[Keys.HANDS_FREE_LOOP] = enabled }
    }

    suspend fun setAutoSilenceStop(enabled: Boolean) {
        context.meroDataStore.edit { it[Keys.AUTO_SILENCE_STOP] = enabled }
    }

    suspend fun setSpokenReplies(enabled: Boolean) {
        context.meroDataStore.edit { it[Keys.SPOKEN_REPLIES] = enabled }
    }
}
