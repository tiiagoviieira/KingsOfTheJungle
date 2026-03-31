package com.example.kingsofthejungle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kingsofthejungle.ui.theme.ActionSelectionScreen
import com.example.kingsofthejungle.ui.theme.LobbyScreen
import com.example.kingsofthejungle.ui.theme.LoginScreen
import com.example.kingsofthejungle.ui.theme.KingsOfTheJungleTheme
import com.example.kingsofthejungle.ui.theme.GameMapScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KingsOfTheJungleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainNavigation()
                }
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel()
    val uiState = viewModel.publicUiState.collectAsState().value

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
                    navController.navigate("lobby")
                },
                onJoinLobbyClick = {
                    // Mocking joining a lobby
                    navController.navigate("lobby")
                }
            )
        }

        composable("lobby") {
            uiState.currentLobby?.let { lobby ->
                val isAdmin = lobby.playerList.find { it.id == "me" }?.isAdmin == true
                LobbyScreen(
                    lobby = lobby,
                    isCurrentUserAdmin = isAdmin,
                    onReadyClick = { viewModel.toggleReadyStatus() },
                    onStartGameClick = {
                        viewModel.startGame()
                        navController.navigate("game_map")
                    }
                )
            }
        }

        composable("game_map") {
            GameMapScreen(uiState = uiState)
        }
    }
}
