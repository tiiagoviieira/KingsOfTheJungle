package com.example.kingsofthejungle.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kingsofthejungle.Lobby
import com.example.kingsofthejungle.Player

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
                title = { Text("Mesh Tracker") },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { /* TODO */ })
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

        // List of players
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
