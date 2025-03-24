package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.Map.Routing.DirectionMapHelpers
import com.example.havenspure_kotlin_prototype.Map.routing.OfflineDirectionMapHelpers
import com.example.havenspure_kotlin_prototype.Map.routing.OfflineRoutingService
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File

/**
 * Simplified offline direction map component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedDirectionMapComponent(
    userLocation: LocationData?,
    destinationLocation: LocationData,
    destinationName: String = "Ziel",
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val helpers = DirectionMapHelpers
    val offlineMapManager = remember { SimplifiedOfflineMapManager(context) }
    val scope = rememberCoroutineScope()

    // Get the routing service instance without explicit initialization
    val routingService = remember {
        OfflineRoutingService.getInstance(context)
    }

// Remove the separate initialization code if the getInstance method already handles initialization
    // State management
    val mapUpdateInProgress = remember { mutableStateOf(false) }
    val mapInitialized = remember { mutableStateOf(false) }
    val currentDirection = remember { mutableStateOf("Starten Sie Ihre Route") }
    val distanceRemaining = remember { mutableStateOf("") }
    val routePoints = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    val shouldFollowUser = remember { mutableStateOf(true) } // Default to following user
    val routeFetched = remember { mutableStateOf(false) }
    val mapLoadError = remember { mutableStateOf<String?>(null) }
    val routingInitialized = remember { mutableStateOf(false) }

    // Enhanced loading states
    val isLoading = remember { mutableStateOf(true) }
    val isInitialLoading = remember { mutableStateOf(true) }
    val isRerouting = remember { mutableStateOf(false) }

    // Calculate initial distance
    val initialDistance = if (userLocation != null) {
        helpers.calculateDistance(
            userLocation.latitude, userLocation.longitude,
            destinationLocation.latitude, destinationLocation.longitude
        )
    } else 0.0

    val formattedDistance = if (initialDistance > 0) {
        helpers.formatDistance(initialDistance)
    } else "Entfernung wird berechnet..."

    // Set initial values
    LaunchedEffect(Unit) {
        distanceRemaining.value = formattedDistance
    }

    // Background thread for map operations
    val handlerThread = remember { HandlerThread("OfflineDirectionMapThread").apply { start() } }
    val backgroundHandler = remember { Handler(handlerThread.looper) }

    // Map view with rotation enabled - with performance optimizations
    val mapView = remember {
        FixedMapView(context).apply {
            // Keep your existing setup
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            // Use the safer zoom setter
            setSafeZoomLevels(13.0, 17.0)

            // Default zoom - set to 16 to match available tiles
            controller.setZoom(16.0)
        }
    }

    // Initialize the routing service
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                // Initialize GraphHopper
                routingService.initialize()
                routingInitialized.value = true
                Log.d("SimplifiedMap", "GraphHopper routing initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMap", "Error initializing GraphHopper: ${e.message}")
        }
    }

    // Set up the map
    LaunchedEffect(mapView) {
        try {
            // Initialize OSMDroid configuration
            val config = Configuration.getInstance()
            config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
            config.userAgentValue = context.packageName

            // Set up cache directories
            val cacheDir = File(context.cacheDir, "osmdroid")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            config.osmdroidTileCache = cacheDir

            // Run diagnostics to help debug issues
            offlineMapManager.logTileDetails()

            Log.d("SimplifiedMap", "Map initialization started")

            // First reorganize tiles to the standard format
            withContext(Dispatchers.IO) {
                offlineMapManager.reorganizeTiles()
            }

            withContext(Dispatchers.Main) {
                // CRITICAL: Set use data connection to false first
                mapView.setUseDataConnection(false)

                // Setup map with simplified manager
                offlineMapManager.setupMapWithOfflineTiles(mapView)

                // Add debug info about available zoom levels
                Log.d(
                    "SimplifiedMap",
                    "Map initialized with zoom range: ${mapView.minZoomLevel} to ${mapView.maxZoomLevel}"
                )
                Log.d("SimplifiedMap", "Tiles directory: ${offlineMapManager.getTilesPath()}")

                // Force a refresh
                mapView.invalidate()

                // Mark initialization complete
                mapInitialized.value = true
                isLoading.value = false
                isInitialLoading.value = false
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMap", "Error initializing map: ${e.message}")
            withContext(Dispatchers.Main) {
                mapLoadError.value = "Fehler beim Laden der Karte: ${e.message}"
                isLoading.value = false
                isInitialLoading.value = false
            }
        }
    }

    // Add rotation gesture overlay
    val rotationGestureOverlay = remember { RotationGestureOverlay(mapView) }

    // Fetch and display the route immediately with fallback
    LaunchedEffect(Unit) {
        if (!routeFetched.value) {
            isLoading.value = true
            isInitialLoading.value = true
            Log.d("SimplifiedMap", "Initial route loading started")

            try {
                // Show fallback route immediately while initializing
                withContext(Dispatchers.Main) {
                    // Basic setup
                    rotationGestureOverlay.isEnabled = true
                    mapView.overlays.add(rotationGestureOverlay)

                    // Create points
                    val destinationPoint =
                        GeoPoint(destinationLocation.latitude, destinationLocation.longitude)
                    val userPoint = userLocation?.let { GeoPoint(it.latitude, it.longitude) }

                    // If user location is not available, create a simulated starting point near destination
                    val startPoint = userPoint ?: GeoPoint(
                        destinationLocation.latitude - 0.01, // About 1km away
                        destinationLocation.longitude - 0.01
                    )

                    mapView.controller.setCenter(startPoint)

                    // Add listener to detect when user moves the map
                    mapView.addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            shouldFollowUser.value = false
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            return false
                        }
                    })

                    // Try to use GraphHopper for routing if initialized
                    // When calculating initial route
                    val initialPoints = if (routingInitialized.value) {
                        try {
                            Log.d("SimplifiedMap", "Using GraphHopper for initial route")
                            // Use the new offline helper
                            OfflineDirectionMapHelpers.getGraphHopperRouteWithVisuals(
                                mapView,
                                startPoint,
                                destinationPoint,
                                Color(0xFF00CC00).toArgb(), // Bright green for route
                                Color(0xFF00AAFF).toArgb(), // Blue for user location
                                Color.Red.toArgb(), // Red for destination marker
                                context
                            )
                        } catch (e: Exception) {
                            Log.e(
                                "SimplifiedMap",
                                "GraphHopper route failed, using fallback: ${e.message}"
                            )
                            // Fallback to simple route if GraphHopper fails
                            helpers.forceStreetBasedRoute(
                                mapView,
                                startPoint,
                                destinationPoint,
                                Color(0xFF00CC00).toArgb(),
                                Color(0xFF00AAFF).toArgb(),
                                Color.Red.toArgb(),
                                context
                            )
                        }
                    } else {
                        // Use original method if GraphHopper not initialized
                        Log.d("SimplifiedMap", "GraphHopper not initialized, using fallback route")
                        helpers.forceStreetBasedRoute(
                            mapView,
                            startPoint,
                            destinationPoint,
                            Color(0xFF00CC00).toArgb(),
                            Color(0xFF00AAFF).toArgb(),
                            Color.Red.toArgb(),
                            context
                        )
                    }

                    // Update route points state
                    routePoints.value = initialPoints

                    // Calculate distance and direction
                    val quickDistance = helpers.calculateDistance(
                        startPoint.latitude, startPoint.longitude,
                        destinationPoint.latitude, destinationPoint.longitude
                    )
                    distanceRemaining.value = helpers.formatDistance(quickDistance)

                    // Try to get enhanced directions
                    val quickDirection = if (routingInitialized.value) {
                        try {
                            // Get instructions
                            val instructions = routingService.getInstructions(
                                LocationData(startPoint.latitude, startPoint.longitude).latitude,
                                LocationData(startPoint.latitude, startPoint.longitude).longitude,
                                destinationLocation.latitude,
                                destinationLocation.longitude,
                                "foot"
                            )

                            // Use the new offline helper to process instructions
                            OfflineDirectionMapHelpers.getGraphHopperNavigationInstruction(
                                LocationData(startPoint.latitude, startPoint.longitude),
                                initialPoints,
                                instructions,
                                context
                            )
                        } catch (e: Exception) {
                            helpers.getFormattedDirections(
                                LocationData(startPoint.latitude, startPoint.longitude),
                                initialPoints,
                                destinationLocation
                            )
                        }
                    } else {
                        helpers.getFormattedDirections(
                            LocationData(startPoint.latitude, startPoint.longitude),
                            initialPoints,
                            destinationLocation
                        )
                    }

                    currentDirection.value = quickDirection

                    mapInitialized.value = true
                    isLoading.value = false
                    isInitialLoading.value = false
                    routeFetched.value = true
                    Log.d("SimplifiedMap", "Initial route loading completed")
                }
            } catch (e: Exception) {
                Log.e("SimplifiedMap", "Error in route initialization: ${e.message}")
                withContext(Dispatchers.Main) {
                    mapLoadError.value = "Fehler bei der Routenberechnung: ${e.message}"
                    isLoading.value = false
                    isInitialLoading.value = false
                }
            }
        }
    }

    // Update map efficiently when user location changes
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null) {
            Log.d("SimplifiedMap", "User location update received: $userLocation")

            if (!mapUpdateInProgress.value) {
                mapUpdateInProgress.value = true

                // New user point
                val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

                try {
                    // Only recenter map if follow mode is active
                    if (shouldFollowUser.value) {
                        mapView.controller.animateTo(newUserPoint)
                    }

                    // Update just the user marker position for smoothness
                    withContext(Dispatchers.Main) {
                        try {
                            // Update user marker
                            helpers.updateUserMarkerPosition(
                                mapView,
                                newUserPoint,
                                Color(0xFF00AAFF).toArgb(), // Blue for user location
                                context
                            )

                            // Update distance and direction
                            val destinationPoint = GeoPoint(
                                destinationLocation.latitude,
                                destinationLocation.longitude
                            )
                            val newDistance = helpers.calculateDistance(
                                newUserPoint.latitude, newUserPoint.longitude,
                                destinationPoint.latitude, destinationPoint.longitude
                            )
                            distanceRemaining.value = helpers.formatDistance(newDistance)

                            // Try to get GraphHopper directions if available
                            currentDirection.value = if (routingInitialized.value) {
                                try {
                                    helpers.getGraphHopperDirections(
                                        userLocation,
                                        routePoints.value,
                                        destinationLocation,
                                        context
                                    )
                                } catch (e: Exception) {
                                    helpers.getFormattedDirections(
                                        userLocation,
                                        routePoints.value,
                                        destinationLocation
                                    )
                                }
                            } else {
                                helpers.getFormattedDirections(
                                    userLocation,
                                    routePoints.value,
                                    destinationLocation
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("SimplifiedMap", "Error updating marker: ${e.message}")
                        } finally {
                            mapUpdateInProgress.value = false
                        }
                    }
                } catch (e: Exception) {
                    mapUpdateInProgress.value = false
                    Log.e("SimplifiedMap", "Error in location update: ${e.message}")
                }
            }
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        mapView.onResume()
                        mapView.invalidate()
                    } catch (e: Exception) {
                        Log.e("SimplifiedMap", "Error resuming: ${e.message}")
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mapView.onPause()
                    } catch (e: Exception) {
                        Log.e("SimplifiedMap", "Error pausing: ${e.message}")
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        backgroundHandler.removeCallbacksAndMessages(null)
                        handlerThread.quitSafely()
                        mapView.onDetach()
                    } catch (e: Exception) {
                        Log.e("SimplifiedMap", "Error destroying: ${e.message}")
                    }
                }

                else -> { /* do nothing */
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
                backgroundHandler.removeCallbacksAndMessages(null)
                handlerThread.quitSafely()
                mapView.onDetach()
            } catch (e: Exception) {
                Log.e("SimplifiedMap", "Error disposing: ${e.message}")
            }
        }
    }

    // UI layout
    Column(modifier = Modifier.fillMaxSize()) {// Direction info
        Surface(
            color = Color(0xFF009688),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Current segment distance (extracted from direction if available)
                Text(
                    text = "(${
                        currentDirection.value.split(" ")
                            .lastOrNull { it.contains("km") } ?: "0.0 km"
                    })",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Distance remaining
                Text(
                    text = distanceRemaining.value,
                    color = Color.White,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Direction text (main instruction)
                Text(
                    text = currentDirection.value,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Offline indicator
                Text(
                    text = "Offline Navigation" + (if (routingInitialized.value) " mit GraphHopper" else ""),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

// Map view with navigation UI
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Map view
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Force redraw on each recomposition
                    view.invalidate()
                }
            )

            // Error message if map loading failed
            mapLoadError.value?.let { error ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = error,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Loading indicator
            if (isLoading.value) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF009688),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isInitialLoading.value) "Route wird geladen..."
                            else if (isRerouting.value) "Neue Route wird berechnet..."
                            else "Aktualisiere...",
                            color = Color(0xFF009688),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Current location button
            FloatingActionButton(
                onClick = {
                    if (userLocation != null) {
                        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

                        // First center the map
                        mapView.controller.animateTo(userGeoPoint)

                        // Then ensure the user marker is updated
                        helpers.updateUserMarkerPosition(
                            mapView,
                            userGeoPoint,
                            Color(0xFF00AAFF).toArgb(),
                            context
                        )

                        shouldFollowUser.value = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
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