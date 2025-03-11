package com.example.havenspure_kotlin_prototype.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
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
import com.example.havenspure_kotlin_prototype.Data.LocationData
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.CustomZoomButtonsController
import java.io.File
import kotlin.math.*

/**
 * Wilhelmshaven city center coordinates
 */
private val WILHELMSHAVEN_COORDINATES = GeoPoint(53.5225, 8.1083)

/**
 * Calculates the distance between two points in meters using the Haversine formula.
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}

/**
 * Creates a glowing location marker icon programmatically.
 */
private fun createGlowingLocationMarker(context: Context, color: Int): BitmapDrawable {
    val size = 48 // Size of the marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create paints
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
 * An enhanced, high-performance map component that displays an OpenStreetMap.
 * If user location is available, it centers on the user location.
 * Otherwise, it shows the map centered on Wilhelmshaven.
 *
 * @param locationData The user's location data (null if location not available)
 */
@Composable
fun MapComponent(locationData: LocationData?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configure OSMDroid with custom cache location and performance optimizations
    val configuration = Configuration.getInstance()
    configuration.load(
        context,
        PreferenceManager.getDefaultSharedPreferences(context)
    )

    // Use specific cache directory to avoid SELinux issues
    configuration.osmdroidTileCache = File(context.cacheDir, "osmdroid")

    // Performance optimization for OSMDroid config
    configuration.apply {
        userAgentValue = context.packageName
        osmdroidBasePath = context.filesDir
        expirationOverrideDuration = 1000L * 60 * 60 * 24 * 7 // Cache for a week
        tileDownloadThreads = 2 // Reduce number of threads
        tileFileSystemThreads = 2
        tileDownloadMaxQueueSize = 50
        tileFileSystemMaxQueueSize = 50
        isMapViewHardwareAccelerated = true
    }

    // Create center point (user location or default city center)
    val centerLocation = if (locationData != null) {
        GeoPoint(locationData.latitude, locationData.longitude)
    } else {
        WILHELMSHAVEN_COORDINATES
    }

    val accentColor = MaterialTheme.colorScheme.primary.toArgb()

    // Container for map and FAB
    Box(modifier = Modifier.fillMaxSize()) {
        // State for tracking last position updates
        val lastProcessedLocation = remember { mutableStateOf<GeoPoint?>(null) }
        val lastUpdateTime = remember { mutableStateOf(0L) }
        val isMapInitialized = remember { mutableStateOf(false) }
        val isFocusedOnUser = remember { mutableStateOf(locationData != null) }

        // Create and remember MapView with performance settings
        val mapView = remember {
            MapView(context).apply {
                // Performance settings
                isTilesScaledToDpi = false
                setMultiTouchControls(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                setUseDataConnection(true)
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                // Disable compass and zoom controls to reduce sensor usage
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            }
        }

        // Setup map and add markers in LaunchedEffect
        LaunchedEffect(mapView, locationData) {
            try {
                // Initialize map on first load
                if (!isMapInitialized.value) {
                    // Basic MapView setup
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                    mapView.minZoomLevel = 5.0
                    mapView.maxZoomLevel = 19.0

                    // If showing city center initially, use a lower zoom level
                    if (locationData == null) {
                        mapView.controller.setZoom(14.0)
                    } else {
                        mapView.controller.setZoom(18.0)
                    }

                    // Initial center set up
                    mapView.controller.setCenter(centerLocation)

                    // Mark as initialized
                    isMapInitialized.value = true
                    lastUpdateTime.value = System.currentTimeMillis()

                    // Only add user location marker if we have location data
                    if (locationData != null) {
                        updateMapMarkers(mapView, centerLocation, locationData, accentColor, context)
                        lastProcessedLocation.value = centerLocation
                    }

                    return@LaunchedEffect
                }

                // Skip updates if there's no location data
                if (locationData == null) return@LaunchedEffect

                // For updates, check both distance and time thresholds
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime.value
                val lastLocation = lastProcessedLocation.value

                // Calculate if we should update based on:
                // 1. Minimum time threshold of 3 seconds AND
                // 2. Minimum distance of 20 meters OR time threshold of 10 seconds
                val shouldUpdate = lastLocation == null || (
                        timeSinceLastUpdate > 3000 && (
                                calculateDistance(
                                    lastLocation.latitude, lastLocation.longitude,
                                    centerLocation.latitude, centerLocation.longitude
                                ) > 20 || timeSinceLastUpdate > 10000
                                )
                        )

                if (shouldUpdate) {
                    // If transitioning from city view to user location, use animation
                    if (lastLocation == null || !isFocusedOnUser.value) {
                        mapView.controller.animateTo(centerLocation, 18.0, 500L)
                        isFocusedOnUser.value = true
                    } else if (isFocusedOnUser.value) {
                        // Only animate if we're focused on user
                        mapView.controller.animateTo(centerLocation, mapView.zoomLevelDouble, 300L)
                    }

                    // Always update markers when we have location data
                    updateMapMarkers(mapView, centerLocation, locationData, accentColor, context)

                    // Update our tracking variables
                    lastProcessedLocation.value = centerLocation
                    lastUpdateTime.value = currentTime
                }

            } catch (e: Exception) {
                Log.e("MapComponent", "Error updating map: ${e.message}")
            }
        }

        // Set up scroll throttling to reduce jitter when panning manually
        LaunchedEffect(mapView) {
            mapView.addMapListener(object : MapListener {
                private var lastScrollUpdate = 0L

                override fun onScroll(event: ScrollEvent?): Boolean {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollUpdate > 100) { // Only update every 100ms during scroll
                        lastScrollUpdate = now
                        mapView.invalidate()
                        // When user scrolls manually, we're no longer focused on user location
                        isFocusedOnUser.value = false
                    }
                    return true
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    return false
                }
            })
        }

        // Handle map lifecycle with optimized cleanup
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        Handler(Looper.getMainLooper()).post {
                            mapView.onResume()
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        Handler(Looper.getMainLooper()).post {
                            mapView.onPause()
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        // Ensure complete cleanup on destroy
                        Handler(Looper.getMainLooper()).post {
                            try {
                                mapView.onDetach()
                            } catch (e: Exception) {
                                Log.e("MapComponent", "Error detaching map: ${e.message}")
                            }
                        }
                    }
                    else -> { /* other events are not handled */ }
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                try {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    // Clear overlays to free memory
                    mapView.overlays.clear()
                    mapView.onDetach()
                } catch (e: Exception) {
                    Log.e("MapComponent", "Error disposing map: ${e.message}")
                }
            }
        }

        // Render the map with minimal updates
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { /* Minimal update approach for better performance */ }
        )

        // Show location FAB only if we have location data
        if (locationData != null) {
            FloatingActionButton(
                onClick = {
                    try {
                        // Re-center map on user location using Handler for smooth operation
                        Handler(Looper.getMainLooper()).post {
                            val userLocation = GeoPoint(locationData.latitude, locationData.longitude)
                            mapView.controller.animateTo(userLocation, 18.0, 500L)
                            isFocusedOnUser.value = true
                        }
                    } catch (e: Exception) {
                        Log.e("MapComponent", "Error on FAB click: ${e.message}")
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
 * Updates map markers without causing a full redraw each time.
 * Only used when we have user location data.
 */
private fun updateMapMarkers(
    mapView: MapView,
    position: GeoPoint,
    locationData: LocationData,
    accentColor: Int,
    context: Context
) {
    // Remove any existing markers to prevent duplicates
    val overlaysToRemove = mapView.overlays.filterIsInstance<Marker>()
    mapView.overlays.removeAll(overlaysToRemove)

    // 1. Add glowing marker (visual indicator)
    val glowMarker = Marker(mapView)
    glowMarker.position = position
    glowMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    glowMarker.icon = createGlowingLocationMarker(context, accentColor)
    glowMarker.setOnMarkerClickListener { _, _ -> false } // Prevent clicking
    mapView.overlays.add(glowMarker)

    // 2. Add standard pin marker above the glow (visible marker)
    val standardIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
    if (standardIcon != null) {
        val pinMarker = Marker(mapView)
        pinMarker.position = position
        pinMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        pinMarker.icon = standardIcon
        pinMarker.title = "Ihr Standort"
        pinMarker.snippet = "${String.format("%.6f", locationData.latitude)}, ${String.format("%.6f", locationData.longitude)}"
        mapView.overlays.add(pinMarker)
    }

    // Trigger a single redraw
    mapView.invalidate()
}