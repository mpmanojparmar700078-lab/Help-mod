package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "live_screen_settings")

data class UserSettings(
    val customApiKey: String = "",
    val preferredLanguage: String = "hi-IN", // Hindi
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoSpeakResponses: Boolean = true,
    val frameCaptureIntervalMs: Long = 2500L,
    val explanationDetail: String = "concise", // "concise" or "detailed"
    val continuousVoiceEnabled: Boolean = false,
    val floatingIndicatorEnabled: Boolean = true
) {
    val effectiveApiKey: String
        get() = if (customApiKey.isNotBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
}

class SettingsRepository(private val context: Context) {
    private object PreferencesKeys {
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak_responses")
        val FRAME_INTERVAL = longPreferencesKey("frame_capture_interval_ms")
        val EXPLANATION_DETAIL = stringPreferencesKey("explanation_detail")
        val CONTINUOUS_VOICE = booleanPreferencesKey("continuous_voice_enabled")
        val FLOATING_INDICATOR = booleanPreferencesKey("floating_indicator_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            customApiKey = preferences[PreferencesKeys.CUSTOM_API_KEY] ?: "",
            preferredLanguage = preferences[PreferencesKeys.PREFERRED_LANGUAGE] ?: "hi-IN",
            speechRate = preferences[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
            speechPitch = preferences[PreferencesKeys.SPEECH_PITCH] ?: 1.0f,
            autoSpeakResponses = preferences[PreferencesKeys.AUTO_SPEAK] ?: true,
            frameCaptureIntervalMs = preferences[PreferencesKeys.FRAME_INTERVAL] ?: 2500L,
            explanationDetail = preferences[PreferencesKeys.EXPLANATION_DETAIL] ?: "concise",
            continuousVoiceEnabled = preferences[PreferencesKeys.CONTINUOUS_VOICE] ?: false,
            floatingIndicatorEnabled = preferences[PreferencesKeys.FLOATING_INDICATOR] ?: true
        )
    }

    suspend fun updateCustomApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_API_KEY] = key.trim() }
    }

    suspend fun updatePreferredLanguage(lang: String) {
        context.dataStore.edit { it[PreferencesKeys.PREFERRED_LANGUAGE] = lang }
    }

    suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { it[PreferencesKeys.SPEECH_RATE] = rate }
    }

    suspend fun updateSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[PreferencesKeys.SPEECH_PITCH] = pitch }
    }

    suspend fun updateAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_SPEAK] = enabled }
    }

    suspend fun updateFrameInterval(intervalMs: Long) {
        context.dataStore.edit { it[PreferencesKeys.FRAME_INTERVAL] = intervalMs }
    }

    suspend fun updateExplanationDetail(detail: String) {
        context.dataStore.edit { it[PreferencesKeys.EXPLANATION_DETAIL] = detail }
    }

    suspend fun updateContinuousVoice(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CONTINUOUS_VOICE] = enabled }
    }

    suspend fun updateFloatingIndicator(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FLOATING_INDICATOR] = enabled }
    }
}
