package com.example.havenspure_kotlin_prototype.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.ui.components.AudioPlayerManager
import com.example.havenspure_kotlin_prototype.OSRM.ui.components.DirectionPanel
import com.example.havenspure_kotlin_prototype.OSRM.ui.components.NavigationButtons
import com.example.havenspure_kotlin_prototype.OSRM.ui.components.TourProgressIndicator
import com.example.havenspure_kotlin_prototype.OSRM.ui.effects.MapLifecycleEffects
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.di.Graph
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationState
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView

enum class NavigationUiState { LOADING, PARTIAL, READY }

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    tourId: String,
    onBackClick: () -> Unit,
    onShowLocationDetail: (Location) -> Unit,
    locationViewModel: LocationViewModel,
    tourNavigator: TourNavigator
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Get toursViewModel from DI
    val toursViewModel = Graph.getInstance().toursViewModel

    // Flag to track if audio has been played for current location
    var hasPlayedAudio by remember { mutableStateOf(false) }

    // Flag to track if the audio player is showing
    var isAudioPlayerVisible by remember { mutableStateOf(false) }

    // Create MapViewModel
    val mapViewModel: MapViewModel = viewModel { MapViewModel(context) }

    // Get location from LocationViewModel
    val userLocationState by locationViewModel.location

    // States for loading and tour data
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Observe states from MapViewModel
    val isNavigating by mapViewModel.isNavigating.collectAsState()
    val navigationInstruction by mapViewModel.navigationInstruction.collectAsState()
    val remainingDistance by mapViewModel.remainingDistance.collectAsState()
    val hasArrived by mapViewModel.hasArrived.collectAsState()
    val followUserLocation by mapViewModel.followUserLocation.collectAsState()
    val isFullyReady by mapViewModel.isFullyReady.collectAsState()

    // State for current and next locations
    val currentLocation by tourNavigator.currentLocation.collectAsState()
    val nextLocation by tourNavigator.nextLocation.collectAsState()
    val visitedLocations by tourNavigator.visitedLocations.collectAsState()
    val tourLocations by tourNavigator.tourLocations.collectAsState()
    val tourProgress by tourNavigator.tourProgress.collectAsState()
    val navigationState by tourNavigator.navigationState.collectAsState()
    val isNearLocation by tourNavigator.isInProximity.collectAsState()

    // Get the current location index (0-based) from the tourNavigator
    val currentLocationIndex = remember(currentLocation) {
        currentLocation?.let { tourNavigator.getCurrentLocationIndex() } ?: 0
    }

    // Audio state
    val audioState by tourNavigator.audioState.collectAsState()

    // UI state for map display
    var uiState by remember { mutableStateOf(NavigationUiState.LOADING) }

    // Update UI state based on combined conditions
    LaunchedEffect(isFullyReady, isLoading) {
        if (isFullyReady && !isLoading) {
            // Small delay to ensure all UI elements are updated before showing map
            delay(100)
            uiState = NavigationUiState.READY
        } else if (!isLoading) {
            uiState = NavigationUiState.PARTIAL
        } else {
            uiState = NavigationUiState.LOADING
        }
    }

    val mapAlpha by animateFloatAsState(
        targetValue = when (uiState) {
            NavigationUiState.LOADING -> 0f
            NavigationUiState.PARTIAL -> 0.7f
            NavigationUiState.READY -> 1f
        },
        label = "mapAlpha"
    )

    // Loading visibility state
    val loadingAlpha by animateFloatAsState(
        targetValue = when (uiState) {
            NavigationUiState.LOADING -> 1f
            NavigationUiState.PARTIAL -> 0.3f
            NavigationUiState.READY -> 0f
        },
        label = "loadingAlpha"
    )

    // Map reference
    val mapView = remember { MapView(context) }

    // Colors for the map
    val routeColor = PrimaryColor.toArgb()
    val userMarkerColor = Color.Blue.toArgb()
    val destinationMarkerColor = Color.Red.toArgb()

    // Format distance for location display
    val formattedDistance = remember(userLocationState, currentLocation) {
        userLocationState?.let { userLoc ->
            currentLocation?.let { targetLoc ->
                val distance = tourNavigator.calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    targetLoc.latitude, targetLoc.longitude
                )
                tourNavigator.formatDistance(distance)
            }
        } ?: "Calculating..."
    }

    // Load tour data
    LaunchedEffect(tourId) {
        isLoading = true
        errorMessage = null

        toursViewModel.getTourWithLocations(tourId) { tourWithLocations ->
            if (tourWithLocations != null) {
                scope.launch {
                    try {
                        val visitedLocations = Graph.getInstance().userProgressRepository
                            .getVisitedLocationsForTour(tourId)

                        val visitedIds = visitedLocations.map { it.locationId }

                        // Initialize tour navigator with tour data
                        tourNavigator.initializeWithTour(tourWithLocations, visitedIds)
                        isLoading = false
                    } catch (e: Exception) {
                        errorMessage = "Failed to load visited locations"
                        isLoading = false
                    }
                }
            } else {
                errorMessage = "Failed to load tour"
                isLoading = false
            }
        }
    }

    // Initialize the map
    LaunchedEffect(Unit) {
        mapViewModel.setColors(routeColor, userMarkerColor, destinationMarkerColor)
        mapViewModel.setupMapView(mapView)
        mapViewModel.setupMapTileLoadingListener(mapView)
    }

    // Update MapViewModel when location changes
    LaunchedEffect(userLocationState, currentLocation) {
        val userLoc = userLocationState
        val targetLoc = currentLocation

        if (userLoc != null && targetLoc != null) {
            // Convert to OSRM format
            val userLocationOSRM = LocationDataOSRM(
                latitude = userLoc.latitude,
                longitude = userLoc.longitude
            )

            val destinationOSRM = LocationDataOSRM(
                latitude = targetLoc.latitude,
                longitude = targetLoc.longitude
            )

            // Update locations in MapViewModel
            mapViewModel.updateUserLocation(userLocationOSRM)
            mapViewModel.setDestinationLocation(destinationOSRM)

            // Start navigation if not already navigating
            if (!isNavigating) {
                mapViewModel.startNavigation(mapViewModel.destinationLocation, mapView)
            } else {
                // Update existing navigation
                mapViewModel.updateNavigation(mapView)
            }

            // Check proximity to current location - using the default 150m threshold in TourNavigator
            tourNavigator.isNearCurrentLocation(
                userLoc.latitude,
                userLoc.longitude
            )
        }
    }

    // Audio playback when near location - MODIFIED
    LaunchedEffect(isNearLocation, currentLocationIndex) {
        // Only play audio automatically for the first location (index 0) or when near any location
        if (isNearLocation && !hasPlayedAudio && navigationState == TourNavigationState.EnRoute &&
            (currentLocationIndex == 0 || tourProgress > 0f)) {

            // Play audio for this location if available
            tourNavigator.getCurrentLocationAudioFile()?.let { audioFile ->
                if (audioFile.isNotEmpty()) {
                    tourNavigator.playAudio(audioFile)
                    hasPlayedAudio = true
                    isAudioPlayerVisible = true
                }
            }
        }
    }

    // Lifecycle management for map
    MapLifecycleEffects(
        lifecycleOwner = lifecycleOwner,
        mapView = mapView,
        mapViewModel = mapViewModel,
        tourNavigator = tourNavigator,
        isNavigating = isNavigating,
        hasPlayedAudio = hasPlayedAudio,
        onResetAudioFlag = {
            hasPlayedAudio = false
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tour Navigation", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Stop navigation when going back
                        if (isNavigating) {
                            mapViewModel.stopNavigation()
                        }
                        onBackClick()
                    }) {
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
            if (isLoading) {
                // Loading indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (errorMessage != null) {
                // Error message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "An error occurred",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            } else {
                // Main navigation content with modified layout
                Column(modifier = Modifier.fillMaxSize()) {
                    // Progress Row - Uses the TourProgressIndicator component
                    TourProgressIndicator(
                        tourNavigator = tourNavigator,
                        visitedLocationsCount = visitedLocations.size,
                        totalLocationsCount = tourLocations.size
                    )

                    // Direction info panel - Uses the DirectionPanel component
                    DirectionPanel(
                        currentLocation = currentLocation,
                        formattedDistance = formattedDistance,
                        navigationState = navigationState,
                        navigationInstruction = navigationInstruction,
                        remainingDistance = remainingDistance
                    )

                    // Map container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Map view with improved rendering
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(mapAlpha),
                            update = { view ->
                                // Ensure map is properly updated when shown
                                if (uiState == NavigationUiState.READY) {
                                    view.invalidate()
                                }
                            }
                        )

                        // Loading overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(loadingAlpha)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryColor,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Route wird berechnet...",
                                    color = PrimaryColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        // AudioPlayerManager
                        AudioPlayerManager(
                            modifier = Modifier.fillMaxSize(),
                            tourNavigator = tourNavigator,
                            audioState = audioState,
                            navigationState = navigationState,
                            tourProgress = tourProgress,
                            onAudioPlaybackStarted = {
                                hasPlayedAudio = true
                                isAudioPlayerVisible = true
                            },
                            onAudioPlaybackStopped = {
                                isAudioPlayerVisible = false
                            }
                        )

                        // My location button - Above the audio button
                        FloatingActionButton(
                            onClick = {
                                mapViewModel.setFollowUserLocation(true)
                                mapViewModel.centerOnUserLocation(mapView)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 88.dp)
                                .size(56.dp)
                                .alpha(mapAlpha),
                            containerColor = Color(0xFF03A9F4)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Auf meinen Standort zentrieren",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Navigation buttons - Modified to navigate to location detail screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isAudioPlayerVisible) 72.dp else 0.dp)
                        ) {
                            NavigationButtons(
                                navigationState = navigationState,
                                isNearLocation = isNearLocation,
                                userLocationState = userLocationState,
                                currentLocation = currentLocation,
                                tourNavigator = tourNavigator,
                                mapViewModel = mapViewModel,
                                mapView = mapView,
                                hasPlayedAudio = hasPlayedAudio,
                                onMarkLocationVisited = {
                                    // Navigate to the location detail screen
                                    currentLocation?.let { location ->
                                        // Stop audio if it's playing
                                        if (hasPlayedAudio) {
                                            tourNavigator.stopAudio()
                                            isAudioPlayerVisible = false
                                        }

                                        // Navigate to the location detail screen
                                        onShowLocationDetail(location)
                                    }
                                },
                                onProceedToNextLocation = {
                                    tourNavigator.proceedToNextLocation()
                                },
                                onResetAudioFlag = {
                                    hasPlayedAudio = false
                                },
                                onStartNewNavigation = {
                                    // Add delay to ensure state is updated before starting new navigation
                                    scope.launch {
                                        delay(100) // Small delay to allow state updates

                                        // Create a new navigation to the next location
                                        val userLoc = userLocationState
                                        val targetLoc = currentLocation

                                        if (userLoc != null && targetLoc != null) {
                                            // Convert to OSRM format
                                            val userLocationOSRM = LocationDataOSRM(
                                                latitude = userLoc.latitude,
                                                longitude = userLoc.longitude
                                            )

                                            val destinationOSRM = LocationDataOSRM(
                                                latitude = targetLoc.latitude,
                                                longitude = targetLoc.longitude
                                            )

                                            // Update locations in MapViewModel
                                            mapViewModel.updateUserLocation(userLocationOSRM)
                                            mapViewModel.setDestinationLocation(destinationOSRM)

                                            // Force recalculation by starting a completely new navigation
                                            mapViewModel.startNavigation(mapViewModel.destinationLocation, mapView)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}