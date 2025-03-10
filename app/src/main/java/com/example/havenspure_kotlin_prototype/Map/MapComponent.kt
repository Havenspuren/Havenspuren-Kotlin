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
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay

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
 * An enhanced, high-performance map component that displays either an OpenStreetMap with user location
 * or a static map image. Includes visual markers and optimized performance.
 *
 * @param locationData The user's location data (null if location not available)
 */
@Composable
fun MapComponent(locationData: LocationData?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configure OSMDroid with performance optimizations
    Configuration.getInstance().load(
        context,
        PreferenceManager.getDefaultSharedPreferences(context)
    )

    // Performance optimization for OSMDroid config
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidTileCache = context.cacheDir
        osmdroidBasePath = context.filesDir
        expirationOverrideDuration = 1000L * 60 * 60 * 24 * 7 // Cache for a week
        tileDownloadThreads = 2 // Reduce number of threads
        tileFileSystemThreads = 2
        tileDownloadMaxQueueSize = 50
        tileFileSystemMaxQueueSize = 50
        isMapViewHardwareAccelerated = true
    }

    // Use a static image if location data is null
    if (locationData == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.map),
                contentDescription = "Map",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Create user location point
        val userLocation = GeoPoint(locationData.latitude, locationData.longitude)
        val accentColor = MaterialTheme.colorScheme.primary.toArgb()

        // Container for map and FAB
        Box(modifier = Modifier.fillMaxSize()) {
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
                }
            }

            // Setup map and add markers in LaunchedEffect
            LaunchedEffect(mapView, locationData) {
                try {
                    // Basic MapView setup
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                    mapView.controller.setZoom(18.0)
                    mapView.controller.setCenter(userLocation)

                    // Set zoom levels
                    mapView.minZoomLevel = 5.0
                    mapView.maxZoomLevel = 19.0

                    // Add a compass overlay with optimized settings
                    val compassOverlay = CompassOverlay(context, mapView)
                    compassOverlay.enableCompass()
                    compassOverlay.setCompassCenter(50f, 50f)
                    mapView.overlays.add(compassOverlay)

                    // Add a map listener for throttling events
                    mapView.addMapListener(object : MapListener {
                        private var lastUpdate = 0L

                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 100) { // Only update every 100ms
                                lastUpdate = now
                                mapView.invalidate()
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            return false
                        }
                    })

                    // Remove any existing markers to prevent duplicates
                    val overlaysToRemove = mapView.overlays.filterIsInstance<Marker>()
                    mapView.overlays.removeAll(overlaysToRemove)

                    // 1. Add glowing marker (visual indicator)
                    val glowMarker = Marker(mapView)
                    glowMarker.position = userLocation
                    glowMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    glowMarker.icon = createGlowingLocationMarker(context, accentColor)
                    glowMarker.setOnMarkerClickListener { _, _ -> false } // Prevent clicking
                    mapView.overlays.add(glowMarker)

                    // 2. Add standard pin marker above the glow (visible marker)
                    val standardIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
                    if (standardIcon != null) {
                        val pinMarker = Marker(mapView)
                        pinMarker.position = userLocation
                        pinMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        pinMarker.icon = standardIcon
                        pinMarker.title = "Ihr Standort"
                        pinMarker.snippet = "${String.format("%.6f", locationData.latitude)}, ${String.format("%.6f", locationData.longitude)}"
                        mapView.overlays.add(pinMarker)
                    }

                    mapView.invalidate() // Refresh the map with new markers
                } catch (e: Exception) {
                    Log.e("MapComponent", "Error initializing map: ${e.message}")
                }
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

            // Location FAB
            FloatingActionButton(
                onClick = {
                    try {
                        // Re-center map on user location using Handler for smooth operation
                        Handler(Looper.getMainLooper()).post {
                            mapView.controller.animateTo(userLocation)
                            mapView.controller.setZoom(18.0)
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