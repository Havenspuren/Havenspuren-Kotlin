package com.example.havenspure_kotlin_prototype.Map
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import com.example.havenspure_kotlin_prototype.Map.Routing.MarkerCreationHelpers
import com.example.havenspure_kotlin_prototype.Map.Routing.RoutingHelpers
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File

/**
 * Optimized DirectionMapComponent with faster loading and better performance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionMapComponent(
    userLocation: LocationData?,
    destinationLocation: LocationData,
    destinationName: String = "Ziel",
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routingManager = RoutingGraph
    val routingHelpers = RoutingHelpers
    val markerHelpers = MarkerCreationHelpers



    // Initialize cache directory
    LaunchedEffect(Unit) {
        routingManager.initializeCacheDir(context)
    }

    // Minimal configuration for performance
    val configuration = Configuration.getInstance().apply {
        load(context, PreferenceManager.getDefaultSharedPreferences(context))
        osmdroidTileCache = File(context.cacheDir, "osmdroid")
        userAgentValue = context.packageName
        osmdroidBasePath = context.filesDir

        // Improved performance settings
        tileDownloadThreads = 2
        tileFileSystemThreads = 2
        tileDownloadMaxQueueSize = 8
        tileFileSystemMaxQueueSize = 8
        expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30 // Cache for a month
    }

    // Colors for map elements
    val routeColor = Color(0xFF00CC00).toArgb() // Bright green for route
    val destinationColor = Color.Red.toArgb() // Red for destination marker
    val userLocationColor = Color(0xFF00AAFF).toArgb() // Blue for user location

    // Create points
    val destinationPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)
    val userPoint = userLocation?.let { GeoPoint(it.latitude, it.longitude) }

    // If user location is not available, create a simulated starting point
    val startPoint = userPoint ?: GeoPoint(
        destinationLocation.latitude - 0.01, // About 1km away
        destinationLocation.longitude - 0.01
    )

    // State management
    val mapUpdateInProgress = remember { mutableStateOf(false) }
    val mapInitialized = remember { mutableStateOf(false) }
    val currentDirection = remember { mutableStateOf("Starten Sie Ihre Route") }
    val distanceRemaining = remember { mutableStateOf("") }
    val routePoints = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    val shouldFollowUser = remember { mutableStateOf(false) }
    val routeFetched = remember { mutableStateOf(false) }

    // Enhanced loading states
    val isLoading = remember { mutableStateOf(true) } // Start with loading state
    val isInitialLoading = remember { mutableStateOf(true) } // Track initial loading
    val isRerouting = remember { mutableStateOf(false) } // Track rerouting state

    // Calculate initial distance
    val initialDistance = if (userLocation != null) {
        routingHelpers.calculateDistance(
            userLocation.latitude, userLocation.longitude,
            destinationLocation.latitude, destinationLocation.longitude
        )
    } else 0.0

    val formattedDistance = if (initialDistance > 0) {
        routingHelpers.formatDistance(initialDistance)
    } else "Entfernung wird berechnet..."

    // Set initial values
    LaunchedEffect(Unit) {
        distanceRemaining.value = formattedDistance
    }

    // Background thread for map operations
    val handlerThread = remember { HandlerThread("DirectionMapThread").apply { start() } }
    val backgroundHandler = remember { Handler(handlerThread.looper) }

    // Map view with rotation enabled - with performance optimizations
    val mapView = remember {
        MapView(context).apply {
            // Performance settings
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setUseDataConnection(true) // Allow network tiles but with caching

            // Set hardware acceleration
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // Disable extras for performance
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            // Set zoom levels - more constrained for better performance
            minZoomLevel = 12.0
            maxZoomLevel = 19.0 // Reduced max zoom for better performance

            // Default zoom
            controller.setZoom(17.0)
        }
    }

    // Add rotation gesture overlay
    val rotationGestureOverlay = remember { RotationGestureOverlay(mapView) }

    // Fetch and display the route immediately with fallback
    LaunchedEffect(Unit) {
        if (!routeFetched.value) {
            isLoading.value = true
            isInitialLoading.value = true
            Log.d("DirectionMap", "Initial route loading started")

            // Show fallback route immediately while fetching proper route
            withContext(Dispatchers.Main) {
                // Basic setup
                rotationGestureOverlay.isEnabled = true
                mapView.overlays.add(rotationGestureOverlay)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
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

                // Force L-shaped route immediately
                val initialPoints = routingManager.forceStreetBasedRoute(
                    mapView,
                    startPoint,
                    destinationPoint,
                    routeColor,
                    userLocationColor,
                    destinationColor,
                    context
                )

                // Update route points state
                routePoints.value = initialPoints

                // Calculate distance and direction
                val quickDistance = routingHelpers.calculateDistance(
                    startPoint.latitude, startPoint.longitude,
                    destinationPoint.latitude, destinationPoint.longitude
                )
                distanceRemaining.value = routingHelpers.formatDistance(quickDistance)

                // Use enhanced directions even for initial route
                val quickDirection = routingHelpers.getFormattedDirections(
                    LocationData(startPoint.latitude, startPoint.longitude),
                    initialPoints,
                    destinationLocation
                )
                currentDirection.value = quickDirection

                mapInitialized.value = true
            }

            // In parallel, fetch better route
            try {
                val routeResult = routingManager.getRoute(
                    startPoint.latitude, startPoint.longitude,
                    destinationPoint.latitude, destinationPoint.longitude,
                    context
                )

                // Only update if the fetched route has more than 3 points (indicating it's from OSRM)
                // Otherwise keep our L-shaped route
                if (routeResult.first.size > 3) {
                    // Update with better route if successful
                    withContext(Dispatchers.Main) {
                        routePoints.value = routeResult.first
                        distanceRemaining.value = routingHelpers.formatDistance(routeResult.second)

                        // Use the enhanced directions
                        currentDirection.value = routingHelpers.getFormattedDirections(
                            LocationData(startPoint.latitude, startPoint.longitude),
                            routeResult.first,
                            destinationLocation
                        )

                        // Update map with better route
                        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                        mapView.overlays.clear()
                        mapView.overlays.addAll(existingOverlays)

                        routingManager.addDirectionRoute(
                            mapView,
                            startPoint,
                            destinationPoint,
                            routeResult.first,
                            routeColor,
                            userLocationColor,
                            destinationColor,
                            context
                        )

                        routeFetched.value = true
                        Log.d("DirectionMap", "Better route loaded and displayed")
                    }
                } else {
                    // The fetched route is also a simple route, keep our L-shaped one
                    routeFetched.value = true
                    Log.d("DirectionMap", "Using L-shaped route (no better route available)")
                }
            } catch (e: Exception) {
                Log.e("DirectionMap", "Error fetching route: ${e.message}")
                // Already displaying L-shaped route
                routeFetched.value = true
            } finally {
                // Finish loading regardless of the outcome
                isLoading.value = false
                isInitialLoading.value = false
                Log.d("DirectionMap", "Initial route loading completed")
            }
        }
    }

    // Update map efficiently when user location changes
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null) {
            Log.d("DirectionMap", "User location update received: $userLocation")

            if (!mapUpdateInProgress.value) {
                mapUpdateInProgress.value = true

                // New user point
                val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

                try {
                    // Only recenter map if follow mode is active
                    if (shouldFollowUser.value) {
                        mapView.controller.animateTo(newUserPoint)
                        Log.d("DirectionMap", "Following user - map centered")
                    }

                    // Check if we need to request a new route
                    val shouldRefreshRoute = if (routePoints.value.isNotEmpty()) {
                        // Calculate distance to closest point on route
                        val closestPointDistance = routePoints.value.minOf { routePoint ->
                            routingHelpers.calculateDistance(
                                newUserPoint.latitude, newUserPoint.longitude,
                                routePoint.latitude, routePoint.longitude
                            )
                        }

                        // If we're more than 30 meters from the route, recalculate
                        val isOffRoute = closestPointDistance > 30

                        // Set rerouting state if user is off route
                        if (isOffRoute && !isRerouting.value) {
                            isRerouting.value = true
                            isLoading.value = true
                            Log.d("DirectionMap", "User is off route (${closestPointDistance.toInt()}m), rerouting...")
                        }

                        isOffRoute
                    } else {
                        // No route exists yet, so we need to calculate one
                        true
                    }

                    if (shouldRefreshRoute) {
                        // Show immediate L-shaped route update with updated position
                        withContext(Dispatchers.Main) {
                            try {
                                // Force L-shaped route with updated position
                                val newPoints = routingManager.forceStreetBasedRoute(
                                    mapView,
                                    newUserPoint,
                                    destinationPoint,
                                    routeColor,
                                    userLocationColor,
                                    destinationColor,
                                    context
                                )

                                // Update route points
                                routePoints.value = newPoints

                                // Update distance and direction
                                val newDistance = routingHelpers.calculateDistance(
                                    newUserPoint.latitude, newUserPoint.longitude,
                                    destinationPoint.latitude, destinationPoint.longitude
                                )
                                distanceRemaining.value = routingHelpers.formatDistance(newDistance)

                                // Use enhanced directions
                                val newDirection = routingHelpers.getFormattedDirections(
                                    LocationData(newUserPoint.latitude, newUserPoint.longitude),
                                    newPoints,
                                    destinationLocation
                                )
                                currentDirection.value = newDirection

                                // Try to get a better route in the background
                                launch {
                                    try {
                                        val betterRoute = routingManager.getRoute(
                                            newUserPoint.latitude, newUserPoint.longitude,
                                            destinationPoint.latitude, destinationPoint.longitude,
                                            context
                                        )

                                        // Only update if the fetched route has more than 3 points
                                        if (betterRoute.first.size > 3) {
                                            routePoints.value = betterRoute.first
                                            distanceRemaining.value = routingHelpers.formatDistance(betterRoute.second)

                                            // Use enhanced directions
                                            currentDirection.value = routingHelpers.getFormattedDirections(
                                                LocationData(newUserPoint.latitude, newUserPoint.longitude),
                                                betterRoute.first,
                                                destinationLocation
                                            )

                                            // Update map with better route
                                            val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                            mapView.overlays.clear()
                                            mapView.overlays.addAll(existingOverlays)

                                            routingManager.addDirectionRoute(
                                                mapView,
                                                newUserPoint,
                                                destinationPoint,
                                                betterRoute.first,
                                                routeColor,
                                                userLocationColor,
                                                destinationColor,
                                                context
                                            )

                                            Log.d("DirectionMap", "Rerouting complete - better route applied")
                                        } else {
                                            Log.d("DirectionMap", "Rerouting complete - keeping L-shaped route")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DirectionMap", "Error fetching better route: ${e.message}")
                                        // Keep the L-shaped route if fetching fails
                                    } finally {
                                        isRerouting.value = false
                                        isLoading.value = false
                                        mapUpdateInProgress.value = false
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("DirectionMap", "Error in route update: ${e.message}")
                                isRerouting.value = false
                                isLoading.value = false
                                mapUpdateInProgress.value = false
                            }
                        }
                    } else {
                        // Just update user marker position without refetching the route
                        withContext(Dispatchers.Main) {
                            try {
                                // Update just the user marker position
                                markerHelpers.updateUserMarkerPosition(
                                    mapView,
                                    newUserPoint,
                                    userLocationColor,
                                    context
                                )

                                // Update distance to destination along route
                                // Find closest point on route to current location
                                var closestPointIndex = 0
                                var minDistance = Double.MAX_VALUE

                                routePoints.value.forEachIndexed { index, point ->
                                    val dist = routingHelpers.calculateDistance(
                                        newUserPoint.latitude, newUserPoint.longitude,
                                        point.latitude, point.longitude
                                    )
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestPointIndex = index
                                    }
                                }

                                // Calculate remaining distance along route
                                var remainingDistance = 0.0
                                for (i in closestPointIndex until routePoints.value.size - 1) {
                                    remainingDistance += routingHelpers.calculateDistance(
                                        routePoints.value[i].latitude, routePoints.value[i].longitude,
                                        routePoints.value[i + 1].latitude, routePoints.value[i + 1].longitude
                                    )
                                }

                                distanceRemaining.value = routingHelpers.formatDistance(remainingDistance)

                                // Update direction guidance using enhanced directions
                                val updatedDirection = routingHelpers.getFormattedDirections(
                                    LocationData(newUserPoint.latitude, newUserPoint.longitude),
                                    routePoints.value,
                                    destinationLocation
                                )
                                currentDirection.value = updatedDirection

                                Log.d("DirectionMap", "User marker position updated, staying on route")
                            } finally {
                                mapUpdateInProgress.value = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    mapUpdateInProgress.value = false
                    Log.e("DirectionMap", "Error in location update: ${e.message}")
                }
            } else {
                Log.d("DirectionMap", "Skipping location update - update already in progress")
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
                        Log.d("DirectionMap", "Map resumed")
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error resuming: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mapView.onPause()
                        Log.d("DirectionMap", "Map paused")
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error pausing: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        backgroundHandler.removeCallbacksAndMessages(null)
                        mapView.onDetach()
                        Log.d("DirectionMap", "Map destroyed")
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error destroying: ${e.message}")
                    }
                }
                else -> { /* do nothing */ }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
                backgroundHandler.removeCallbacksAndMessages(null)
                handlerThread.quitSafely()
                mapView.onDetach()
                Log.d("DirectionMap", "Map component disposed")
            } catch (e: Exception) {
                Log.e("DirectionMap", "Error disposing: ${e.message}")
            }
        }
    }

    // UI layout
    Column(modifier = Modifier.fillMaxSize()) {
        // Direction info
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
                    text = currentDirection.value,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Distance remaining
                Text(
                    text = distanceRemaining.value,
                    color = Color.White,
                    fontSize = 20.sp
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
                    // Empty update to avoid recreation
                }
            )

            // Enhanced loading indicator with text
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

            // Current location button - Enhanced with more reliable behavior
            FloatingActionButton(
                onClick = {
                    if (userLocation != null) {
                        Log.d("DirectionMap", "Location button clicked, user location: $userLocation")

                        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

                        // First center the map
                        mapView.controller.animateTo(userGeoPoint)

                        // Then ensure the user marker is updated
                        markerHelpers.updateUserMarkerPosition(
                            mapView,
                            userGeoPoint,
                            userLocationColor,
                            context
                        )

                        shouldFollowUser.value = true

                        // Update user position immediately if needed
                        if (!mapUpdateInProgress.value && routePoints.value.isNotEmpty()) {
                            // Calculate if we're off-route
                            val closestPointDistance = routePoints.value.minOf { routePoint ->
                                routingHelpers.calculateDistance(
                                    userGeoPoint.latitude, userGeoPoint.longitude,
                                    routePoint.latitude, routePoint.longitude
                                )
                            }

                            // If off-route, initiate rerouting
                            if (closestPointDistance > 30 && !isRerouting.value) {
                                isRerouting.value = true
                                isLoading.value = true

                                // This will trigger the LaunchedEffect(userLocation) and recalculate the route
                                Log.d("DirectionMap", "Initiating reroute from location button")
                            }
                        }
                    } else {
                        Log.d("DirectionMap", "Cannot center: userLocation is null")
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