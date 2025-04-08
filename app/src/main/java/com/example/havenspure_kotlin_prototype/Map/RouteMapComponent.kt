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
 * @param destinationLocation The destination location
 */
@Composable
fun StableRouteMapComponent(userLocation: LocationData?, destinationLocation: LocationData) {
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
    val destinationPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)
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
        if (!mapInitialized.value) {
            try {
                // Basic setup on main thread
                mapView.setTileSource(TileSourceFactory.MAPNIK)

                // Set initial center and zoom
                val initialZoom = 15.0
                mapView.controller.setZoom(initialZoom)

                val initialCenter = userPoint ?: destinationPoint
                mapView.controller.setCenter(initialCenter)

                // Only do the rest after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // If we have both points, calculate a bounding box
                        if (userPoint != null) {
                            // Simple bounding box calculation
                            val north = max(userPoint.latitude, destinationPoint.latitude) + 0.01
                            val south = min(userPoint.latitude, destinationPoint.latitude) - 0.01
                            val east = max(userPoint.longitude, destinationPoint.longitude) + 0.01
                            val west = min(userPoint.longitude, destinationPoint.longitude) - 0.01

                            val box = BoundingBox(north, east, south, west)
                            mapView.zoomToBoundingBox(box, true, 100)
                        }

                        // Add markers and line after a delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                addMarkersAndLine(mapView, userPoint, destinationPoint, routeColor, destinationColor, context)
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
    }

    // Update user location marker only, and only if user position changes significantly
    LaunchedEffect(userLocation) {
        if (mapInitialized.value && userLocation != null && !mapUpdateInProgress.value) {
            mapUpdateInProgress.value = true

            // New user point
            val newUserPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            // Move center if focused on user
            if (isFocusedOnUser.value) {
                mapView.controller.animateTo(newUserPoint)
            }

            // Update markers on background thread
            backgroundHandler.post {
                try {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            // Very simple update - just recreate everything
                            // This is less efficient but much more stable
                            mapView.overlays.clear()
                            addMarkersAndLine(mapView, newUserPoint, destinationPoint, routeColor, destinationColor, context)
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
 * Simplified function to add markers and connecting line.
 * All objects are created fresh each time for stability.
 */
private fun addMarkersAndLine(
    mapView: MapView,
    userPoint: GeoPoint?,
    destinationPoint: GeoPoint,
    routeColor: Int,
    destinationColor: Int,
    context: Context
) {
    // Add destination marker with red pin
    val destMarker = Marker(mapView).apply {
        position = destinationPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Try to use custom red pin marker
        try {
            icon = createRedPinMarker(context)
        } catch (e: Exception) {
            // Fallback to simple drawable tinted red
            val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)?.mutate()
            drawable?.setTint(AndroidColor.RED)
            icon = drawable
        }

        title = "Ziel"
    }
    mapView.overlays.add(destMarker)

    // Add user marker and line if we have user location
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

        // Add simple line connecting the points (green route)
        val line = Polyline(mapView).apply {
            setPoints(listOf(userPoint, destinationPoint))
            outlinePaint.color = routeColor
            outlinePaint.strokeWidth = 7f // Slightly thicker
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(line)
    }

    // Trigger a single redraw
    mapView.invalidate()
}