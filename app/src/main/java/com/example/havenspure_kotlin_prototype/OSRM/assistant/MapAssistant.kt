package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.data.models.RouteData
import com.example.havenspure_kotlin_prototype.OSRM.data.remote.MapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

/**
 * Main coordinator class for map functionality
 * Provides a simplified interface to all map-related operations
 */
class MapAssistant private constructor(private val context: Context) {
    private val TAG = "MapAssistant"

    // Component managers
    private val markerManager = MarkerManager(context)
    private val mapRepository = MapRepository(context)
    private val routeManager = RouteManager(context, markerManager, mapRepository)
    private val navigationManager = NavigationManager(context)
    private val offlineMapManager = OfflineMapManager(context)

    // State tracking
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentRoute = MutableStateFlow<RouteData?>(null)
    val currentRoute: StateFlow<RouteData?> = _currentRoute

    // Map colors
    private var routeColor: Int = android.graphics.Color.BLUE
    private var userMarkerColor: Int = android.graphics.Color.BLUE
    private var destinationMarkerColor: Int = android.graphics.Color.RED

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: MapAssistant? = null

        /**
         * Get or create the MapAssistant instance
         */
        fun getInstance(context: Context): MapAssistant {
            return instance ?: synchronized(this) {
                instance ?: MapAssistant(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Initialize all map components
     * Call this before using any map functionality
     */
    fun initialize() {
        try {
            // Initialize offline map configuration
            offlineMapManager.initialize()

            Log.d(TAG, "MapAssistant initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MapAssistant: ${e.message}")
        }
    }

    /**
     * Setup the map view with necessary configuration
     *
     * @param mapView Map view to set up
     */
    fun setupMapView(mapView: MapView) {
        try {
            // Pre-cache important settings to speed up initialization
            mapView.setMultiTouchControls(true)
            mapView.isTilesScaledToDpi = true
            mapView.setUseDataConnection(true) // Set to false if using only offline maps

            // Set higher tile download threads for faster loading
            val tileProvider = mapView.tileProvider
            if (tileProvider is org.osmdroid.tileprovider.MapTileProviderBase) {
                // Increase download threads if available in this version
                tileProvider.getTileRequestCompleteHandlers().clear() // Reset handlers
                // Add more efficient handlers if needed
            }

            // Set minimum zoom level to ensure detailed tiles
            mapView.minZoomLevel = 10.0

            // Configure map for optimal performance
            offlineMapManager.configureMapView(mapView)

            // Add rotation gesture overlay
            val rotationGestureOverlay = RotationGestureOverlay(mapView)
            rotationGestureOverlay.isEnabled = true
            mapView.overlays.add(rotationGestureOverlay)

            // Try to disable built-in loading indicator if possible
            try {
                val tilesOverlay = mapView.overlayManager.tilesOverlay
                // Use reflection if needed to access loading line property
                val field = tilesOverlay.javaClass.getDeclaredField("mLoadingLine")
                field.isAccessible = true
                field.set(tilesOverlay, null)
            } catch (e: Exception) {
                // If this fails, it's not critical
                Log.d(TAG, "Could not disable loading line: ${e.message}")
            }

            Log.d(TAG, "MapView set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MapView: ${e.message}")
        }
    }

    /**
     * Set colors for map elements
     */
    fun setColors(
        routeColor: Int,
        userMarkerColor: Int,
        destinationMarkerColor: Int
    ) {
        this.routeColor = routeColor
        this.userMarkerColor = userMarkerColor
        this.destinationMarkerColor = destinationMarkerColor

        // Pass colors to route manager
        routeManager.setColors(routeColor, userMarkerColor, destinationMarkerColor)
    }

    /**
     * Start navigation between two points
     *
     * @param mapView Map view to display navigation on
     * @param userLocation User's current location
     * @param destinationLocation Destination location
     * @param callback Callback for navigation status
     */
    fun startNavigation(
        mapView: MapView,
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        try {
            _isLoading.value = true

            // Convert to GeoPoints
            val startPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val endPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)

            // Pre-load map tiles for current area (speeds up map visibility)
            // Create bounding box for route area
            val north = Math.max(startPoint.latitude, endPoint.latitude)
            val south = Math.min(startPoint.latitude, endPoint.latitude)
            val east = Math.max(startPoint.longitude, endPoint.longitude)
            val west = Math.min(startPoint.longitude, endPoint.longitude)

            // Add padding to the bounding box (50%)
            val latPadding = (north - south) * 0.5
            val lonPadding = (east - west) * 0.5

            val boundingBox = org.osmdroid.util.BoundingBox(
                north + latPadding,
                east + lonPadding,
                south - latPadding,
                west - lonPadding
            )

            // Zoom to the bounding box
            mapView.zoomToBoundingBox(boundingBox, false, 50)

            // Set initial map position
            mapView.controller.setCenter(startPoint)
            mapView.controller.setZoom(15.0)

            // Calculate route with higher priority
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Calculate route (use existing method)
                    routeManager.calculateRoute(mapView, startPoint, endPoint) { success, error, route ->
                        _isLoading.value = false

                        if (success && route != null) {
                            _currentRoute.value = route
                            _isNavigating.value = true

                            // Start orientation tracking for bearing
                            navigationManager.startOrientationTracking()

                            callback(true, null)
                        } else {
                            callback(false, error)
                        }
                    }
                } catch (e: Exception) {
                    _isLoading.value = false
                    Log.e(TAG, "Error in route calculation: ${e.message}")
                    callback(false, e.message)
                }
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Error starting navigation: ${e.message}")
            callback(false, e.message)
        }
    }

// Modify the updateNavigation method in MapAssistant.kt to improve map smoothness

    /**
     * Update navigation with user's current location
     *
     * @param mapView Map view to update
     * @param userLocation User's current location
     * @param destinationLocation Destination location
     * @param centerMap Whether to center the map on user's location
     * @return True if updates were applied successfully
     */
    fun updateNavigation(
        mapView: MapView,
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM,
        centerMap: Boolean = false
    ): Boolean {
        if (!_isNavigating.value) return false

        try {
            // Update user marker with bearing from sensors
            val bearing = navigationManager.currentBearing.value
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            // Update the marker on the map
            routeManager.updateUserMarker(
                mapView,
                userPoint,
                bearing
            )

            // Only center map if explicitly requested (follow mode active)
            if (centerMap) {
                // Use smooth animation to center with a slight zoom
                mapView.controller.animateTo(
                    userPoint,
                    mapView.zoomLevelDouble,
                    400, // Animation duration in ms
                    0f   // No rotation
                )
            }

            // Update navigation instructions regardless of centering
            navigationManager.updateNavigation(
                userLocation,
                destinationLocation,
                routeManager
            )

            // Check if rerouting is needed
            if (routeManager.isReroutingNeeded(userLocation)) {
                Log.d(TAG, "User is off route, rerouting...")
                rerouteNavigation(mapView, userLocation, destinationLocation)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating navigation: ${e.message}")
            return false
        }
    }

    /**
     * Recalculate route when user is off path
     */
    private fun rerouteNavigation(
        mapView: MapView,
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM
    ) {
        try {
            _isLoading.value = true

            // Convert to GeoPoints
            val startPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val endPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)

            // Calculate new route
            routeManager.calculateRoute(mapView, startPoint, endPoint) { success, error, route ->
                _isLoading.value = false

                if (success && route != null) {
                    _currentRoute.value = route
                    Log.d(TAG, "Rerouting completed successfully")
                } else {
                    Log.e(TAG, "Rerouting failed: $error")
                }
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Error during rerouting: ${e.message}")
        }
    }

    /**
     * Stop navigation and clean up resources
     */
    fun stopNavigation() {
        try {
            _isNavigating.value = false
            navigationManager.stopOrientationTracking()
            routeManager.clearRoute()
            _currentRoute.value = null

            Log.d(TAG, "Navigation stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping navigation: ${e.message}")
        }
    }

    /**
     * Get current navigation instruction
     */
    fun getCurrentInstruction(): String {
        return navigationManager.currentInstruction.value
    }

    /**
     * Get remaining distance text
     */
    fun getRemainingDistance(): String {
        return navigationManager.remainingDistance.value
    }

    /**
     * Check if user has arrived at destination
     */
    fun hasArrived(): Boolean {
        return navigationManager.isArrived.value
    }

    /**
     * Download map area for offline use
     *
     * @param mapView Map view to use for downloading
     * @param center Center point of area to download
     * @param radiusKm Radius in kilometers to download
     * @param callback Progress callback
     */
    fun downloadMapArea(
        mapView: MapView,
        center: GeoPoint,
        radiusKm: Double,
        callback: (progress: Int, total: Int, completed: Boolean) -> Unit
    ) {
        offlineMapManager.downloadMapArea(mapView, center, radiusKm, 12, 17, callback)
    }



    /**
     * Restore navigation from saved route data
     *
     * @param mapView Map view to display navigation on
     * @param userLocation User's current location
     * @param destinationLocation Destination location
     * @param routeData Previously calculated route data
     * @return True if restored successfully
     */
    fun restoreNavigation(
        mapView: MapView,
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM,
        routeData: RouteData
    ): Boolean {
        try {
            _isLoading.value = true

            // Convert to GeoPoints
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val destPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)

             // In restoreNavigation method:
           // Get the current bearing from NavigationManager
            val currentBearing = navigationManager.currentBearing.value

            // Pass the bearing to restoreRouteVisualization
            routeManager.restoreRouteVisualization(mapView, routeData, userPoint, destPoint, currentBearing)


            // Set map view
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(userPoint)

            // Update state
            _currentRoute.value = routeData
            _isNavigating.value = true
            _isLoading.value = false

            // Start orientation tracking for bearing
            navigationManager.startOrientationTracking()

            return true
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Error restoring navigation: ${e.message}")
            return false
        }
    }
    /**
     * Clean up resources when done
     */

    fun cleanup() {
        try {
            // Stop navigation before cleanup
            if (_isNavigating.value) {
                stopNavigation()
            }

            // Ensure orientation tracking is stopped
            navigationManager.stopOrientationTracking()
            navigationManager.cleanup()

            // Ensure route is cleared
            routeManager.clearRoute()
            _currentRoute.value = null

            Log.d(TAG, "MapAssistant cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}