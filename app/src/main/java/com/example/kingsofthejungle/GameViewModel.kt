package com.example.kingsofthejungle

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class GameViewModel(
    private val nearbyRepository: NearbyConnectionsRepository,
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository
) : ViewModel() {
    private val privUiState = MutableStateFlow(AppUiState())
    private val localPlayerId = java.util.UUID.randomUUID().toString()

    val publicUiState : StateFlow<AppUiState> = privUiState.asStateFlow()

    private var mapCenterLocation: Location? = null

    init {

        privUiState.update { it.copy(localPlayerId = localPlayerId) }
        // Collect local location updates
        viewModelScope.launch {
            locationRepository.locationFlow.collect { location ->
                location?.let { currentLocation ->
                    Log.d("GameViewModel", "User Location: Lat ${currentLocation.latitude}, Lng ${currentLocation.longitude}")
                    
                    if (privUiState.value.gameState) {
                        // Set center if not yet initialized
                        if (mapCenterLocation == null) {
                            mapCenterLocation = currentLocation
                        }

                        mapCenterLocation?.let { center ->
                            val (newX, newY) = MapMathHelper.convertToCanvasCoordinates(center, currentLocation)
                            
                            var updatedMe: Player? = null
                            
                            privUiState.update { currentState ->
                                val updatedLobby = currentState.currentLobby?.let { lobby ->
                                    val updatedPlayerList = lobby.playerList.map { player ->
                                        if (player.id == localPlayerId) {
                                            val up = player.copy(x = newX, y = newY)
                                            updatedMe = up
                                            up
                                        } else {
                                            player
                                        }
                                    }
                                    lobby.copy(playerList = updatedPlayerList)
                                }
                                currentState.copy(currentLobby = updatedLobby)
                            }
                            
                            // Broadcast local movement to others
                            updatedMe?.let {
                                nearbyRepository.broadcastPlayer(it)
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            nearbyRepository.incomingPlayers.collect { incomingPlayer ->
                var finalLobby: Lobby? = null
                privUiState.update { currentState ->
                    val updatedLobby = currentState.currentLobby?.let { lobby ->
                        val exists = lobby.playerList.any { it.id == incomingPlayer.id }
                        val newList = if (exists) {
                            lobby.playerList.map { if (it.id == incomingPlayer.id) incomingPlayer else it }
                        } else {
                            lobby.playerList + incomingPlayer
                        }
                        val upLobby = lobby.copy(playerList = newList)
                        finalLobby = upLobby
                        upLobby
                    }
                    currentState.copy(currentLobby = updatedLobby)
                }

                val isAdmin = privUiState.value.currentLobby
                    ?.playerList?.find { it.id == localPlayerId }?.isAdmin == true

                if (isAdmin) {
                    if (!privUiState.value.gameState) {
                        // Lobby phase: broadcast the full updated lobby to all clients
                        finalLobby?.let { nearbyRepository.broadcastLobby(it) }
                    } else {
                        // Game phase: relay the raw player update to all other endpoints.
                        // Without this, in a 3+ player star topology the admin is the only
                        // hub, so Client A's forfeit would never reach Client B otherwise.
                        nearbyRepository.broadcastPlayer(incomingPlayer)
                    }
                }

                checkAndHandleGameOver()
            }
        }

        // Collect incoming Lobby objects (for Clients joining)
        viewModelScope.launch {
            nearbyRepository.incomingLobby.collect { lobby ->
                privUiState.update { it.copy(currentLobby = lobby) }
            }
        }

        // Collect discovered endpoints for the server browser
        viewModelScope.launch {
            nearbyRepository.discoveredEndpoints.collect { endpoints ->
                privUiState.update { it.copy(availableLobbies = endpoints) }
            }
        }

        // Collect compass heading updates
        viewModelScope.launch {
            sensorRepository.headingFlow.collect { heading ->
                privUiState.update { it.copy(compassHeading = heading) }
            }
        }

        // Collect heart rate updates
        viewModelScope.launch {
            sensorRepository.heartRateFlow.collect { heartRate ->
                privUiState.update { it.copy(heartRate = heartRate) }
            }
        }

        // Collect incoming game commands (e.g., START_GAME)
        viewModelScope.launch {
            nearbyRepository.gameCommands.collect { command ->
                if (command == "START_GAME") {
                    privUiState.update { it.copy(gameState = true) }
                    startLocationTracking()
                    sensorRepository.startListening()
                }
            }
        }

        // Collect new connection events to trigger the join handshake
        viewModelScope.launch {
            nearbyRepository.newConnections.collect {
                val isAdmin = privUiState.value.currentLobby
                    ?.playerList?.find { it.id == localPlayerId }?.isAdmin == true  // <-- was "me"
                if (!isAdmin) {
                    // Client announces itself with its real unique ID
                    val me = Player(id = localPlayerId, name = privUiState.value.playerName)
                    nearbyRepository.broadcastPlayer(me)
                } else {
                    // Admin sends current lobby to the new connection
                    privUiState.value.currentLobby?.let { nearbyRepository.broadcastLobby(it) }
                }
            }
        }

        viewModelScope.launch {
            nearbyRepository.discoveryError.collect { errorMessage ->
                privUiState.update { it.copy(discoveryError = errorMessage) }
            }
        }

        viewModelScope.launch {
            nearbyRepository.playerLeaves.collect { leavingPlayerId ->
                var updatedLobbyRef: Lobby? = null

                privUiState.update { currentState ->
                    val updatedLobby = currentState.currentLobby?.let { lobby ->
                        // Remove the disconnected player
                        val newList = lobby.playerList.filter { it.id != leavingPlayerId }
                        val upLobby = lobby.copy(playerList = newList)
                        updatedLobbyRef = upLobby
                        upLobby
                    }
                    currentState.copy(currentLobby = updatedLobby)
                }

                // If I am the admin, broadcast the newly updated lobby to the remaining clients
                val isAdmin = privUiState.value.currentLobby
                    ?.playerList?.find { it.id == localPlayerId }?.isAdmin == true

                if (isAdmin && !privUiState.value.gameState) {
                    updatedLobbyRef?.let { nearbyRepository.broadcastLobby(it) }
                }

                // If they drop during a live game, check if this triggers a game over
                checkAndHandleGameOver()
            }
        }

    }

    // Called after any player state update. Ends the game when everyone is dead.
    private fun checkAndHandleGameOver() {
        val lobby = privUiState.value.currentLobby ?: return
        if (!privUiState.value.gameState) return  // Already left the game

        val alivePlayers = lobby.playerList.filter { it.isAlive }
        val myPlayer = lobby.playerList.find { it.id == localPlayerId }

        when {
            // Last player standing — winner!
            alivePlayers.size == 1 && myPlayer?.isAlive == true -> {
                stopLocationTracking()
                sensorRepository.stopListening()
                val resetLobby = lobby.copy(
                    playerList = lobby.playerList.map { it.copy(isAlive = true, isReady = false) }
                )
                privUiState.update {
                    it.copy(
                        gameState = false,
                        currentLobby = resetLobby,
                        gameMessage = "🏆 You won! Congratulations!"
                    )
                }
                // Broadcast reset so forfeited players get the clean lobby too
                nearbyRepository.broadcastLobby(resetLobby)
            }

            // Everyone dead at the same time (edge case — simultaneous forfeit)
            alivePlayers.isEmpty() -> {
                stopLocationTracking()
                sensorRepository.stopListening()
                val resetLobby = lobby.copy(
                    playerList = lobby.playerList.map { it.copy(isAlive = true, isReady = false) }
                )
                privUiState.update { it.copy(gameState = false, currentLobby = resetLobby) }
                nearbyRepository.broadcastLobby(resetLobby)
            }
        }
    }

    fun updatePlayerName(name : String) {
        privUiState.update {
            it.copy(playerName = name)
        }
    }

    fun clearGameMessage() {
        privUiState.update { it.copy(gameMessage = null) }
    }

    fun createLobby(lobbyName: String, maxPlayers: Int) {
        val admin = Player(
            id = localPlayerId,
            name = privUiState.value.playerName,
            isAdmin = true
        )
        val newLobby = Lobby(
            id = "lobby_${System.currentTimeMillis()}",
            name = lobbyName,
            maxPlayers = maxPlayers,
            playerList = listOf(admin)
        )
        privUiState.update { it.copy(currentLobby = newLobby) }
        nearbyRepository.startAdvertising(privUiState.value.playerName)
    }

    fun startDiscoveringLobbies() {
        nearbyRepository.stopDiscovery()
        nearbyRepository.startDiscovery()
    }

    fun joinDiscoveredLobby(endpointId: String) {
        nearbyRepository.stopDiscovery()
        nearbyRepository.requestConnectionTo(endpointId, privUiState.value.playerName)
    }

    fun leaveLobby() {
        nearbyRepository.stopAll()
        privUiState.update { it.copy(currentLobby = null, gameState = false) }
        stopLocationTracking()
        sensorRepository.stopListening()
    }

    fun startLocationTracking() {
        locationRepository.startTracking()
    }

    fun stopLocationTracking() {
        locationRepository.stopTracking()
    }

    fun toggleReadyStatus() {
        var updatedMe: Player? = null
        privUiState.update { currentState ->
            val updatedLobby = currentState.currentLobby?.let { lobby ->
                val updatedPlayerList = lobby.playerList.map { player ->
                    if (player.id == localPlayerId) {
                        val up = player.copy(isReady = !player.isReady)
                        updatedMe = up
                        up
                    } else player
                }
                lobby.copy(playerList = updatedPlayerList)
            }
            currentState.copy(currentLobby = updatedLobby)
        }
        updatedMe?.let { nearbyRepository.broadcastPlayer(it) }
    }

    fun startGame() {
        privUiState.update {
            it.copy(
                gameState = true
            )
        }
        // Broadcast Start Game to all clients
        nearbyRepository.broadcastStartGame()

        // Start tracking location and orientation when the game starts
        startLocationTracking()
        sensorRepository.startListening()
    }

    fun stopDiscoveringLobbies() {
        nearbyRepository.stopDiscovery()
    }

    fun forfeitGame() {
        var updatedMe: Player? = null
        privUiState.update { currentState ->
            val updatedLobby = currentState.currentLobby?.let { lobby ->
                lobby.copy(
                    playerList = lobby.playerList.map { player ->
                        if (player.id == localPlayerId) {
                            val up = player.copy(isAlive = false)
                            updatedMe = up
                            up
                        } else player
                    }
                )
            }
            // Set gameState = false immediately so LaunchedEffect(currentLobby)
            // routes this player back to the lobby screen
            currentState.copy(currentLobby = updatedLobby, gameState = false)
        }
        stopLocationTracking()
        sensorRepository.stopListening()
        updatedMe?.let { nearbyRepository.broadcastPlayer(it) }
        // Winner detection runs on the OTHER device's incomingPlayers collector.
        // This device is already leaving regardless of what others do.
    }

    override fun onCleared() {
        super.onCleared()
        nearbyRepository.stopAll()
        locationRepository.stopTracking()
        sensorRepository.stopListening()
    }
}
