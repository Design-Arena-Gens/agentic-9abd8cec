package com.max.pandaai.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "panda_ai_settings")

data class AssistantSettings(
    val assistantName: String = "Panda",
    val assistantVoice: String = "Default",
    val darkModeEnabled: Boolean = false
)

class SettingsManager(private val context: Context) {

    private val nameKey = stringPreferencesKey("assistant_name")
    private val voiceKey = stringPreferencesKey("assistant_voice")
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val settingsFlow: Flow<AssistantSettings> = context.dataStore.data.map { prefs ->
        AssistantSettings(
            assistantName = prefs[nameKey] ?: AssistantSettings().assistantName,
            assistantVoice = prefs[voiceKey] ?: AssistantSettings().assistantVoice,
            darkModeEnabled = prefs[darkModeKey] ?: AssistantSettings().darkModeEnabled
        )
    }

    suspend fun saveSettings(newSettings: AssistantSettings) {
        context.dataStore.edit { prefs ->
            prefs[nameKey] = newSettings.assistantName
            prefs[voiceKey] = newSettings.assistantVoice
            prefs[darkModeKey] = newSettings.darkModeEnabled
        }
    }
}
