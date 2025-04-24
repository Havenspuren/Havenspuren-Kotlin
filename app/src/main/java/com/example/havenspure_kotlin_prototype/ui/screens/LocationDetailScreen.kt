import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.OSRM.ui.components.AudioPlayerManager
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationState
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    location: Location,
    tour: Tour,
    tourNavigator: TourNavigator,
    onBackClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Audio playback states
    var hasPlayedAudio by remember { mutableStateOf(false) }
    var isAudioPlayerVisible by remember { mutableStateOf(false) }

    // Get audio state from navigator
    val audioState by tourNavigator.audioState.collectAsState()

    // Check if location has audio
    val hasAudio = !location.audioFileName.isNullOrEmpty()

    // Check if location has image
    val hasImage = !location.imageName.isNullOrEmpty()

    // Check if the current location is already visited
    val isAlreadyVisited = tourNavigator.isLocationVisited(location.id)

    // Calculate bottom padding for content
    val bottomPadding = if (isAudioPlayerVisible) 140.dp else 80.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tour.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Zurück",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
        ) {
            // Main content with appropriate bottom padding
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(bottom = bottomPadding)
            ) {
                // Location title
                Text(
                    text = "${location.order}. ${location.name}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Location image if available
                if (hasImage) {
                    val imagePath = tourNavigator.getLocationImagePath(location.id)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.placeholder_image),
                            contentDescription = location.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bubble text (if present)
                if (location.bubbleText.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Text(
                            text = location.bubbleText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                // Detail text
                if (location.detailText.isNotEmpty()) {
                    Text(
                        text = location.detailText,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Justify
                    )
                }

                // Trophy section if location has trophy
                if (location.hasTrophy) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF9C4) // Light yellow for trophy
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_trophy),
                                contentDescription = "Trophy",
                                tint = Color(0xFFFFD700), // Gold color
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(bottom = 8.dp)
                            )

                            Text(
                                text = location.trophyTitle ?: "Trophy",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )

                            if (!location.trophyDescription.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = location.trophyDescription,
                                    fontSize = 14.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Fixed position for audio player (when visible)
            if (hasAudio) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
                ) {
                    AudioPlayerManager(
                        modifier = Modifier.fillMaxWidth(),
                        tourNavigator = tourNavigator,
                        audioState = audioState,
                        navigationState = TourNavigationState.AtLocation,
                        tourProgress = 0f,
                        onAudioPlaybackStarted = {
                            hasPlayedAudio = true
                            isAudioPlayerVisible = true
                        },
                        onAudioPlaybackStopped = {
                            isAudioPlayerVisible = false
                        }
                    )
                }
            }

            // Finish button always at the bottom
            Button(
                onClick = {
                    scope.launch {
                        if (!isAlreadyVisited) {
                            tourNavigator.markCurrentLocationAsVisited()
                        }
                        tourNavigator.setNavigationState(TourNavigationState.AtLocation)
                        if (hasPlayedAudio) {
                            tourNavigator.stopAudio()
                        }
                        delay(100)
                        onFinishClick()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor
                )
            ) {
                Text(
                    text = "Fertig",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }

    // Load the current location into the navigator when the screen appears
    LaunchedEffect(location) {
        // Set the current location
        tourNavigator.setCurrentLocationDirectly(location)

        // Automatically play audio if available
        if (hasAudio && !hasPlayedAudio) {
            scope.launch {
                delay(500) // Small delay to ensure everything is loaded
                tourNavigator.playLocationAudio(location)
                hasPlayedAudio = true
                isAudioPlayerVisible = true
            }
        }
    }
}