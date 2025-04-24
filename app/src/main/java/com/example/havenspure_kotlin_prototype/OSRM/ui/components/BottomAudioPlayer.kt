package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.navigation.AudioState
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

/**
 * A compact bottom media player control for audio playback
 */
@Composable
fun BottomAudioPlayer(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    audioState: AudioState,
    audioTitle: String,
    progress: Float,
    duration: Int,
    currentPosition: Int,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onForwardClick: () -> Unit,
    onRewindClick: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + expandVertically() + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        // Format duration
        val formattedCurrentTime = formatDuration(currentPosition / 1000)
        val formattedTotalTime = formatDuration(duration / 1000)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Title row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = audioTitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close player",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Middle row with controls and progress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Control buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // Rewind 5 seconds button
                        IconButton(
                            onClick = onRewindClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_rewind_5),
                                contentDescription = "Rewind 5 seconds",
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Play/Pause button
                        IconButton(
                            onClick = {
                                if (audioState is AudioState.Playing) {
                                    onPauseClick()
                                } else {
                                    onPlayClick()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (audioState is AudioState.Playing)
                                    Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (audioState is AudioState.Playing)
                                    "Pause" else "Play",
                                tint = PrimaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Forward 5 seconds button
                        IconButton(
                            onClick = onForwardClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_forward_5),
                                contentDescription = "Forward 5 seconds",
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Time and slider column
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Slider
                        Slider(
                            value = progress,
                            onValueChange = onSeekTo,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryColor,
                                activeTrackColor = PrimaryColor,
                                inactiveTrackColor = Color.LightGray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        )

                        // Time indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formattedCurrentTime,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = formattedTotalTime,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format seconds into MM:SS string
 */
private fun formatDuration(durationInSeconds: Int): String {
    val minutes = durationInSeconds / 60
    val seconds = durationInSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}