package com.replog.app.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.remote.ApiService
import com.replog.app.data.remote.AuthTokenStore
import com.replog.app.data.remote.FriendDto
import com.replog.app.data.remote.FriendRequestRequest
import com.replog.app.data.remote.FriendRespondRequest
import com.replog.app.data.remote.LeaderboardEntryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<FriendDto> = emptyList(),
    val outgoing: List<FriendDto> = emptyList(),
    val leaderboard: List<LeaderboardEntryDto> = emptyList(),
    val message: String? = null,
    val addEmail: String = "",
    val busy: Boolean = false
)

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenStore: AuthTokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(SocialUiState(signedIn = tokenStore.isLoggedIn()))
    val state: StateFlow<SocialUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        if (!tokenStore.isLoggedIn()) {
            _state.update { it.copy(signedIn = false, loading = false) }
            return
        }
        _state.update { it.copy(loading = true, signedIn = true) }
        viewModelScope.launch {
            try {
                val token = "Bearer ${tokenStore.token}"
                val today = LocalDate.now()
                val date = today.toString()
                val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString()
                val friendsResp = api.friends(token, date, weekStart)
                val board = api.leaderboard(token, date, weekStart)
                _state.update {
                    it.copy(
                        loading = false,
                        friends = friendsResp.friends,
                        incoming = friendsResp.incoming,
                        outgoing = friendsResp.outgoing,
                        leaderboard = board.entries
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = friendly(e) ?: "Could not load friends"
                    )
                }
            }
        }
    }

    fun updateAddEmail(email: String) {
        _state.update { it.copy(addEmail = email, message = null) }
    }

    fun sendRequest() {
        val email = _state.value.addEmail.trim()
        if (email.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val resp = api.friendRequest(
                    "Bearer ${tokenStore.token}",
                    FriendRequestRequest(email)
                )
                _state.update {
                    it.copy(
                        busy = false,
                        addEmail = "",
                        message = when (resp.status) {
                            "accepted" -> "You are now friends with $email"
                            else -> "Request sent to $email"
                        }
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e) ?: "Could not send request") }
            }
        }
    }

    fun respond(fromUserId: Long, accept: Boolean) {
        viewModelScope.launch {
            runCatching {
                api.friendRespond(
                    "Bearer ${tokenStore.token}",
                    FriendRespondRequest(fromUserId, if (accept) "accept" else "decline")
                )
            }
            refresh()
        }
    }

    fun removeFriend(userId: Long) {
        viewModelScope.launch {
            runCatching {
                api.friendRemove(
                    "Bearer ${tokenStore.token}",
                    FriendRespondRequest(userId, "remove")
                )
            }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun friendly(e: Exception): String? {
        val msg = e.message ?: return null
        return when {
            msg.contains("Unable to resolve", true) || msg.contains("Failed to connect", true) ->
                "Cannot reach the REPLOG server. Check your connection and backend URL."
            msg.contains("401", true) -> "Session expired — sign in again from Settings."
            else -> msg.take(160)
        }
    }
}
