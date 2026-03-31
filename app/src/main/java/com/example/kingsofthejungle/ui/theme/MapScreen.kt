package com.example.kingsofthejungle.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.kingsofthejungle.AppUiState
import com.example.kingsofthejungle.Player

@Composable
fun GameMapScreen(uiState: AppUiState) {
    val players = uiState.currentLobby?.playerList ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Draw the Mesh Connections and Nodes
        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw Connections (Lines between players)
            // For a mesh, you might want to draw lines between all players or just neighbors
            players.forEachIndexed { index, player ->
                players.drop(index + 1).forEach { other ->
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(player.x * canvasWidth, player.y * canvasHeight),
                        end = Offset(other.x * canvasWidth, other.y * canvasHeight),
                        strokeWidth = 2f
                    )
                }
            }

            // 2. Draw Player Nodes
            players.forEach { player ->
                drawCircle(
                    color = if (player.id == "me") Color.Green else Color.Blue,
                    radius = 20f,
                    center = Offset(player.x * canvasWidth, player.y * canvasHeight)
                )
                drawCircle(
                    color = Color.White,
                    radius = 20f,
                    center = Offset(player.x * canvasWidth, player.y * canvasHeight),
                    style = Stroke(width = 2f)
                )
            }
        }

        // 3. Overlay UI (Player Names)
        players.forEach { player ->
            Box(
                modifier = Modifier
                    .offset(x = (player.x * 300).dp, y = (player.y * 500).dp) // Approximate for positioning
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
        
        Text(
            text = "Mesh Status: Active",
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
