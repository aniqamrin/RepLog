package com.replog.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.remote.ApiService
import com.replog.app.data.remote.AuthResponse
import com.replog.app.data.remote.AuthTokenStore
import com.replog.app.data.remote.LoginRequest
import com.replog.app.data.remote.RegisterRequest
import com.replog.app.data.prefs.AppSettings
import com.replog.app.data.prefs.SettingsRepository
import com.replog.app.data.repository.DemoSeeder
import com.replog.app.data.repository.ProfileRepository
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.GoalType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val mode: String = "login",
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val loggedInEmail: String? = null
)

data class AiConfigUiState(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val chatModel: String = "gpt-4o-mini",
    val visionModel: String = "gpt-4o-mini",
    val saved: Boolean = false,
    val active: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val demoSeeder: DemoSeeder,
    private val api: ApiService,
    private val tokenStore: AuthTokenStore,
    private val aiClear: com.replog.app.data.repository.AiRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _authState = MutableStateFlow(AuthUiState(loggedInEmail = tokenStore.email))
    val authState: StateFlow<AuthUiState> = _authState

    private val _aiConfig = MutableStateFlow(AiConfigUiState())
    val aiConfig: StateFlow<AiConfigUiState> = _aiConfig

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _aiConfig.value = _aiConfig.value.copy(
                    baseUrl = if (_aiConfig.value.saved) _aiConfig.value.baseUrl else s.aiBaseUrl,
                    chatModel = if (_aiConfig.value.saved) _aiConfig.value.chatModel else s.aiChatModel,
                    visionModel = if (_aiConfig.value.saved) _aiConfig.value.visionModel else s.aiVisionModel,
                    active = s.aiConfigured
                )
            }
        }
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            _aiConfig.value = _aiConfig.value.copy(baseUrl = s.aiBaseUrl, chatModel = s.aiChatModel, visionModel = s.aiVisionModel)
        }
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    fun setThemeDark(dark: Boolean) {
        viewModelScope.launch { settingsRepository.setThemeDark(dark) }
    }

    fun setReminder(key: String, value: Boolean) {
        viewModelScope.launch { settingsRepository.setReminder(key, value) }
    }

    suspend fun loadGoals(): Goals = profileRepository.goals()

    suspend fun currentName(): String =
        profileRepository.profile()?.name ?: "Athlete"

    fun saveGoals(goals: Goals) {
        viewModelScope.launch {
            runCatching { profileRepository.updateGoals(goals) }
        }
    }

    fun saveName(name: String) {
        viewModelScope.launch { profileRepository.updateName(name.trim()) }
    }

    fun regenerateDemoData() {
        viewModelScope.launch {
            runCatching { demoSeeder.reseed() }
            _statusMessage.value = "Demo data regenerated"
        }
    }

    fun clearAiChat() {
        viewModelScope.launch { aiClear.clearAll() }
    }

    fun updateAiConfig(transform: (AiConfigUiState) -> AiConfigUiState) {
        _aiConfig.value = transform(_aiConfig.value).copy(saved = false)
    }

    fun saveAiConfig() {
        val c = _aiConfig.value
        viewModelScope.launch {
            settingsRepository.setAiConfig(c.apiKey, c.baseUrl, c.chatModel, c.visionModel)
            val active = settingsRepository.settings.first().aiConfigured
            _aiConfig.value = _aiConfig.value.copy(
                saved = true,
                apiKey = "",
                active = active,
                baseUrl = settingsRepository.settings.first().aiBaseUrl,
                chatModel = settingsRepository.settings.first().aiChatModel,
                visionModel = settingsRepository.settings.first().aiVisionModel
            )
        }
    }

    fun updateAuth(transform: (AuthUiState) -> AuthUiState) {
        _authState.value = transform(_authState.value)
    }

    fun submitAuth() {
        val s = _authState.value
        if (s.busy || s.email.isBlank() || s.password.length < 6) return
        _authState.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val response: AuthResponse = if (s.mode == "register") {
                    api.register(RegisterRequest(s.email.trim(), s.password, s.name.ifBlank { "Athlete" }))
                } else {
                    api.login(LoginRequest(s.email.trim(), s.password))
                }
                if (response.token.isBlank()) throw IllegalStateException("Server returned no token")
                tokenStore.token = response.token
                tokenStore.email = s.email.trim()
                settingsRepository.setBackendConfigured(true)
                profileRepository.updateName(s.name.ifBlank { "Athlete" })
                profileRepository.updateEmail(s.email.trim())
                _authState.value = _authState.value.copy(busy = false, loggedInEmail = s.email.trim())
                _statusMessage.value = "Signed in — sync and AI enabled"
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(busy = false, error = friendly(e))
            }
        }
    }

    fun logout() {
        tokenStore.clear()
        viewModelScope.launch { settingsRepository.setBackendConfigured(false) }
        _authState.value = AuthUiState()
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("Failed to connect", true) == true ||
            e.message?.contains("Unable to resolve", true) == true ->
            "Could not reach the REPLOG server. Check the backend URL is running."
        e.message?.contains("401", true) == true -> "Wrong email or password."
        else -> e.message ?: "Sign-in failed"
    }
}
