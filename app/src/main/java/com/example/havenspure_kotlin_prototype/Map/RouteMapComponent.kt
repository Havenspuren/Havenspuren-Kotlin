package com.example.havenspure_kotlin_prototype.Map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.data.LocationData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.*

/**
 * An ultra-lightweight map component designed for maximum stability.
 * This component prioritizes app stability over feature richness to prevent ANRs.
 *
 * @param userLocation The user's current location (may be null)
 * @param destinationLocations List of destination locations to plot on the map
 */
@Composable
fun StableRouteMapComponent(userLocation: LocationData?, destinationLocations: List<LocationData>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Minimal configuration to avoid heavy processing
    val configuration = Configuration.getInstance().apply {
        load(context, PreferenceManager.getDefaultSharedPreferences(context))
        osmdroidTileCache = File(context.cacheDir, "osmdroid")
        userAgentValue = context.packageName
        osmdroidBasePath = context.filesDir

        // Extreme performance settings
        tileDownloadThreads = 1
        tileFileSystemThreads = 1
        tileDownloadMaxQueueSize = 2
        tileFileSystemMaxQueueSize = 2
        expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30 // Cache for a month
    }

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val routeColor = Color(0xFF00CC00).toArgb() // Green route
    val destinationColor = Color.Red.toArgb() // Red destination marker

    // Create points
    val destinationPoints = destinationLocations.map {
        GeoPoint(it.latitude, it.longitude)
    }
    val userPoint = userLocation?.let { GeoPoint(it.latitude, it.longitude) }

    // Flag to prevent concurrent map operations
    val mapUpdateInProgress = remember { mutableStateOf(false) }
    val mapInitialized = remember { mutableStateOf(false) }
    val isFocusedOnUser = remember { mutableStateOf(false) }

    // Map update handler thread
    val handlerThread = remember { HandlerThread("MapUpdateThread").apply { start() } }
    val backgroundHandler = remember { Handler(handlerThread.looper) }

    // Remember map view
    val mapView = remember {
        MapView(context).apply {
            // Minimal performance settings
            isTilesScaledToDpi = false
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false

            // Disable all extras
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // Set very limited zoom
            minZoomLevel = 10.0
            maxZoomLevel = 17.0
        }
    }

    // Initialize map only once
    LaunchedEffect(Unit) {
        if (!mapInitialized.value && destinationPoints.isNotEmpty()) {
            try {
                // Basic setup on main thread
                mapView.setTileSource(TileSourceFactory.MAPNIK)

                // Set initial center and zoom
                val initialZoom = 15.0
                mapView.controller.setZoom(initialZoom)

                // Set initial center to first destination if no user location or user location
                val initialCenter = userPoint ?: destinationPoints.first()
                mapView.controller.setCenter(initialCenter)

                // Only do the rest after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Calculate a bounding box that contains all points
                        if (destinationPoints.isNotEmpty()) {
                            val points = destinationPoints.toMutableList()
                            if (userPoint != null) points.add(userPoint)

                            if (points.size > 1) {
                                // Calculate bounding box
                                val latitudes = points.map { it.latitude }
                                val longitudes = points.map { it.longitude }

                                val north = latitudes.maxOrNull()!! + 0.01
                                val south = latitudes.minOrNull()!! - 0.01
                                val east = longitudes.maxOrNull()!! + 0.01
                                val west = longitudes.minOrNull()!! - 0.01

                                val box = BoundingBox(north, east, south, west)
                                mapView.zoomToBoundingBox(box, true, 100)
                            }
                        }

                        // Add markers and route after a delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                addMarkersAndRoute(mapView, userPoint, destinationPoints, routeColor, destinationColor, context)
                                mapInitialized.value = true
                            } catch (e: Exception) {
                                Log.e("StableMap", "Error adding markers: ${e.message}")
                            }
                        }, 500)
                    } catch (e: Exception) {
                        Log.e("StableMap", "Error setting bounding box: ${e.message}")
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e("StableMap", "Error initializing map: ${e.message}")
            }
        }

        // Add zoom listener to update marker positions when zoom changes
        // Add zoom listener to update marker positions when zoom changes
        var lastZoomLevel = mapView.zoomLevelDouble
        // Add zoom listener to update marker positions when zoom changes
        mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            // Use the proper method to add a MapListener
            mapView.addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    return true
                }

                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    // Only update if zoom level has actually changed
                    if (lastZoomLevel != mapView.zoomLevelDouble && !mapUpdateInProgress.value) {
                        lastZoomLevel = mapView.zoomLevelDouble
                        mapUpdateInProgress.value = true

                        // Refresh markers on zoom change to update offsets
                        Handler(Looper.getMainLooper()).post {
                            try {
                                mapView.overlays.clear()
                                addMarkersAndRoute(mapView, userPoint, destinationPoints, routeColor, destinationColor, context)
                            } catch (e: Exception) {
                                Log.e("StableMap", "Error updating markers on zoom: ${e.message}")
                            } finally {
                                mapUpdateInProgress.value = false
                            }
                        }
                    }
                    return true
                }
            })
        }
    }

    // Update user location marker and route when user position changes significantly
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null && !mapUpdateInProgress.value && destinationPoints.isNotEmpty()) {
            mapUpdateInProgress.value = true

            // New user point
            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            // Move center if focused on user
            if (isFocusedOnUser.value) {
                mapView.controller.animateTo(newUserPoint)
            }

            // Update markers and route on background thread
            backgroundHandler.post {
                try {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            // Very simple update - just recreate everything
                            // This is less efficient but much more stable
                            mapView.overlays.clear()
                            addMarkersAndRoute(mapView, newUserPoint, destinationPoints, routeColor, destinationColor, context)
                        } catch (e: Exception) {
                            Log.e("StableMap", "Error updating markers: ${e.message}")
                        } finally {
                            mapUpdateInProgress.value = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StableMap", "Error in background handler: ${e.message}")
                    mapUpdateInProgress.value = false
                }
            }
        }
    }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        mapView.onResume()
                    } catch (e: Exception) {
                        Log.e("StableMap", "Error resuming map: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mapView.onPause()
                    } catch (e: Exception) {
                        Log.e("StableMap", "Error pausing map: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        backgroundHandler.removeCallbacksAndMessages(null)
                        mapView.onDetach()
                    } catch (e: Exception) {
                        Log.e("StableMap", "Error detaching map: ${e.message}")
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
                Log.e("StableMap", "Error disposing map: ${e.message}")
            }
        }
    }

    // Render map view with container for FAB
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { /* No updates here to avoid ANR */ }
        )

        // Show "center on me" button if we have user location
        if (userLocation != null) {
            FloatingActionButton(
                onClick = {
                    if (!mapUpdateInProgress.value) {
                        try {
                            // Simply center on user location
                            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                            mapView.controller.animateTo(newUserPoint, 16.0, 300L)
                            isFocusedOnUser.value = true
                        } catch (e: Exception) {
                            Log.e("StableMap", "Error centering on user: ${e.message}")
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .shadow(4.dp, CircleShape),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on my location",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Calculate offset for markers based on zoom level and proximity
 */
private fun calculateMarkerOffset(
    mapView: MapView,
    markerIndex: Int,
    totalMarkers: Int,
    point: GeoPoint,
    otherPoints: List<GeoPoint>
): Pair<Double, Double> {
    // Get current zoom level
    val zoomLevel = mapView.zoomLevelDouble

    // No offset when fully zoomed in
    if (zoomLevel >= 16.0) return Pair(0.0, 0.0)

    // DIRECT APPROACH: Use a fixed vertical offset based on index and zoom
    // This creates a clear vertical stacking effect regardless of proximity
    val verticalOffsetBase = 0.0045  // This is a VERY large value - adjust as needed

    // Apply stronger offset at lower zoom levels
    val zoomMultiplier = (16.0 - zoomLevel) / 8.0

    // Calculate latitude offset - directly based on index (higher index = higher up)
    val latOffset = verticalOffsetBase * (markerIndex + 1) * zoomMultiplier

    // Small longitude offset to create an arc
    val lngOffset = 0.002 * (markerIndex - (totalMarkers / 2.0)) / totalMarkers

    return Pair(latOffset, lngOffset)
}

/**
 * Calculate distance between two points in kilometers
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Earth radius in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

/**
 * Create a custom red pin marker for destination
 */
private fun createRedPinMarker(context: Context): BitmapDrawable {
    val size = 48 // Size of marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.RED
    paint.style = Paint.Style.FILL

    // Draw pin shape
    val pinWidth = size * 0.8f
    val pinHeight = size * 0.8f

    // Draw a circle for the top of the pin
    val circleRadius = pinWidth * 0.5f
    canvas.drawCircle(size/2f, circleRadius, circleRadius, paint)

    // Draw triangle for bottom of pin
    val path = android.graphics.Path()
    path.moveTo(size/2f, size * 0.9f) // Point
    path.lineTo(size/2f - circleRadius * 0.7f, circleRadius * 1.5f) // Left edge
    path.lineTo(size/2f + circleRadius * 0.7f, circleRadius * 1.5f) // Right edge
    path.close()

    canvas.drawPath(path, paint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Create a glowing location marker for user position
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
 * Creates numbered marker for tour stops
 */
private fun createNumberedMarker(context: Context, number: Int): BitmapDrawable {
    val size = 48 // Size of marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background circle
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    bgPaint.color = AndroidColor.RED
    bgPaint.style = Paint.Style.FILL
    canvas.drawCircle(size/2f, size/2f, size/2f - 4, bgPaint)

    // Number text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    textPaint.color = AndroidColor.WHITE
    textPaint.textSize = size * 0.5f
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.isFakeBoldText = true

    // Center text position
    val xPos = size / 2f
    val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

    // Draw the number
    canvas.drawText(number.toString(), xPos, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}


/**
 * Add markers and route with hairline connector lines.
 * Uses extremely thin, semi-transparent lines for a subtle but effective visual connection.
 */
private fun addMarkersAndRoute(
    mapView: MapView,
    userPoint: GeoPoint?,
    destinationPoints: List<GeoPoint>,
    routeColor: Int,
    destinationColor: Int,
    context: Context
) {
    // Do nothing if no destinations
    if (destinationPoints.isEmpty()) return

    // Sort points by distance from top of screen to bottom
    // This ensures markers with higher indices appear further up (creating a clear progression)
    val sortedPoints = destinationPoints.sortedBy { it.latitude }

    // Add markers for each destination with hairline connector lines
    sortedPoints.forEachIndexed { index, point ->
        // Calculate offset based on zoom level and proximity to other points
        val (latOffset, lngOffset) = calculateMarkerOffset(
            mapView,
            index,
            sortedPoints.size,
            point,
            sortedPoints.filterIndexed { i, _ -> i != index }
        )

        // Apply offset to create a new position with projection effect
        val adjustedPosition = GeoPoint(
            point.latitude + latOffset,
            point.longitude + lngOffset
        )

        // Add a hairline connector line from actual position to offset position
        // Only add if there's actually an offset
        if (latOffset != 0.0 || lngOffset != 0.0) {
            val connectorLine = Polyline(mapView).apply {
                setPoints(listOf(point, adjustedPosition))

                // Style for connector line - extremely thin, semi-transparent line
                outlinePaint.color = AndroidColor.parseColor("#40000000") // 25% transparent black
                outlinePaint.strokeWidth = 1.5f // Very thin line (hairline)
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(connectorLine)
        }

        // Add the marker at the offset position
        val marker = Marker(mapView).apply {
            position = adjustedPosition
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Try to use custom numbered marker
            try {
                // Find the original index to maintain the correct numbering
                val originalIndex = destinationPoints.indexOf(point)
                icon = createNumberedMarker(context, originalIndex + 1)
            } catch (e: Exception) {
                // Fallback to simple drawable tinted red
                val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)?.mutate()
                drawable?.setTint(AndroidColor.RED)
                icon = drawable
            }

            // Find the original index to maintain the correct numbering
            val originalIndex = destinationPoints.indexOf(point)
            title = "Stop ${originalIndex + 1}"
        }
        mapView.overlays.add(marker)
    }

    // Add user marker if we have user location
    if (userPoint != null) {
        // Add user marker with glowing blue dot
        val userMarker = Marker(mapView).apply {
            position = userPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // Try to use glowing marker
            try {
                icon = createGlowingLocationMarker(context, Color(0xFF00AAFF).toArgb())
            } catch (e: Exception) {
                // Fallback to standard icon
                icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
            }

            title = "Ihr Standort"
        }
        mapView.overlays.add(userMarker)
    }

    // For the route line, always use the actual geographic points without offset
    // Connect from user location to all destinations in the original order
    if (userPoint != null && destinationPoints.isNotEmpty()) {
        val routePoints = mutableListOf<GeoPoint>()
        routePoints.add(userPoint)
        routePoints.addAll(destinationPoints) // Original points, not offset ones

        val line = Polyline(mapView).apply {
            setPoints(routePoints)
            outlinePaint.color = routeColor
            outlinePaint.strokeWidth = 7f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(line)
    } else if (destinationPoints.size > 1) {
        // If no user location, still connect the destination points
        val line = Polyline(mapView).apply {
            setPoints(destinationPoints)
            outlinePaint.color = routeColor
            outlinePaint.strokeWidth = 7f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(line)
    }

    // Trigger a single redraw
    mapView.invalidate()
}

/**
 * Creates a small dot marker for showing actual position
 */
private fun createSmallDotMarker(context: Context): BitmapDrawable {
    val size = 12 // Small size in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create a small, semi-transparent black dot
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.parseColor("#80000000") // 50% transparent black
    paint.style = Paint.Style.FILL

    // Draw a small circle
    canvas.drawCircle(size/2f, size/2f, size/2f, paint)

    return BitmapDrawable(context.resources, bitmap)
}