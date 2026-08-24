package com.replog.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "replog_settings")

data class AppSettings(
    val themeDark: Boolean = true,
    val seeded: Boolean = false,
    val remindFood: Boolean = true,
    val remindWorkout: Boolean = true,
    val remindWater: Boolean = false,
    val remindCheckIn: Boolean = true,
    val backendConfigured: Boolean = false,
    val aiApiKey: String = "",
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiChatModel: String = "gpt-4o-mini",
    val aiVisionModel: String = "gpt-4o-mini"
) {
    val aiConfigured: Boolean get() = aiApiKey.isNotBlank()
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME_DARK = booleanPreferencesKey("theme_dark")
        val SEEDED = booleanPreferencesKey("demo_seeded")
        val REMIND_FOOD = booleanPreferencesKey("remind_food")
        val REMIND_WORKOUT = booleanPreferencesKey("remind_workout")
        val REMIND_WATER = booleanPreferencesKey("remind_water")
        val REMIND_CHECKIN = booleanPreferencesKey("remind_checkin")
        val BACKEND_CONFIGURED = booleanPreferencesKey("backend_configured")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_CHAT_MODEL = stringPreferencesKey("ai_chat_model")
        val AI_VISION_MODEL = stringPreferencesKey("ai_vision_model")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeDark = p[Keys.THEME_DARK] ?: true,
            seeded = p[Keys.SEEDED] ?: false,
            remindFood = p[Keys.REMIND_FOOD] ?: true,
            remindWorkout = p[Keys.REMIND_WORKOUT] ?: true,
            remindWater = p[Keys.REMIND_WATER] ?: false,
            remindCheckIn = p[Keys.REMIND_CHECKIN] ?: true,
            backendConfigured = p[Keys.BACKEND_CONFIGURED] ?: false,
            aiApiKey = p[Keys.AI_API_KEY] ?: "",
            aiBaseUrl = p[Keys.AI_BASE_URL]?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1",
            aiChatModel = p[Keys.AI_CHAT_MODEL]?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini",
            aiVisionModel = p[Keys.AI_VISION_MODEL]?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini"
        )
    }

    suspend fun setAiConfig(apiKey: String, baseUrl: String, chatModel: String, visionModel: String) {
        context.dataStore.edit {
            it[Keys.AI_API_KEY] = apiKey.trim()
            it[Keys.AI_BASE_URL] = baseUrl.trim()
            it[Keys.AI_CHAT_MODEL] = chatModel.trim()
            it[Keys.AI_VISION_MODEL] = visionModel.trim()
        }
    }

    suspend fun clearAiConfig() {
        context.dataStore.edit {
            it[Keys.AI_API_KEY] = ""
        }
    }

    suspend fun setThemeDark(dark: Boolean) =
        context.dataStore.edit { it[Keys.THEME_DARK] = dark }

    suspend fun setSeeded() =
        context.dataStore.edit { it[Keys.SEEDED] = true }

    suspend fun setBackendConfigured(value: Boolean) =
        context.dataStore.edit { it[Keys.BACKEND_CONFIGURED] = value }

    suspend fun setReminder(key: String, value: Boolean) {
        context.dataStore.edit {
            it[booleanPreferencesKey(key)] = value
        }
    }
}
