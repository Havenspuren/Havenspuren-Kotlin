package com.example.havenspure_kotlin_prototype.Map

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Enhanced DirectionMapComponent displays a turn-by-turn navigation map using OSRM for routing:
 * - Full 360-degree rotation of the map with multitouch
 * - Red destination marker
 * - Glowing blue user location
 * - Directional arrows along the route
 * - No automatic camera repositioning
 * - Fixed improper routing display
 *
 * @param userLocation Current user location
 * @param destinationLocation Destination location
 * @param destinationName Name of the destination to display in header
 * @param onBackPress Callback for handling back button press
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

    // Map view with rotation enabled
    val mapView = remember {
        MapView(context).apply {
            // Performance settings
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false

            // Disable extras for performance
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // Set zoom levels
            minZoomLevel = 12.0
            maxZoomLevel = 25.0

            // Default zoom for navigation
            controller.setZoom(17.0)
        }
    }

    // Add rotation gesture overlay - essential for 360-degree rotation
    val rotationGestureOverlay = remember { RotationGestureOverlay(mapView) }

    // Pre-fetch the route immediately on composition to reduce delay
    LaunchedEffect(Unit) {
        if (!routeFetched.value) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("DirectionMap", "Pre-fetching OSRM route...")
                    val routeResult = helpers.fetchOsrmRoute(
                        startPoint.longitude, startPoint.latitude,
                        destinationPoint.longitude, destinationPoint.latitude
                    )

                    if (routeResult != null && routeResult.first.isNotEmpty()) {
                        routePoints.value = routeResult.first
                        val routeDistance = routeResult.second

                        withContext(Dispatchers.Main) {
                            distanceRemaining.value = helpers.formatDistance(routeDistance)
                            if (routeResult.third.isNotEmpty()) {
                                currentDirection.value = routeResult.third
                            }
                            routeFetched.value = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DirectionMap", "Error pre-fetching route: ${e.message}")
                }
            }
        }
    }

    // Initialize map and fetch OSRM route on first composition
    LaunchedEffect(Unit) {
        if (!mapInitialized.value) {
            try {
                // Enable rotation
                rotationGestureOverlay.isEnabled = true
                mapView.overlays.add(rotationGestureOverlay)
                mapView.setMultiTouchControls(true)

                // Basic setup - use OpenStreetMap for more street details
                mapView.setTileSource(TileSourceFactory.MAPNIK)

                // Center on user location or start point for initial view ONLY on first load
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

                // Use the pre-fetched route if available, otherwise fetch it now
                if (routeFetched.value && routePoints.value.isNotEmpty()) {
                    // Preserve rotation overlay
                    val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                    mapView.overlays.clear()
                    mapView.overlays.addAll(existingOverlays)

                    // Add pre-fetched route
                    helpers.addDirectionRoute(
                        mapView,
                        startPoint,
                        destinationPoint,
                        routePoints.value,
                        routeColor,
                        userLocationColor,
                        destinationColor,
                        context
                    )
                    mapView.invalidate()
                    mapInitialized.value = true
                } else {
                    // Fetch OSRM route if not pre-fetched
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d("DirectionMap", "Fetching OSRM route...")

                            val routeResult = helpers.fetchOsrmRoute(
                                startPoint.longitude, startPoint.latitude,
                                destinationPoint.longitude, destinationPoint.latitude
                            )

                            if (routeResult != null && routeResult.first.isNotEmpty()) {
                                Log.d("DirectionMap", "OSRM route received with ${routeResult.first.size} points")
                                routePoints.value = routeResult.first

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    // Preserve rotation overlay
                                    val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                    mapView.overlays.clear()
                                    mapView.overlays.addAll(existingOverlays)

                                    // Add full OSRM route
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

                                    // Update distance from OSRM result
                                    val routeDistance = routeResult.second
                                    distanceRemaining.value = helpers.formatDistance(routeDistance)

                                    // Update initial direction
                                    if (routeResult.third.isNotEmpty()) {
                                        currentDirection.value = routeResult.third
                                    }

                                    mapView.invalidate()
                                    mapInitialized.value = true
                                    routeFetched.value = true
                                }
                            } else {
                                Log.e("DirectionMap", "OSRM route failed or returned empty")

                                // Try fetching street data directly as fallback
                                val streetPoints = helpers.fetchAllStreets(
                                    startPoint.longitude, startPoint.latitude,
                                    destinationPoint.longitude, destinationPoint.latitude
                                )

                                if (streetPoints.isNotEmpty()) {
                                    Log.d("DirectionMap", "Using street data with ${streetPoints.size} points")
                                    routePoints.value = streetPoints

                                    withContext(Dispatchers.Main) {
                                        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                        mapView.overlays.clear()
                                        mapView.overlays.addAll(existingOverlays)

                                        helpers.addDirectionRoute(
                                            mapView,
                                            startPoint,
                                            destinationPoint,
                                            streetPoints,
                                            routeColor,
                                            userLocationColor,
                                            destinationColor,
                                            context
                                        )

                                        mapInitialized.value = true
                                        routeFetched.value = true
                                    }
                                } else {
                                    // Last resort - direct route
                                    Log.d("DirectionMap", "Falling back to direct route")
                                    withContext(Dispatchers.Main) {
                                        val directRoute = helpers.createDirectRoute(startPoint, destinationPoint)
                                        routePoints.value = directRoute

                                        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                        mapView.overlays.clear()
                                        mapView.overlays.addAll(existingOverlays)

                                        helpers.addDirectionRoute(
                                            mapView,
                                            startPoint,
                                            destinationPoint,
                                            directRoute,
                                            routeColor,
                                            userLocationColor,
                                            destinationColor,
                                            context
                                        )

                                        mapInitialized.value = true
                                        routeFetched.value = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DirectionMap", "Error fetching OSRM route: ${e.message}")

                            // Fallback to direct route if OSRM fails
                            withContext(Dispatchers.Main) {
                                val directRoute = helpers.createDirectRoute(startPoint, destinationPoint)
                                routePoints.value = directRoute

                                val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                mapView.overlays.clear()
                                mapView.overlays.addAll(existingOverlays)

                                helpers.addDirectionRoute(
                                    mapView,
                                    startPoint,
                                    destinationPoint,
                                    directRoute,
                                    routeColor,
                                    userLocationColor,
                                    destinationColor,
                                    context
                                )

                                mapInitialized.value = true
                                routeFetched.value = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DirectionMap", "Error initializing map: ${e.message}")
            }
        }
    }

    // Update map when user location changes - but don't recenter unless explicitly requested
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null && !mapUpdateInProgress.value) {
            mapUpdateInProgress.value = true

            // New user point
            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            try {
                // Only recenter map if follow mode is active - this prevents automatic repositioning
                if (shouldFollowUser.value) {
                    mapView.controller.animateTo(newUserPoint)
                }

                // Check if we need to request a new route from OSRM
                // (if we've deviated significantly from the route)
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
                    // Fetch new route in background
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val routeResult = helpers.fetchOsrmRoute(
                                newUserPoint.longitude, newUserPoint.latitude,
                                destinationPoint.longitude, destinationPoint.latitude
                            )

                            if (routeResult != null && routeResult.first.isNotEmpty()) {
                                routePoints.value = routeResult.first

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    try {
                                        // Preserve rotation overlay
                                        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
                                        mapView.overlays.clear()
                                        mapView.overlays.addAll(existingOverlays)

                                        // Add new route with OSRM path
                                        helpers.addDirectionRoute(
                                            mapView,
                                            newUserPoint,
                                            destinationPoint,
                                            routeResult.first,
                                            routeColor,
                                            userLocationColor,
                                            destinationColor,
                                            context
                                        )

                                        // Update direction and distance from OSRM
                                        distanceRemaining.value = helpers.formatDistance(routeResult.second)
                                        currentDirection.value = routeResult.third

                                        mapView.invalidate()
                                    } finally {
                                        mapUpdateInProgress.value = false
                                    }
                                }
                            } else {
                                // Fallback to simple direction if OSRM fails
                                withContext(Dispatchers.Main) {
                                    try {
                                        val newDistance = helpers.calculateDistance(
                                            userLocation.latitude, userLocation.longitude,
                                            destinationLocation.latitude, destinationLocation.longitude
                                        )

                                        distanceRemaining.value = helpers.formatDistance(newDistance)
                                        currentDirection.value = helpers.getDirectionText(userLocation, destinationLocation)
                                    } finally {
                                        mapUpdateInProgress.value = false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DirectionMap", "Error fetching route update: ${e.message}")
                            withContext(Dispatchers.Main) {
                                mapUpdateInProgress.value = false
                            }
                        }
                    }
                } else {
                    // Just update user marker position without refetching the route or recentering
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

    // UI layout - REMOVED redundant title bar with back button
    Column(modifier = Modifier.fillMaxSize()) {

        // Direction info directly (NO redundant title bar)
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

            // Current location button in bottom right (blue circle)
            FloatingActionButton(
                onClick = {
                    if (!mapUpdateInProgress.value && userLocation != null) {
                        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                        // Center on user location when button is pressed
                        mapView.controller.animateTo(userGeoPoint)
                        // Set follow mode to true to maintain centering until user moves map
                        shouldFollowUser.value = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
                containerColor = Color(0xFF03A9F4) // Light blue color as in screenshot
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