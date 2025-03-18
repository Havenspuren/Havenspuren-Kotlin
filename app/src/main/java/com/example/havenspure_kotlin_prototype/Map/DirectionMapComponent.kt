package com.example.havenspure_kotlin_prototype.Map

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.Map.Routing.DirectionMapHelpers
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
import kotlin.math.max
import kotlin.math.min

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
    val helpers = DirectionMapHelpers

    // Initialize cache directory
    LaunchedEffect(Unit) {
        helpers.initializeCacheDir(context)
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
    val isLoading = remember { mutableStateOf(true) } // Start with loading state

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

    /**
     * Forces the map to use L-shaped routes
     */
    fun forceStreetBasedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routeColor: Int,
        userLocationColor: Int,
        destinationColor: Int,
        context: Context
    ) {
        // Create an L-shaped route directly
        val points = mutableListOf<GeoPoint>()
        points.add(startPoint)

        // Calculate if we should move horizontally or vertically first
        val latDifference = Math.abs(endPoint.latitude - startPoint.latitude)
        val lonDifference = Math.abs(endPoint.longitude - startPoint.longitude)

        if (latDifference > lonDifference) {
            // Move vertically first, then horizontally
            points.add(GeoPoint(endPoint.latitude, startPoint.longitude))
        } else {
            // Move horizontally first, then vertically
            points.add(GeoPoint(startPoint.latitude, endPoint.longitude))
        }

        points.add(endPoint)

        // Clear existing overlays but keep rotation
        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
        mapView.overlays.clear()
        mapView.overlays.addAll(existingOverlays)

        // Add the L-shaped route
        val helpers = DirectionMapHelpers
        helpers.addDirectionRoute(
            mapView,
            startPoint,
            endPoint,
            points,
            routeColor,
            userLocationColor,
            destinationColor,
            context
        )

        // Update route points state
        routePoints.value = points

        // Force map refresh
        mapView.invalidate()
    }

    // Fetch and display the route immediately with fallback
    LaunchedEffect(Unit) {
        if (!routeFetched.value) {
            isLoading.value = true

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
                forceStreetBasedRoute(
                    mapView,
                    startPoint,
                    destinationPoint,
                    routeColor,
                    userLocationColor,
                    destinationColor,
                    context
                )

                // Calculate distance and direction
                val quickDistance = helpers.calculateDistance(
                    startPoint.latitude, startPoint.longitude,
                    destinationPoint.latitude, destinationPoint.longitude
                )
                distanceRemaining.value = helpers.formatDistance(quickDistance)

                val quickDirection = helpers.getDirectionText(
                    LocationData(startPoint.latitude, startPoint.longitude),
                    LocationData(destinationPoint.latitude, destinationPoint.longitude)
                )
                currentDirection.value = quickDirection

                mapInitialized.value = true
                isLoading.value = false
            }

            // In parallel, fetch better route
            try {
                val routeResult = helpers.getRoute(
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
                        distanceRemaining.value = helpers.formatDistance(routeResult.second)
                        currentDirection.value = routeResult.third

                        // Update map with better route
                        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                        mapView.overlays.clear()
                        mapView.overlays.addAll(existingOverlays)

                        helpers.addDirectionRoute(
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
                    }
                } else {
                    // The fetched route is also a simple route, keep our L-shaped one
                    routeFetched.value = true
                }
            } catch (e: Exception) {
                Log.e("DirectionMap", "Error fetching route: ${e.message}")
                // Already displaying L-shaped route
                routeFetched.value = true
            }
        }
    }

    // Update map efficiently when user location changes
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null && !mapUpdateInProgress.value) {
            mapUpdateInProgress.value = true

            // New user point
            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            try {
                // Only recenter map if follow mode is active
                if (shouldFollowUser.value) {
                    mapView.controller.animateTo(newUserPoint)
                }

                // Check if we need to request a new route
                val shouldRefreshRoute = if (routePoints.value.isNotEmpty()) {
                    // Calculate distance to closest point on route
                    val closestPointDistance = routePoints.value.minOf { routePoint ->
                        helpers.calculateDistance(
                            newUserPoint.latitude, newUserPoint.longitude,
                            routePoint.latitude, routePoint.longitude
                        )
                    }

                    // If we're more than 50 meters from the route, recalculate
                    closestPointDistance > 50
                } else {
                    // No route exists yet, so we need to calculate one
                    true
                }

                if (shouldRefreshRoute) {
                    // Don't set loading state if we already have a route
                    if (routePoints.value.isEmpty()) {
                        isLoading.value = true
                    }

                    // Show immediate L-shaped route update with updated position
                    withContext(Dispatchers.Main) {
                        // Force L-shaped route with updated position
                        forceStreetBasedRoute(
                            mapView,
                            newUserPoint,
                            destinationPoint,
                            routeColor,
                            userLocationColor,
                            destinationColor,
                            context
                        )

                        // Update distance and direction
                        val newDistance = helpers.calculateDistance(
                            newUserPoint.latitude, newUserPoint.longitude,
                            destinationPoint.latitude, destinationPoint.longitude
                        )
                        distanceRemaining.value = helpers.formatDistance(newDistance)

                        val newDirection = helpers.getDirectionText(
                            LocationData(newUserPoint.latitude, newUserPoint.longitude),
                            LocationData(destinationPoint.latitude, destinationPoint.longitude)
                        )
                        currentDirection.value = newDirection

                        isLoading.value = false
                        mapUpdateInProgress.value = false
                    }
                } else {
                    // Just update user marker position without refetching the route
                    withContext(Dispatchers.Main) {
                        try {
                            // Preserve rotation overlay when clearing
                            val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                            mapView.overlays.clear()
                            mapView.overlays.addAll(existingOverlays)

                            // Re-add existing route
                            helpers.addDirectionRoute(
                                mapView,
                                newUserPoint,
                                destinationPoint,
                                routePoints.value,
                                routeColor,
                                userLocationColor,
                                destinationColor,
                                context
                            )

                            // Update distance to destination along route
                            // Find closest point on route to current location
                            var closestPointIndex = 0
                            var minDistance = Double.MAX_VALUE

                            routePoints.value.forEachIndexed { index, point ->
                                val dist = helpers.calculateDistance(
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
                                remainingDistance += helpers.calculateDistance(
                                    routePoints.value[i].latitude, routePoints.value[i].longitude,
                                    routePoints.value[i + 1].latitude, routePoints.value[i + 1].longitude
                                )
                            }

                            distanceRemaining.value = helpers.formatDistance(remainingDistance)

                            // Ensure map is refreshed without changing camera position
                            mapView.invalidate()
                        } finally {
                            mapUpdateInProgress.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                mapUpdateInProgress.value = false
                Log.e("DirectionMap", "Error in location update: ${e.message}")
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
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error resuming: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mapView.onPause()
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error pausing: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        backgroundHandler.removeCallbacksAndMessages(null)
                        mapView.onDetach()
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

            // Smaller loading indicator
            if (isLoading.value) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .size(60.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF009688),
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            // Current location button
            FloatingActionButton(
                onClick = {
                    if (!mapUpdateInProgress.value && userLocation != null) {
                        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                        mapView.controller.animateTo(userGeoPoint)
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