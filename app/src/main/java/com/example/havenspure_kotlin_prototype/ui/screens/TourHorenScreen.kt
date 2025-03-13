package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

/**
 * TourHorenScreen displays an audio player interface for tour audio content.
 *
 * @param tour The tour object containing all details including audio resources
 * @param onBackClick Callback for when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourHorenScreen(
    tour: Tour,
    onBackClick: () -> Unit
) {
    // Audio player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    val totalDuration by remember { mutableStateOf(180f) } // Default 3 minutes (in seconds)
   // val scope = rememberCoroutineScope()

    // Simulate playback when playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && currentPosition < totalDuration) {
            delay(1000) // Update every second
            currentPosition += 1f
            if (currentPosition >= totalDuration) {
                isPlaying = false
                currentPosition = 0f
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${tour.title} - Audio", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Album art / tour image placeholder
            AudioArtworkCard(tour)

            // Audio information
            AudioInfoCard(tour)

            Spacer(modifier = Modifier.weight(1f))

            // Player controls
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = { isPlaying = !isPlaying },
                onRewind = {
                    currentPosition = (currentPosition - 10).coerceAtLeast(0f)
                },
                onForward = {
                    currentPosition = (currentPosition + 10).coerceAtMost(totalDuration)
                },
                onSeek = { position ->
                    currentPosition = position * totalDuration
                },
                currentPosition = currentPosition / totalDuration,
                currentTimeFormatted = formatTime(currentPosition.toInt()),
                totalTimeFormatted = formatTime(totalDuration.toInt())
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AudioArtworkCard(tour: Tour) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PrimaryColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            // To specify a specific image regardless of tour data:
            Image(
                painter = painterResource(id = R.drawable.tour_harbour),
                contentDescription = "Harbor image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Tour title overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = tour.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AudioInfoCard(tour: Tour) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Audioführung",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PrimaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Entdecken Sie die Geschichte von ${tour.title} in dieser Audioführung.",
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp
            )

            if (tour.address != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Addresse: ${tour.address}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Float) -> Unit,
    currentPosition: Float,
    currentTimeFormatted: String,
    totalTimeFormatted: String
) {
    // Seekbar
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentTimeFormatted,
                color = Color.White,
                fontSize = 12.sp
            )

            Text(
                text = totalTimeFormatted,
                color = Color.White,
                fontSize = 12.sp
            )
        }

        // Seekbar
        Slider(
            value = currentPosition,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = PrimaryColor,
                activeTrackColor = PrimaryColor,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
            )
        )

        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind button
            IconButton(
                onClick = onRewind,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play/Pause button (larger)
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Forward button
            IconButton(
                onClick = onForward,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * Formats seconds into MM:SS format
 */
private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}