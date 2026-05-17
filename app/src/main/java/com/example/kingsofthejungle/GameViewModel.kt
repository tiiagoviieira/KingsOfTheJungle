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
    val publicUiState : StateFlow<AppUiState> = privUiState.asStateFlow()

    private var mapCenterLocation: Location? = null

    init {
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
                                        if (player.id == "me") {
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

        // Collect incoming movements from other players via Nearby Connections
        viewModelScope.launch {
            nearbyRepository.incomingPlayers.collect { incomingPlayer ->
                privUiState.update { currentState ->
                    val updatedLobby = currentState.currentLobby?.let { lobby ->
                        // If player exists, update them. If not, add them to the list.
                        val exists = lobby.playerList.any { it.id == incomingPlayer.id }
                        val newList = if (exists) {
                            lobby.playerList.map { 
                                if (it.id == incomingPlayer.id) incomingPlayer else it 
                            }
                        } else {
                            lobby.playerList + incomingPlayer
                        }
                        lobby.copy(playerList = newList)
                    }
                    currentState.copy(currentLobby = updatedLobby)
                }
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
    }

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
        nearbyRepository.startAdvertising(privUiState.value.playerName)
    }

    fun startDiscoveringLobbies() {
        nearbyRepository.startDiscovering()
    }

    fun startLocationTracking() {
        locationRepository.startTracking()
    }

    fun stopLocationTracking() {
        locationRepository.stopTracking()
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
        // Broadcast Start Game to all clients
        nearbyRepository.broadcastStartGame()

        // Start tracking location and orientation when the game starts
        startLocationTracking()
        sensorRepository.startListening()
    }

    fun forfeitGame() {
        var updatedMe: Player? = null
        privUiState.update { currentState ->
            val updatedLobby = currentState.currentLobby?.let { lobby ->
                val updatedPlayerList = lobby.playerList.map { player ->
                    if (player.id == "me") {
                        val up = player.copy(isAlive = false)
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
        updatedMe?.let {
            nearbyRepository.broadcastPlayer(it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyRepository.stopAll()
        locationRepository.stopTracking()
        sensorRepository.stopListening()
    }
}
