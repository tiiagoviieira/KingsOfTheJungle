package com.example.kingsofthejungle

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update


class GameViewModel : ViewModel() {
    private val privUiState = MutableStateFlow(AppUiState())
    val publicUiState : StateFlow<AppUiState> = privUiState.asStateFlow()

    fun updatePlayerName(name : String) {
        privUiState.update {
            it.copy(playerName = name)
        }
    }

    fun createLobby(lobbyName: String, maxPlayers : Int) {
        val admin = Player(
            id = "me",
            name = privUiState.value.playerName,
            isAdmin = true
        )
        val newLobby = Lobby(
            id = "lobby_${System.currentTimeMillis()}",
            name = lobbyName,
            maxPlayers = maxPlayers,
            playerList = listOf(admin)
        )
        privUiState.update {
            it.copy(
                currentLobby = newLobby
            )
        }
    }

    fun toggleReadyStatus() {
        privUiState.update { currentState ->
            val updatedLobby = currentState.currentLobby?.let { lobby ->
                val updatedPlayerList = lobby.playerList.map { player ->
                    if (player.id == "me") {
                        player.copy(isReady = !player.isReady)
                    } else {
                        player
                    }
                }
                lobby.copy(playerList = updatedPlayerList)
            }
            currentState.copy(currentLobby = updatedLobby)
        }
    }

    fun startGame() {
        privUiState.update {
            it.copy(
                gameState = true
            )
        }
    }
}
