package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.havenspure_kotlin_prototype.navigation.AudioState

/**
 * A floating action button that starts/pauses audio playback for tour locations
 */
@Composable
fun AudioPlayerButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    audioState: AudioState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = {
                when (audioState) {
                    is AudioState.Playing -> onPauseClick()
                    else -> onPlayClick()
                }
            },
            containerColor = Color(0xFF03A9F4),
            modifier = Modifier
                .size(56.dp)
        ) {
            Icon(
                imageVector = if (audioState is AudioState.Playing)
                    Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (audioState is AudioState.Playing)
                    "Pause Audio" else "Play Audio",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}