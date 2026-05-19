package com.example.kingsofthejungle

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NearbyConnectionsRepository(private val context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_CLUSTER
    private val serviceId = "com.example.kingsofthejungle.SERVICE_ID"
    private val gson = Gson()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    
    private val _newConnections = MutableSharedFlow<Unit>()
    val newConnections = _newConnections.asSharedFlow()

    private val _discoveryError = MutableSharedFlow<String>()
    val discoveryError = _discoveryError.asSharedFlow()

    private val _connectedEndpointIds = mutableSetOf<String>()
    
    private val _incomingPlayers = MutableSharedFlow<Player>()
    val incomingPlayers = _incomingPlayers.asSharedFlow()

    private val _incomingLobby = MutableSharedFlow<Lobby>()
    val incomingLobby = _incomingLobby.asSharedFlow()

    private val _gameCommands = MutableSharedFlow<String>()
    val gameCommands = _gameCommands.asSharedFlow()

    private val _discoveredEndpoints = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val discoveredEndpoints = _discoveredEndpoints.asStateFlow()

    private val _endpointToPlayerId = mutableMapOf<String, String>()

    private val _playerLeaves = MutableSharedFlow<String>()
    val playerLeaves = _playerLeaves.asSharedFlow()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("NearbyRepo", "onConnectionInitiated: $endpointId, name: ${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d("NearbyRepo", "onConnectionResult: STATUS_OK with $endpointId")
                    _connectedEndpointIds.add(endpointId)
                    repositoryScope.launch { _newConnections.emit(Unit) }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d("NearbyRepo", "onConnectionResult: REJECTED with $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.d("NearbyRepo", "onConnectionResult: ERROR with $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("NearbyRepo", "onDisconnected: $endpointId")
            _connectedEndpointIds.remove(endpointId)
            val playerId = _endpointToPlayerId.remove(endpointId)
            if (playerId != null) {
                repositoryScope.launch { _playerLeaves.emit(playerId) }
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("NearbyRepo", "onEndpointFound: $endpointId, name: ${info.endpointName}")
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("NearbyRepo", "onEndpointLost: $endpointId")
            _discoveredEndpoints.value = _discoveredEndpoints.value.filter { it.first != endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes()
            if (bytes != null) {
                val receivedString = String(bytes)
                if (receivedString == "START_GAME") {
                    repositoryScope.launch {
                        _gameCommands.emit(receivedString)
                    }
                } else {
                    try {
                        if (receivedString.contains("maxPlayers")) {
                            val lobby = gson.fromJson(receivedString, Lobby::class.java)
                            repositoryScope.launch {
                                _incomingLobby.emit(lobby)
                            }
                        } else {
                            val player = gson.fromJson(receivedString, Player::class.java)

                            _endpointToPlayerId[endpointId] = player.id
                            repositoryScope.launch {
                                _incomingPlayers.emit(player)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NearbyRepo", "Failed to parse payload", e)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun broadcastPlayer(player: Player) {
        if (_connectedEndpointIds.isEmpty()) return
        
        val jsonString = gson.toJson(player)
        val payload = Payload.fromBytes(jsonString.toByteArray())
        
        connectionsClient.sendPayload(_connectedEndpointIds.toList(), payload)
            .addOnFailureListener { e -> Log.e("NearbyRepo", "Broadcast failed", e) }
    }

    fun broadcastLobby(lobby: Lobby) {
        if (_connectedEndpointIds.isEmpty()) return
        
        val jsonString = gson.toJson(lobby)
        val payload = Payload.fromBytes(jsonString.toByteArray())

        connectionsClient.sendPayload(_connectedEndpointIds.toList(), payload)
            .addOnFailureListener { e -> Log.e("NearbyRepo", "Broadcast Lobby failed", e) }
    }

    fun broadcastStartGame() {
        if (_connectedEndpointIds.isEmpty()) return
        val payload = Payload.fromBytes("START_GAME".toByteArray())
        connectionsClient.sendPayload(_connectedEndpointIds.toList(), payload)
            .addOnFailureListener { e -> Log.e("NearbyRepo", "Broadcast Start Game failed", e) }
    }

    fun startAdvertising(playerName: String) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(playerName, serviceId, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener { Log.d("NearbyRepo", "Advertising started") }
            .addOnFailureListener { e -> Log.e("NearbyRepo", "Advertising failed", e) }
    }

    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.d("NearbyRepo", "Discovery started") }
            .addOnFailureListener { e ->
                Log.e("NearbyRepo", "Discovery failed", e)
                repositoryScope.launch {
                    _discoveryError.emit("Discovery failed: ${e.message ?: "Unknown error"}. Ensure Bluetooth and Location are enabled.")
                }
            }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        Log.d("NearbyRepo", "Discovery stopped")
    }

    fun requestConnectionTo(endpointId: String, playerName: String) {
        connectionsClient.requestConnection(playerName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener { Log.d("NearbyRepo", "Connection requested to $endpointId") }
            .addOnFailureListener { e -> Log.e("NearbyRepo", "Connection request failed", e) }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedEndpointIds.clear()
        _endpointToPlayerId.clear()
        _discoveredEndpoints.value = emptyList()
    }
}
