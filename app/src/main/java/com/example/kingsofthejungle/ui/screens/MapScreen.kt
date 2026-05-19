package com.example.kingsofthejungle.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kingsofthejungle.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMapScreen(
    uiState: AppUiState,
    onForfeitClick: () -> Unit
) {
    val players = uiState.currentLobby?.playerList ?: emptyList()
    val myPlayer = players.find { it.id == uiState.localPlayerId }
    val myTeam = myPlayer?.team ?: ""
    val teammates = players.filter { it.team == myTeam }
    val aliveTeammatesCount = teammates.count { it.isAlive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentLobby?.name ?: "Game") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Rate",
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.heartRate != null) "${uiState.heartRate} BPM" else "-- BPM",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = onForfeitClick) {
                        Text("Forfeit", color = MaterialTheme.colorScheme.error)
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
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Rotating Map Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .graphicsLayer { rotationZ = -uiState.compassHeading }
            ) {
                // Draw Mesh Connections and Player Dots
                Canvas(modifier = Modifier.fillMaxSize()) {
                    players.forEachIndexed { index, player ->
                        players.drop(index + 1).forEach { other ->
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(player.x * size.width, player.y * size.height),
                                end = Offset(other.x * size.width, other.y * size.height),
                                strokeWidth = 2f
                            )
                        }
                    }

                    players.forEach { player ->
                        val dotColor = when {
                            !player.isAlive -> Color.Gray
                            player.id == uiState.localPlayerId -> Color.Green
                            else -> Color.Blue
                        }
                        
                        drawCircle(
                            color = dotColor,
                            radius = 20f,
                            center = Offset(player.x * size.width, player.y * size.height)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 20f,
                            center = Offset(player.x * size.width, player.y * size.height),
                            style = Stroke(width = 2f)
                        )
                    }
                }

                // Player Labels that "Rotate Back" to stay readable
                players.forEach { player ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (player.x * maxWidth.value).dp - 40.dp,
                                y = (player.y * maxHeight.value).dp + 15.dp
                            )
                            .graphicsLayer { rotationZ = uiState.compassHeading }
                    ) {
                        Text(
                            text = player.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White
                        )
                    }
                }
            }

            // Static UI Overlay
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Heading: ${uiState.compassHeading.toInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Mesh Status: Active",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
