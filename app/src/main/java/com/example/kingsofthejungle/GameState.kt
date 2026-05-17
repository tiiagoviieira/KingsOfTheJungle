package com.example.kingsofthejungle

data class Player(
    val id : String,
    val name : String,
    val isReady : Boolean = false,
    val isAdmin : Boolean = false,
    val x: Float = 0.5f, // Normalized position (0.0 to 1.0)
    val y: Float = 0.5f,
    val isAlive: Boolean = true,
    val team: String = "Red"
)

data class Lobby(
    val id : String,
    val name : String,
    val maxPlayers : Int,
    val playerList : List<Player> = emptyList(),
    val requestList : List<Player> = emptyList()
)

data class AppUiState(
    val playerName : String = "",
    val listLobbys : List<Lobby> = emptyList(),
    val currentLobby : Lobby? = null,
    val gameState : Boolean = false,
    val compassHeading: Float = 0f,
    val heartRate: Int? = null
)
