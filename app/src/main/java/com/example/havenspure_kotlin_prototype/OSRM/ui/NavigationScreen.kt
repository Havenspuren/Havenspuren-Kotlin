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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.di.Graph
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationCoordinator
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationLogic
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
    locationViewModel: LocationViewModel,
    tourNavigationCoordinator: TourNavigationCoordinator
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Get toursViewModel from DI
    val toursViewModel = Graph.getInstance().toursViewModel

    // Create MapViewModel
    val mapViewModel: MapViewModel = viewModel { MapViewModel(context) }

    // Create navigation logic handler
    val navigationLogic = remember { TourNavigationLogic(context) }

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
    val currentLocation by navigationLogic.currentLocation.collectAsState()
    val nextLocation by navigationLogic.nextLocation.collectAsState()
    val visitedLocations by navigationLogic.visitedLocations.collectAsState()
    val tourLocations by navigationLogic.tourLocations.collectAsState()
    val tourProgress by navigationLogic.tourProgress.collectAsState()
    val navigationState by navigationLogic.navigationState.collectAsState()

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
                val distance = navigationLogic.calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    targetLoc.latitude, targetLoc.longitude
                )
                navigationLogic.formatDistance(distance)
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

                        // Initialize navigation logic with tour data
                        navigationLogic.initializeWithTour(tourWithLocations, visitedIds)
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
        }
    }

    // Check if user has arrived at location
    LaunchedEffect(userLocationState, currentLocation, hasArrived) {
        val userLoc = userLocationState

        // Check if user has arrived at the destination
        if (userLoc != null && currentLocation != null && hasArrived) {
            // Mark current location as visited
            navigationLogic.markCurrentLocationAsVisited()

            // Mark location as visited in repository
            scope.launch {
                currentLocation?.let { location ->
                    Graph.getInstance().userProgressRepository.visitLocation(tourId, location.id)
                }
            }
        }
    }

    // Lifecycle management for map
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

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
                    // Progress Row - Simplified version
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0F7FA))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standort ${navigationLogic.getCurrentLocationIndex() + 1} von ${tourLocations.size}",
                            fontSize = 16.sp,
                            color = Color.Black
                        )

                        Text(
                            text = "${((visitedLocations.size.toFloat() / tourLocations.size) * 100).toInt()}% besucht",
                            fontSize = 16.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Direction info panel
                    Surface(
                        color = Color(0xFF009688),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Location info in one line
                            currentLocation?.let { location ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${location.order}. ${location.name} - $formattedDistance",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Direction text
                            Text(
                                text = if (navigationState == TourNavigationState.AtLocation)
                                    "Sie haben Ihr Ziel erreicht"
                                else navigationInstruction,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Distance remaining
                            Text(
                                text = remainingDistance,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                    }

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

                        // My location button
                        FloatingActionButton(
                            onClick = {
                                mapViewModel.setFollowUserLocation(true)
                                mapViewModel.centerOnUserLocation(mapView)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
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

                        // "Continue to next location" button (shown when at location)
                        if (navigationState == TourNavigationState.AtLocation) {
                            Button(
                                onClick = {
                                    navigationLogic.proceedToNextLocation()
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Weiter zum nächsten Standort")
                            }
                        }
                    }
                }
            }
        }
    }
}