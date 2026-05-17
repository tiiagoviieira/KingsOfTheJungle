package com.example.kingsofthejungle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kingsofthejungle.Lobby
import androidx.compose.ui.tooling.preview.Preview
import com.example.kingsofthejungle.ui.theme.KingsOfTheJungleTheme
import com.example.kingsofthejungle.Player
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Kings Of The Jungle") },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { })
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = playerName,
                onValueChange = onNameChange,
                label = { Text("Enter Player Name") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onContinue, enabled = playerName.isNotBlank()) {
                Text("Enter")
            }
        }
    }
}

@Composable
fun ActionSelectionScreen(
    onCreateLobbyClick: () -> Unit,
    onJoinLobbyClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onCreateLobbyClick, modifier = Modifier.fillMaxWidth(0.6f)) {
            Text("Create Lobby")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onJoinLobbyClick, modifier = Modifier.fillMaxWidth(0.6f)) {
            Text("Join Local Lobby")
        }
    }
}

@Composable
fun LobbyScreen(
    lobby: Lobby,
    isCurrentUserAdmin: Boolean,
    onReadyClick: () -> Unit,
    onStartGameClick: () -> Unit
) {
    val allReady = lobby.playerList.all { it.isReady }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Lobby: ${lobby.name}", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Text("Players (${lobby.playerList.size}/${lobby.maxPlayers})")

        lobby.playerList.forEach { player ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = player.name, modifier = Modifier.weight(1f))
                Text(text = if (player.isReady) "Ready" else "Waiting...")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onReadyClick, modifier = Modifier.fillMaxWidth()) {
            Text("Toggle Ready")
        }

        if (isCurrentUserAdmin) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStartGameClick,
                enabled = allReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Game")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InGameScreen(
    currentPlayerHeartRate: Int,
    players: List<Player>,
    onForfeitClick: () -> Unit
) {
    val myTeam = players.firstOrNull { it.id == "me" }?.team ?: "Red"
    val teammates = players.filter { it.team == myTeam }
    val aliveTeammatesCount = teammates.count { it.isAlive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hunt On") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Rate",
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$currentPlayerHeartRate BPM",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = onForfeitClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forfeit")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Team Alive", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "$aliveTeammatesCount / ${teammates.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFFE0E0E0)) 
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                players.forEach { player ->
                    val dotColor = when {
                        !player.isAlive -> Color.Gray
                        player.id == "me" -> Color.Green
                        player.team == "Red" -> Color.Red
                        else -> Color.Blue
                    }

                    drawCircle(
                        color = dotColor,
                        radius = 25f,
                        center = Offset(player.x * canvasWidth, player.y * canvasHeight)
                    )
                }
            }
        }
    }
}




@Preview(showBackground = true, name = "Login")
@Composable
fun LoginScreenPreview() {
    KingsOfTheJungleTheme { 
        LoginScreen(
            playerName = "Tiago",
            onNameChange = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, name = "Selection")
@Composable
fun ActionSelectionPreview() {
    KingsOfTheJungleTheme {
        ActionSelectionScreen(
            onCreateLobbyClick = {},
            onJoinLobbyClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Lobby")
@Composable
fun LobbyScreenPreview() {
    val mockPlayer1 = Player("1", "Tiago (Admin)", isReady = true, isAdmin = true)
    val mockPlayer2 = Player("2", "João", isReady = false, isAdmin = false)
    val mockLobby = Lobby("lobby1", "Jungle do Tiago", 4, listOf(mockPlayer1, mockPlayer2))

    KingsOfTheJungleTheme {
        LobbyScreen(
            lobby = mockLobby,
            isCurrentUserAdmin = true,
            onReadyClick = {},
            onStartGameClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Game On", device = "id:pixel_5")
@Composable
fun PreviewInGameScreen() {
    val mockPlayers = listOf(
        Player(id = "me", name = "Tiago", x = 0.5f, y = 0.8f, team = "Red", isAlive = true),
        Player(id = "2", name = "João", x = 0.2f, y = 0.6f, team = "Red", isAlive = true),
        Player(id = "3", name = "Maria", x = 0.8f, y = 0.9f, team = "Red", isAlive = false), 
        Player(id = "4", name = "Enemy1", x = 0.5f, y = 0.2f, team = "Blue", isAlive = true),
        Player(id = "5", name = "Enemy2", x = 0.7f, y = 0.3f, team = "Blue", isAlive = true)
    )

    KingsOfTheJungleTheme {
        InGameScreen(
            currentPlayerHeartRate = 115,
            players = mockPlayers,
            onForfeitClick = {}
        )
    }
}
