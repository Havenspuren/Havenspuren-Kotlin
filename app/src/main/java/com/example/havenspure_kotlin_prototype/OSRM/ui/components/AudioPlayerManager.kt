package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.havenspure_kotlin_prototype.navigation.AudioState
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationState
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A manager component that handles the audio player UI and interaction with the tour navigator
 */
@Composable
fun AudioPlayerManager(
    modifier: Modifier = Modifier,
    tourNavigator: TourNavigator,
    audioState: AudioState,
    navigationState: TourNavigationState,
    tourProgress: Float,
    onAudioPlaybackStarted: () -> Unit,
    onAudioPlaybackStopped: () -> Unit
) {
    // State for player visibility and audio playback
    var showAudioFab by remember { mutableStateOf(false) }
    var showAudioPlayer by remember { mutableStateOf(false) }

    // Local audio control states
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var audioTitle by remember { mutableStateOf("Tour Audio") }

    // Get current audio file
    val currentAudioFile = tourNavigator.getCurrentLocationAudioFile()

    // Determine if intro audio should be shown (when progress is 0%)
    val shouldShowIntroAudio = tourProgress == 0f &&
            currentAudioFile?.startsWith("intro") == true

    // Determine if audio button should be shown - either for intro or location
    LaunchedEffect(navigationState, tourProgress, currentAudioFile) {
        // Show for introduction audio if tour progress is 0%
        val hasIntroAudio = shouldShowIntroAudio

        // Show for location audio if at a location or en route
        val hasLocationAudio = currentAudioFile != null &&
                (navigationState == TourNavigationState.AtLocation ||
                        navigationState == TourNavigationState.EnRoute)

        showAudioFab = hasIntroAudio || hasLocationAudio
    }

    // Update audio playback information regularly
    LaunchedEffect(audioState) {
        when (audioState) {
            is AudioState.Playing -> {
                // Get the title from the current playing file
                audioTitle = (audioState as AudioState.Playing).audioFileName
                    .substringBeforeLast(".")
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " ")

                // Show the full player when audio starts
                showAudioPlayer = true
                onAudioPlaybackStarted()

                // Update progress while audio is playing
                while (isActive && audioState is AudioState.Playing) {
                    if (!isUserSeeking) {
                        // Get actual values from TourNavigator
                        duration = tourNavigator.getAudioDuration()
                        currentPosition = tourNavigator.getAudioPosition()

                        // Calculate progress
                        if (duration > 0) {
                            progress = currentPosition.toFloat() / duration
                        }
                    }
                    delay(200) // Update 5 times per second
                }
            }
            is AudioState.Stopped -> {
                // Reset progress when stopped
                progress = 0f
                currentPosition = 0
            }
            else -> { /* Loading state - no action needed */ }
        }
    }

    // Audio player components
    Column(modifier = modifier) {
        // Main container for positioning the button and player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Audio button - positioned BELOW location button
            // Changed position from above (88dp bottom padding) to below (16dp bottom padding)
            AudioPlayerButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp), // Position BELOW location FAB
                isVisible = showAudioFab && !showAudioPlayer,
                audioState = audioState,
                onPlayClick = {
                    // Play audio for current location or intro
                    tourNavigator.playCurrentLocationAudio()
                },
                onPauseClick = {
                    tourNavigator.stopAudio()
                }
            )
        }

        // Bottom player with controls
        BottomAudioPlayer(
            isVisible = showAudioPlayer,
            audioState = audioState,
            audioTitle = audioTitle,
            progress = progress,
            duration = duration,
            currentPosition = currentPosition,
            onPlayClick = {
                tourNavigator.playCurrentLocationAudio()
            },
            onPauseClick = {
                tourNavigator.pauseAudio()
            },
            onForwardClick = {
                tourNavigator.skipForward(5) // Skip forward 5 seconds
            },
            onRewindClick = {
                tourNavigator.skipBackward(5) // Skip backward 5 seconds
            },
            onSeekTo = { newProgress ->
                isUserSeeking = true
                progress = newProgress

                // Calculate position in milliseconds and seek
                val newPositionMs = (newProgress * duration).toInt()
                tourNavigator.seekToPosition(newPositionMs)

                // Update current position immediately for smoother UX
                currentPosition = newPositionMs

                isUserSeeking = false
            },
            onDismiss = {
                // Stop audio and hide player
                tourNavigator.stopAudio()
                showAudioPlayer = false
                onAudioPlaybackStopped()
            }
        )
    }
}