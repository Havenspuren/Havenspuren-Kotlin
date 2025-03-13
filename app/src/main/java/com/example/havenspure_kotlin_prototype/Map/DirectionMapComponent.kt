package com.example.havenspure_kotlin_prototype.Map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.*

/**
 * DirectionMapComponent displays a turn-by-turn navigation map with all requested features:
 * - Fixed rotation icon for direction indicator
 * - Red destination marker
 * - Glowing blue user location
 * - Directional arrows along the route
 * - Current heading indicator
 *
 * @param userLocation Current user location
 * @param destinationLocation Destination location
 */
@Composable
fun DirectionMapComponent(
    userLocation: LocationData?,
    destinationLocation: LocationData
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Minimal configuration for performance
    val configuration = Configuration.getInstance().apply {
        load(context, PreferenceManager.getDefaultSharedPreferences(context))
        osmdroidTileCache = File(context.cacheDir, "osmdroid")
        userAgentValue = context.packageName
        osmdroidBasePath = context.filesDir

        tileDownloadThreads = 1
        tileFileSystemThreads = 1
        tileDownloadMaxQueueSize = 4
        tileFileSystemMaxQueueSize = 4
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
    val currentDirection = remember { mutableStateOf("Folgen Sie der Strecke") }
    val distanceRemaining = remember { mutableStateOf("") }
    val mapRotation = remember { mutableStateOf(0f) } // Store map rotation angle

    // Calculate initial distance
    val initialDistance = if (userLocation != null) {
        calculateDistance(
            userLocation.latitude, userLocation.longitude,
            destinationLocation.latitude, destinationLocation.longitude
        )
    } else 0.0

    val formattedDistance = if (initialDistance > 0) {
        formatDistance(initialDistance)
    } else "Entfernung wird berechnet..."

    // Direction text based on current and destination points
    val initialDirectionText = if (userLocation != null) {
        getDirectionText(userLocation, destinationLocation)
    } else "Starten Sie die Navigation"

    // Set initial values
    LaunchedEffect(Unit) {
        currentDirection.value = initialDirectionText
        distanceRemaining.value = formattedDistance
    }

    // Background thread for map operations
    val handlerThread = remember { HandlerThread("DirectionMapThread").apply { start() } }
    val backgroundHandler = remember { Handler(handlerThread.looper) }

    // Map view with performance settings
    val mapView = remember {
        MapView(context).apply {
            // Performance settings
            isTilesScaledToDpi = false
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false

            // Disable extras for performance
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // Set zoom levels
            minZoomLevel = 15.0
            maxZoomLevel = 19.0
        }
    }

    // Initialize map on first composition
    LaunchedEffect(Unit) {
        if (!mapInitialized.value) {
            try {
                // Basic setup
                mapView.setTileSource(TileSourceFactory.MAPNIK)

                // Set zoom level optimized for navigation
                mapView.controller.setZoom(17.0)

                // Center on user location or start point
                mapView.controller.setCenter(startPoint)

                // Delay to allow map to stabilize, then add route
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Initialize route and markers
                        addDirectionRoute(
                            mapView,
                            startPoint,
                            destinationPoint,
                            routeColor,
                            userLocationColor,
                            destinationColor,
                            context
                        )

                        mapInitialized.value = true
                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error adding route: ${e.message}")
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e("DirectionMap", "Error initializing map: ${e.message}")
            }
        }
    }

    // Update map when user location changes
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null && !mapUpdateInProgress.value) {
            mapUpdateInProgress.value = true

            // New user point
            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            try {
                // Update map center to follow user
                mapView.controller.animateTo(newUserPoint)

                // Calculate new bearing for map orientation
                val bearing = calculateBearing(
                    userLocation.latitude, userLocation.longitude,
                    destinationLocation.latitude, destinationLocation.longitude
                )

                // Store bearing for rotation indicator
                mapRotation.value = bearing.toFloat()

                // Update route display
                Handler(Looper.getMainLooper()).post {
                    try {
                        mapView.overlays.clear()
                        addDirectionRoute(
                            mapView,
                            newUserPoint,
                            destinationPoint,
                            routeColor,
                            userLocationColor,
                            destinationColor,
                            context
                        )

                        // Add direction arrows along route
                        addDirectionArrows(
                            mapView,
                            newUserPoint,
                            destinationPoint,
                            routeColor,
                            context
                        )

                        // Update direction and distance
                        val newDistance = calculateDistance(
                            userLocation.latitude, userLocation.longitude,
                            destinationLocation.latitude, destinationLocation.longitude
                        )

                        distanceRemaining.value = formatDistance(newDistance)
                        currentDirection.value = getDirectionText(userLocation, destinationLocation)

                    } catch (e: Exception) {
                        Log.e("DirectionMap", "Error updating route: ${e.message}")
                    } finally {
                        mapUpdateInProgress.value = false
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
        // Direction panel like your reference image (teal panel with white text)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = PrimaryColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Direction text (like "Gehen Sie nach Südosten")
                Text(
                    text = currentDirection.value,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Distance remaining
                Text(
                    text = distanceRemaining.value,
                    color = Color.White,
                    fontSize = 18.sp
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
                modifier = Modifier.fillMaxSize()
            )

            // Direction indicator in bottom left (green circle with arrow)
            // Now functional - rotates in the direction of travel
            if (userLocation != null) {
                FloatingActionButton(
                    onClick = {
                        // Toggle map rotation
                        if (mapView.mapOrientation == 0f) {
                            // Rotate map to match direction of travel
                            mapView.mapOrientation = mapRotation.value
                        } else {
                            // Reset to north-up orientation
                            mapView.mapOrientation = 0f
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(56.dp),
                    containerColor = Color(0xFF00CC00) // Bright green matching the route
                ) {
                    // Navigation arrow pointing in the direction of travel
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Karte drehen",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Current location button in bottom right (blue circle)
                FloatingActionButton(
                    onClick = {
                        if (!mapUpdateInProgress.value && userLocation != null) {
                            val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                            mapView.controller.animateTo(userGeoPoint, 17.0, 300L)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = PrimaryColor
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Auf meinen Standort zentrieren",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Add route line, direction arrows, and markers for the journey.
 */
private fun addDirectionRoute(
    mapView: MapView,
    startPoint: GeoPoint,
    endPoint: GeoPoint,
    routeColor: Int,
    userLocationColor: Int,
    destinationColor: Int,
    context: Context
) {
    // Destination marker with red color
    val destinationMarker = Marker(mapView).apply {
        position = endPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Create custom red pin
        val pinDrawable = createCustomPin(context, destinationColor)
        icon = pinDrawable

        title = "Ziel"
        // No snippet to keep UI clean for navigation
    }
    mapView.overlays.add(destinationMarker)

    // Main route line - thick green line like in navigation apps
    val routeLine = Polyline(mapView).apply {
        setPoints(listOf(startPoint, endPoint))
        outlinePaint.color = routeColor
        outlinePaint.strokeWidth = 10f // Thick line for visibility
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
    }
    mapView.overlays.add(routeLine)

    // Add glowing user location marker
    if (startPoint != null) {
        // Create glowing blue dot for user location
        val userMarker = Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // Use the glowing marker drawable
            icon = createGlowingLocationMarker(context, userLocationColor)

            title = null // No title for cleaner navigation view
            setInfoWindow(null) // No info window
        }
        mapView.overlays.add(userMarker)
    }

    // Add direction arrows along the route
    addDirectionArrows(mapView, startPoint, endPoint, routeColor, context)

    // Refresh the map
    mapView.invalidate()
}

/**
 * Add direction arrows along the route to indicate travel direction.
 */
private fun addDirectionArrows(
    mapView: MapView,
    startPoint: GeoPoint,
    endPoint: GeoPoint,
    arrowColor: Int,
    context: Context
) {
    // Calculate total distance and divide into segments
    val distance = calculateDistance(
        startPoint.latitude, startPoint.longitude,
        endPoint.latitude, endPoint.longitude
    )

    // Place arrows every X meters depending on distance
    val arrowSpacing = when {
        distance < 100 -> 20.0 // Close - show more arrows
        distance < 500 -> 50.0
        distance < 1000 -> 100.0
        distance < 5000 -> 500.0
        else -> 1000.0 // Far away - fewer arrows
    }

    // Calculate number of arrows (at least 1)
    val numArrows = max(1, (distance / arrowSpacing).toInt())

    if (numArrows > 1) {
        // Calculate points along the route for arrows
        for (i in 1 until numArrows) {
            val fraction = i.toDouble() / numArrows

            // Interpolate between start and end points
            val arrowLat = startPoint.latitude + fraction * (endPoint.latitude - startPoint.latitude)
            val arrowLon = startPoint.longitude + fraction * (endPoint.longitude - startPoint.longitude)

            // Create arrow marker
            val arrowPoint = GeoPoint(arrowLat, arrowLon)
            val arrowMarker = Marker(mapView).apply {
                position = arrowPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Calculate rotation angle for arrow
                val bearing = calculateBearing(
                    startPoint.latitude, startPoint.longitude,
                    endPoint.latitude, endPoint.longitude
                )

                // Set rotation angle
                rotation = bearing.toFloat()

                // Create small arrow icon
                icon = createDirectionArrow(context, arrowColor)

                // No info window or title
                setInfoWindow(null)
                title = null
            }

            mapView.overlays.add(arrowMarker)
        }
    }
}

/**
 * Create a glowing location marker for user position.
 */
private fun createGlowingLocationMarker(context: Context, color: Int): BitmapDrawable {
    val size = 48 // Size of the marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create paints for layers
    val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    outerGlowPaint.color = color
    outerGlowPaint.alpha = 40 // Very transparent
    outerGlowPaint.style = Paint.Style.FILL

    val middleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    middleGlowPaint.color = color
    middleGlowPaint.alpha = 80 // Semi-transparent
    middleGlowPaint.style = Paint.Style.FILL

    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    centerPaint.color = color
    centerPaint.alpha = 255 // Fully opaque
    centerPaint.style = Paint.Style.FILL

    // Draw outer glow
    canvas.drawCircle(size/2f, size/2f, size/2f, outerGlowPaint)

    // Draw middle glow
    canvas.drawCircle(size/2f, size/2f, size/3f, middleGlowPaint)

    // Draw center dot
    canvas.drawCircle(size/2f, size/2f, size/6f, centerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Create a custom pin marker for the destination.
 */
private fun createCustomPin(context: Context, color: Int): BitmapDrawable {
    val size = 72 // Size of the marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create paints
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    pinPaint.color = color
    pinPaint.style = Paint.Style.FILL

    val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    pinStrokePaint.color = AndroidColor.WHITE
    pinStrokePaint.style = Paint.Style.STROKE
    pinStrokePaint.strokeWidth = 3f

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    circlePaint.color = AndroidColor.WHITE
    circlePaint.style = Paint.Style.FILL

    // Create pin shape
    val path = Path()

    // Draw pin body (drop shape)
    val pinRadius = size * 0.3f
    val pinCenterX = size / 2f
    val pinCenterY = size / 4f

    path.addCircle(pinCenterX, pinCenterY, pinRadius, Path.Direction.CW)

    // Add pin point
    path.moveTo(pinCenterX - pinRadius / 2, pinCenterY + pinRadius * 0.8f)
    path.lineTo(pinCenterX, size * 0.75f) // Point of the pin
    path.lineTo(pinCenterX + pinRadius / 2, pinCenterY + pinRadius * 0.8f)
    path.close()

    // Draw pin with white outline
    canvas.drawPath(path, pinPaint)
    canvas.drawPath(path, pinStrokePaint)

    // Draw white circle in center of pin
    canvas.drawCircle(pinCenterX, pinCenterY, pinRadius * 0.5f, circlePaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Create a small arrow icon for direction indicators along the route.
 */
private fun createDirectionArrow(context: Context, color: Int): BitmapDrawable {
    val size = 24 // Small size for route arrows
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create paint for arrow
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    arrowPaint.color = color
    arrowPaint.style = Paint.Style.FILL
    arrowPaint.strokeWidth = 2f

    // Create arrow pointing up
    val path = Path()
    path.moveTo(size / 2f, 0f) // Top point
    path.lineTo(size.toFloat(), size.toFloat()) // Bottom right
    path.lineTo(size / 2f, size * 0.7f) // Middle bottom
    path.lineTo(0f, size.toFloat()) // Bottom left
    path.close()

    // Draw arrow
    canvas.drawPath(path, arrowPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Calculate distance between two points in meters.
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return earthRadius * c
}

/**
 * Format distance in a human-readable way.
 */
private fun formatDistance(distanceMeters: Double): String {
    return when {
        distanceMeters < 1000 -> "Entfernung: ${distanceMeters.toInt()} m"
        else -> "Entfernung: ${String.format("%.1f", distanceMeters / 1000)} km"
    }
}

/**
 * Get a human-readable direction based on coordinates.
 */
private fun getDirectionText(fromLocation: LocationData, toLocation: LocationData): String {
    // Calculate bearing between points
    val bearing = calculateBearing(
        fromLocation.latitude, fromLocation.longitude,
        toLocation.latitude, toLocation.longitude
    )

    // Convert bearing to cardinal direction
    return when {
        bearing > 337.5 || bearing <= 22.5 -> "Gehen Sie nach Norden"
        bearing > 22.5 && bearing <= 67.5 -> "Gehen Sie nach Nordosten"
        bearing > 67.5 && bearing <= 112.5 -> "Gehen Sie nach Osten"
        bearing > 112.5 && bearing <= 157.5 -> "Gehen Sie nach Südosten"
        bearing > 157.5 && bearing <= 202.5 -> "Gehen Sie nach Süden"
        bearing > 202.5 && bearing <= 247.5 -> "Gehen Sie nach Südwesten"
        bearing > 247.5 && bearing <= 292.5 -> "Gehen Sie nach Westen"
        bearing > 292.5 && bearing <= 337.5 -> "Gehen Sie nach Nordwesten"
        else -> "Folgen Sie der Strecke"
    }
}

/**
 * Calculate bearing between two points (0-360 degrees).
 */
private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val startLat = Math.toRadians(lat1)
    val startLng = Math.toRadians(lon1)
    val endLat = Math.toRadians(lat2)
    val endLng = Math.toRadians(lon2)

    val dLng = endLng - startLng

    val y = Math.sin(dLng) * Math.cos(endLat)
    val x = Math.cos(startLat) * Math.sin(endLat) -
            Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng)

    var bearing = Math.toDegrees(Math.atan2(y, x))
    if (bearing < 0) {
        bearing += 360
    }

    return bearing
}