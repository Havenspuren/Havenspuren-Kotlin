// File: LocationDetailsPanel.kt
// This is a new component file

package com.example.havenspure_kotlin_prototype.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.navigation.AudioState
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

@Composable
fun LocationDetailsPanel(
    location: Location,
    audioState: AudioState,
    hasNextLocation: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = location.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = location.detailText,
                fontSize = 16.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Audio controls
            if (location.audioFileName.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        when (audioState) {
                            is AudioState.Playing -> onStopAudio()
                            else -> onPlayAudio(location.audioFileName)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryColor
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(PrimaryColor)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (audioState) {
                            is AudioState.Playing -> "Audio stoppen"
                            is AudioState.Loading -> "Wird geladen..."
                            is AudioState.Stopped -> "Audio abspielen"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Navigation buttons
            if (hasNextLocation) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Weiter zum nächsten Standort")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null
                    )
                }
            } else {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tour abschließen")
                }
            }
        }
    }
}