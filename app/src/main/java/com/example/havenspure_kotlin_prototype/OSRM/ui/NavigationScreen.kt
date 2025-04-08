package com.example.havenspure_kotlin_prototype.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import org.osmdroid.views.MapView
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.api.IGeoPoint
/**
 * NavigationScreen displays a turn-by-turn navigation interface using the MapAssistant library.
 * This screen provides directions to help the user find a tour location
 * using actual street-based routing.
 *
 * @param tour The tour to navigate to
 * @param onBackClick Callback for the back button
 * @param locationViewModel ViewModel providing user location updates
 */
enum class NavigationUiState { LOADING, PARTIAL, READY }

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    tour: Tour,
    onBackClick: () -> Unit,
    locationViewModel: LocationViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create MapViewModel
    val mapViewModel: MapViewModel = viewModel { MapViewModel(context) }

    // Get location from LocationViewModel
    val userLocationState by locationViewModel.location

    // Observe states from MapViewModel
    val isNavigating by mapViewModel.isNavigating.collectAsState()
    val isLoading by mapViewModel.isLoading.collectAsState()
    val navigationInstruction by mapViewModel.navigationInstruction.collectAsState()
    val remainingDistance by mapViewModel.remainingDistance.collectAsState()
    val hasArrived by mapViewModel.hasArrived.collectAsState()
    val followUserLocation by mapViewModel.followUserLocation.collectAsState()
    val isCalculatingRoute by mapViewModel.isCalculatingRoute.collectAsState()

    // Get fully ready state directly from ViewModel
    val isFullyReady by mapViewModel.isFullyReady.collectAsState()

    // Define a single UI state enum

    // Use a single state for UI
    var uiState by remember { mutableStateOf(NavigationUiState.LOADING) }

    // Variables to track manual map movement
    var userMovedMap by remember { mutableStateOf(false) }
    var followTimer by remember { mutableStateOf<Job?>(null) }
    // Store the initial map center when user starts touching
    // Store the initial map center when user starts touching
    var initialMapCenter by remember { mutableStateOf<IGeoPoint?>(null) }
// Minimum distance in meters to consider as a "real" user movement
    val MIN_MOVEMENT_THRESHOLD = 100.0 // 100 meters threshold

// Make sure you have this import

    // Add this helper function to calculate distance between two IGeoPoints (in meters)
    fun calculateDistanceInMeters(point1: IGeoPoint, point2: IGeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val lon1Rad = Math.toRadians(point1.longitude)
        val lon2Rad = Math.toRadians(point2.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }


    // Update UI state based on combined conditions
    LaunchedEffect(isFullyReady, isLoading, isCalculatingRoute) {
        if (isFullyReady && !isLoading && !isCalculatingRoute) {
            // Small delay to ensure all UI elements are updated before showing map
            delay(100)
            uiState = NavigationUiState.READY
        } else {
            uiState = NavigationUiState.LOADING
        }
    }

    // Update UI state based on minimal readiness
    LaunchedEffect(mapViewModel.isMinimallyReady, mapViewModel.isLoading) {
        if (mapViewModel.isMinimallyReady.value && !mapViewModel.isLoading.value) {
            // Show map with just user location as soon as tiles are loaded
            if (uiState == NavigationUiState.LOADING) {
                uiState = NavigationUiState.PARTIAL
            }
        }
    }

    // Map visibility state with animation - tied to UI state
    // Map visibility state with animation - show partially in PARTIAL state
    val mapAlpha by animateFloatAsState(
        targetValue = when (uiState) {
            NavigationUiState.LOADING -> 0f
            NavigationUiState.PARTIAL -> 0.7f
            NavigationUiState.READY -> 1f
        },
        label = "mapAlpha"
    )

// Loading visibility state with animation
    val loadingAlpha by animateFloatAsState(
        targetValue = when (uiState) {
            NavigationUiState.LOADING -> 1f
            NavigationUiState.PARTIAL -> 0.3f
            NavigationUiState.READY -> 0f
        },
        label = "loadingAlpha"
    )

    // Ensure the tour has a location
    val tourLocation = tour.location ?: LocationData(53.5142, 8.1428) // Default to Wilhelmshaven harbor if null

    // Map reference
    val mapView = remember { MapView(context) }

    // Colors for the map
    val routeColor = PrimaryColor.toArgb()
    val userMarkerColor = Color.Blue.toArgb()
    val destinationMarkerColor = Color.Red.toArgb()

    // Function to start the follow timer
    // Function to start the follow timer
    fun startFollowTimer() {
        followTimer?.cancel()
        followTimer = lifecycleOwner.lifecycleScope.launch {
            delay(13000) // 13 seconds delay
            mapViewModel.setFollowUserLocation(true)
            // MODIFIED: Call centerOnUserLocation with preserveZoomAndRotation=true
            mapViewModel.centerOnUserLocation(mapView, preserveZoomAndRotation = true)
            userMovedMap = false
        }
    }

    // Initialize the map on first composition
    LaunchedEffect(Unit) {
        // Initialize the MapAssistant through the ViewModel
        mapViewModel.setColors(routeColor, userMarkerColor, destinationMarkerColor)
        mapViewModel.setupMapView(mapView)

        // Setup the tile loading listener to track readiness
        mapViewModel.setupMapTileLoadingListener(mapView)
    }

    // Update MapViewModel when location changes in LocationViewModel
    LaunchedEffect(userLocationState) {
        userLocationState?.let { location ->
            // Convert LocationData to LocationDataOSRM
            val locationOSRM = LocationDataOSRM(
                latitude = location.latitude,
                longitude = location.longitude
            )

            // Update user location in MapViewModel
            mapViewModel.updateUserLocation(locationOSRM)

            // Update navigation if we're already navigating
            if (isNavigating) {
                mapViewModel.updateNavigation(mapView)
            }
        }
    }

    // Start navigation when we have a user location
    LaunchedEffect(userLocationState, tour) {
        userLocationState?.let { location ->
            // Convert tour location to LocationDataOSRM
            val destinationLocation = LocationDataOSRM(
                latitude = tourLocation.latitude,
                longitude = tourLocation.longitude
            )

            // Set destination in MapViewModel
            mapViewModel.setDestinationLocation(destinationLocation)

            // Start navigation if we have both locations and not already navigating
            if (!isNavigating && !isCalculatingRoute) {
                mapViewModel.startNavigation(mapView)
            }
        }
    }

    // Lifecycle management for map
    // In the DisposableEffect, modify the cleanup logic:
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    // Check if we're already navigating
                    if (isNavigating) {
                        // Update navigation with current location
                        userLocationState?.let { location ->
                            val locationOSRM = LocationDataOSRM(
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                            mapViewModel.updateUserLocation(locationOSRM)
                            mapViewModel.updateNavigation(mapView)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Save state before pausing
                    if (isNavigating) {
                        mapViewModel.saveNavigationState()
                    }
                    mapView.onPause()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Save state before disposing
            if (isNavigating) {
                mapViewModel.saveNavigationState()
            }
            // Important: wait for any pending operations to complete before detaching
            lifecycleOwner.lifecycleScope.launch {
                delay(100) // Short delay to ensure operations complete
                mapView.onDetach()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation zu ${tour.title}", color = Color.White) },
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                        // Direction text
                        Text(
                            text = if (hasArrived) "Sie haben Ihr Ziel erreicht" else navigationInstruction,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Distance remaining
                        Text(
                            text = remainingDistance,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                }

                // Map container
                // Map container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Map view with animated alpha
                    AndroidView(
                        factory = {
                            // Setup the map touch listener
                            mapView.setOnTouchListener { view: View, event: MotionEvent ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        // User started touching the map
                                        if (followUserLocation) {
                                            // Store initial map center position
                                            initialMapCenter = mapView.mapCenter
                                            // Don't set userMovedMap yet - wait for ACTION_UP to determine if movement was significant
                                            mapViewModel.setFollowUserLocation(false)
                                            followTimer?.cancel()
                                        }
                                        false // Allow OSMDroid to handle the event
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        // User stopped touching the map
                                        initialMapCenter?.let { initialCenter ->
                                            // Calculate the distance between initial position and current position
                                            val currentCenter = mapView.mapCenter
                                            val distanceMoved = calculateDistanceInMeters(initialCenter, currentCenter)

                                            // Only consider it a user movement if distance exceeds threshold
                                            if (distanceMoved > MIN_MOVEMENT_THRESHOLD) {
                                                userMovedMap = true
                                                startFollowTimer() // Start the follow timer only if significant movement
                                            }

                                            // Clear the initial center
                                            initialMapCenter = null
                                        }
                                        false // Allow OSMDroid to handle the event
                                    }
                                    else -> false
                                }
                            }
                            mapView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(mapAlpha),
                        update = { view ->
                            // Map is updated through MapViewModel and LaunchedEffects
                        }
                    )

                    // Unified Loading Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(loadingAlpha)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryColor,
                                modifier = Modifier.size(56.dp),
                                strokeWidth = 4.dp
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
                            userLocationState?.let { userLocation ->
                                // When user explicitly clicks the button, we don't preserve zoom and rotation
                                // This allows them to reset to default view if desired
                                mapViewModel.setFollowUserLocation(true)
                                mapViewModel.centerOnUserLocation(mapView, preserveZoomAndRotation = false)
                            }
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
                }
            }
        }
    }
}



