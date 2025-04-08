package com.example.havenspure_kotlin_prototype.ui.screens

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
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import java.io.File

/**
 * RichtungenZeigenScreen displays a turn-by-turn navigation interface.
 * This screen provides directions to help the user find a tour location
 * using actual street-based routing.
 *
 * @param tour The tour to navigate to
 * @param onBackClick Callback for the back button
 * @param locationViewModel ViewModel providing user location updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichtungenZeigenScreen(
    tour: Tour,
    onBackClick: () -> Unit,
    locationViewModel: LocationViewModel
) {
    val TAG = "RichtungenZeigenScreen"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userLocation by locationViewModel.location
    val coroutineScope = rememberCoroutineScope()

    // Ensure the tour has a location
    val tourLocation = tour.location ?: LocationData(53.5142, 8.1428) // Default to Wilhelmshaven harbor if null

    // State variables
    var mapInitialized by remember { mutableStateOf(false) }
    var navigationStarted by remember { mutableStateOf(false) }
    var followUser by remember { mutableStateOf(true) }
    var currentDirection by remember { mutableStateOf("Starten Sie die Navigation") }
    var currentDistance by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRerouting by remember { mutableStateOf(false) }

    // Colors for the map
    val routeColor = PrimaryColor.toArgb()
    val userMarkerColor = Color.Blue.toArgb()
    val destinationMarkerColor = Color.Red.toArgb()

    // Map reference
    val mapView = remember {
        MapView(context).apply {
            // Performance settings
            setTileSource(TileSourceFactory.MAPNIK)
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 12.0
            maxZoomLevel = 19.0
            controller.setZoom(17.0)
        }
    }

    // Initialize RoutingGraph on first composition
    LaunchedEffect(Unit) {
        try {
            // Set cache directory
            val cacheDir = File(context.cacheDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Load configuration
            Configuration.getInstance().apply {
                load(context, PreferenceManager.getDefaultSharedPreferences(context))
                osmdroidTileCache = cacheDir
                userAgentValue = context.packageName
                osmdroidBasePath = context.filesDir
            }

            // Initialize RoutingGraph
            RoutingGraph.initialize(context)
            RoutingGraph.setColors(routeColor, userMarkerColor, destinationMarkerColor)

            mapInitialized = true
            Log.d(TAG, "RoutingGraph initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RoutingGraph: ${e.message}")
        }
    }

    // Add rotation overlay to map
    LaunchedEffect(mapView) {
        val rotationGestureOverlay = RotationGestureOverlay(mapView)
        rotationGestureOverlay.isEnabled = true
        mapView.overlays.add(rotationGestureOverlay)
    }

    // Start navigation when user location is available
    LaunchedEffect(userLocation, mapInitialized) {
        if (userLocation != null && mapInitialized && !navigationStarted) {
            try {
                val userGeoPoint = GeoPoint(userLocation!!.latitude, userLocation!!.longitude)
                val destGeoPoint = GeoPoint(tourLocation.latitude, tourLocation.longitude)

                // Center map on user location
                mapView.controller.setCenter(userGeoPoint)

                // Start navigation
                isLoading = true

                RoutingGraph.calculateRoute(mapView, userGeoPoint, destGeoPoint) { success, error, _ ->
                    if (success) {
                        navigationStarted = true
                        Log.d(TAG, "Navigation started successfully")
                    } else {
                        Log.e(TAG, "Failed to start navigation: $error")
                        currentDirection = "Routenberechnung fehlgeschlagen. Verwende einfache Route."
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting navigation: ${e.message}")
                isLoading = false
            }
        }
    }

    // Update navigation when user location changes
    LaunchedEffect(userLocation) {
        if (navigationStarted && userLocation != null) {
            try {
                // Get current navigation instruction
                currentDirection = RoutingGraph.getNavigationInstruction(userLocation!!, tourLocation)

                // Get current distance
                currentDistance = RoutingGraph.getRemainingDistance(userLocation!!, tourLocation)

                // Update user position on map
                val userGeoPoint = GeoPoint(userLocation!!.latitude, userLocation!!.longitude)
                RoutingGraph.updateUserMarker(mapView, userGeoPoint)

                // If follow user is enabled, center map on user
                if (followUser) {
                    mapView.controller.animateTo(userGeoPoint)
                }

                // Check if we need to recalculate the route
                val destGeoPoint = GeoPoint(tourLocation.latitude, tourLocation.longitude)
                val distToDest = userGeoPoint.distanceToAsDouble(destGeoPoint)

                // If we've reached the destination
                if (distToDest < 30) {
                    currentDirection = "Sie haben Ihr Ziel erreicht"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating navigation: ${e.message}")
            }
        }
    }

    // Lifecycle management for map
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation zu ${tour.title}", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                    color = PrimaryColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Direction text
                        Text(
                            text = currentDirection,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Distance remaining
                        Text(
                            text = currentDistance,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                }

                // Map container
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

                    // Loading indicator
                    if (isLoading) {
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
                                    color = PrimaryColor,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isRerouting) "Neue Route wird berechnet..."
                                    else "Route wird geladen...",
                                    color = PrimaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // My location button
                    FloatingActionButton(
                        onClick = {
                            if (userLocation != null) {
                                followUser = true
                                mapView.controller.animateTo(
                                    GeoPoint(userLocation!!.latitude, userLocation!!.longitude)
                                )

                                // If navigation has started, check if we need to recalculate
                                if (navigationStarted) {
                                    // Force recalculation by simulating a new location
                                    val mainHandler = Handler(Looper.getMainLooper())
                                    mainHandler.post {
                                        locationViewModel.updateLocation(userLocation!!)
                                    }
                                }
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
    }
}