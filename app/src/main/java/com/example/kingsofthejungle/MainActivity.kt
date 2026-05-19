package com.example.kingsofthejungle

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kingsofthejungle.ui.screens.ActionSelectionScreen
import com.example.kingsofthejungle.ui.screens.LobbyScreen
import com.example.kingsofthejungle.ui.screens.LoginScreen
import com.example.kingsofthejungle.ui.theme.KingsOfTheJungleTheme
import com.example.kingsofthejungle.ui.screens.GameMapScreen
import com.example.kingsofthejungle.ui.screens.JoinLobbyScreen

class MainActivity : ComponentActivity() {
    private lateinit var nearbyRepository: NearbyConnectionsRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var sensorRepository: SensorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        nearbyRepository = NearbyConnectionsRepository(applicationContext)
        locationRepository = LocationRepository(applicationContext)
        sensorRepository = SensorRepository(applicationContext)
        
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return GameViewModel(nearbyRepository, locationRepository, sensorRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        setContent {
            KingsOfTheJungleTheme {
                RequestGamePermissions()
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainNavigation(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun RequestGamePermissions() {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BODY_SENSORS
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }
}

@Composable
fun MainNavigation(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel(factory = viewModelFactory)
    val uiState = viewModel.publicUiState.collectAsState().value

    // Auto-navigate to lobby when a lobby object is received (connection successful)
    LaunchedEffect(uiState.currentLobby) {
        if (uiState.currentLobby != null && !uiState.gameState) {
            // Only navigate if we're not already on the lobby screen.
            // Previously this fired on every lobby update (player moved, toggled ready, etc.)
            // causing the screen to re-enter and visually "flash".
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != "lobby") {
                navController.navigate("lobby") {
                    popUpTo("action_selection") { inclusive = false }
                }
            }
        }
    }

    // Auto-navigate to game map when gameState is true (sync across network)
    LaunchedEffect(uiState.gameState) {
        if (uiState.gameState) {
            navController.navigate("game_map") {
                // Ensure we don't build up a backstack of lobby screens
                popUpTo("action_selection") { inclusive = false }
            }
        }
    }

    NavHost(navController = navController, startDestination = "login") {

        composable("login") {
            LoginScreen(
                playerName = uiState.playerName,
                onNameChange = { viewModel.updatePlayerName(it) },
                onContinue = { navController.navigate("action_selection") }
            )
        }

        composable("action_selection") {
            ActionSelectionScreen(
                onCreateLobbyClick = {
                    viewModel.createLobby(lobbyName = "${uiState.playerName}'s Jungle", maxPlayers = 4)
                },
                onJoinLobbyClick = {
                    viewModel.startDiscoveringLobbies()
                    navController.navigate("join_lobby")
                }
            )
        }

        composable("join_lobby") {
            DisposableEffect(Unit) {
                onDispose { viewModel.stopDiscoveringLobbies() }
            }
            JoinLobbyScreen(
                uiState = uiState,
                onLobbyClick = { endpointId ->
                    viewModel.joinDiscoveredLobby(endpointId)
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("lobby") {
            uiState.currentLobby?.let { lobby ->
                val isAdmin = lobby.playerList.find { it.id == uiState.localPlayerId }?.isAdmin == true
                val isReady = lobby.playerList.find { it.id == uiState.localPlayerId }?.isReady == true
                LobbyScreen(
                    lobby = lobby,
                    isCurrentUserAdmin = isAdmin,
                    isCurrentUserReady = isReady,
                    gameMessage = uiState.gameMessage,
                    onGameMessageDismissed = { viewModel.clearGameMessage() },
                    onReadyClick = { viewModel.toggleReadyStatus() },
                    onStartGameClick = { viewModel.startGame() },
                    onLeaveClick = {
                        viewModel.leaveLobby()
                        navController.navigate("action_selection") {
                            popUpTo("action_selection") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("game_map") {
            GameMapScreen(
                uiState = uiState,
                onForfeitClick = { viewModel.forfeitGame() }
            )
        }
    }
}
