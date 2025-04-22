package com.example.havenspure_kotlin_prototype.OSRM.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.OSRM.assistant.MapAssistant
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.data.models.RouteData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * ViewModel for managing map UI state
 */
class MapViewModel(private val context: Context) : ViewModel() {
    private val TAG = "MapViewModel"

    // Main coordinator
    private val mapAssistant = MapAssistant.getInstance(context)

    // Flag to prevent redundant route calculations
    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    // UI state
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentRoute = MutableStateFlow<RouteData?>(null)
    val currentRoute: StateFlow<RouteData?> = _currentRoute.asStateFlow()

    private val _navigationInstruction = MutableStateFlow("Starten Sie die Navigation")
    val navigationInstruction: StateFlow<String> = _navigationInstruction.asStateFlow()

    private val _remainingDistance = MutableStateFlow("Entfernung wird berechnet...")
    val remainingDistance: StateFlow<String> = _remainingDistance.asStateFlow()

    private val _userLocation = MutableStateFlow<LocationDataOSRM?>(null)
    val userLocation: StateFlow<LocationDataOSRM?> = _userLocation.asStateFlow()

    private val _destinationLocation = MutableStateFlow<LocationDataOSRM?>(null)
    val destinationLocation: StateFlow<LocationDataOSRM?> = _destinationLocation.asStateFlow()

    private val _followUserLocation = MutableStateFlow(true)
    val followUserLocation: StateFlow<Boolean> = _followUserLocation.asStateFlow()

    private val _hasArrived = MutableStateFlow(false)
    val hasArrived: StateFlow<Boolean> = _hasArrived.asStateFlow()

    // Loading states
    private val _mapTilesLoaded = MutableStateFlow(false)
    val mapTilesLoaded: StateFlow<Boolean> = _mapTilesLoaded.asStateFlow()

    private val _routeCalculated = MutableStateFlow(false)
    val routeCalculated: StateFlow<Boolean> = _routeCalculated.asStateFlow()

    private val _isFullyReady = MutableStateFlow(false)
    val isFullyReady: StateFlow<Boolean> = _isFullyReady.asStateFlow()

    private val _savedRouteData = MutableStateFlow<RouteData?>(null)
    val savedRouteData: StateFlow<RouteData?> = _savedRouteData.asStateFlow()

    private val _savedUserLocation = MutableStateFlow<LocationDataOSRM?>(null)
    private val _savedDestinationLocation = MutableStateFlow<LocationDataOSRM?>(null)

    private val _isMinimallyReady = MutableStateFlow(false)
    val isMinimallyReady: StateFlow<Boolean> = _isMinimallyReady.asStateFlow()

    private var lastNavigationUpdateTime = 0L

    // Route cache with key as "startLat,startLng;destLat,destLng"
    private val routeCache = mutableMapOf<String, RouteData>()


    // Add this to your MapViewModel.kt file
// Add these variables near the top of the class with other declarations

   // private val TAG = "MapViewModel" // If not already defined
    private var lastLoggedBearing = -999f
    private val BEARING_LOG_THRESHOLD = 10f // Only log when bearing changes by more than 10 degrees

    // Add this method to your class
    private fun logBearingData(bearing: Float?, adjustedBearing: Float?) {
        // Skip logging if bearing is not valid
        if (bearing == null || bearing.isNaN()) return

        // Only log when bearing changes significantly to avoid spamming the log
        if (Math.abs(bearing - lastLoggedBearing) > BEARING_LOG_THRESHOLD) {
            Log.d(TAG, "BEARING DATA: Raw=${bearing.toInt()}°, " +
                    "Adjusted=${adjustedBearing?.toInt() ?: "N/A"}°, " +
                    "Arrow points at: ${((bearing + 180) % 360).toInt()}°")
            lastLoggedBearing = bearing
        }
    }

// In your updateNavigation method, add this line where you get the bearing value
// (Look for where navigationManager.currentBearing.value is accessed)

    // Log bearing data for debugging


    init {
        // Initialize map assistant
        mapAssistant.initialize()

        // Observe state changes from the map assistant
        viewModelScope.launch {
            mapAssistant.isNavigating.collectLatest {
                _isNavigating.value = it
            }
        }

        viewModelScope.launch {
            mapAssistant.isLoading.collectLatest {
                _isLoading.value = it
                updateReadyState()
            }
        }

        viewModelScope.launch {
            mapAssistant.currentRoute.collectLatest {
                _currentRoute.value = it
                if (it != null) {
                    // Immediately update navigation instructions when route is available
                    _navigationInstruction.value = mapAssistant.getCurrentInstruction()
                    _remainingDistance.value = mapAssistant.getRemainingDistance()

                    // Small delay to ensure UI updates before showing map
                    delay(200)

                    setRouteCalculated(true)
                    _isCalculatingRoute.value = false
                } else {
                    setRouteCalculated(false)
                    _isCalculatingRoute.value = false
                }
            }
        }
    }

    // Generate cache key for routes
    private fun generateRouteCacheKey(start: LocationDataOSRM, dest: LocationDataOSRM): String {
        return "${start.latitude},${start.longitude};${dest.latitude},${dest.longitude}"
    }

    // Check cache for existing route
    private fun getCachedRoute(start: LocationDataOSRM, dest: LocationDataOSRM): RouteData? {
        val key = generateRouteCacheKey(start, dest)
        return routeCache[key]
    }

    // Store route in cache
    private fun cacheRoute(start: LocationDataOSRM, dest: LocationDataOSRM, route: RouteData) {
        val key = generateRouteCacheKey(start, dest)
        routeCache[key] = route

        // Limit cache size to prevent memory issues
        if (routeCache.size > 10) {
            routeCache.entries.firstOrNull()?.let { routeCache.remove(it.key) }
        }
    }

    // Add this method to determine if recalculation is needed
    fun shouldRecalculateRoute(): Boolean {
        val currentUserLoc = _userLocation.value
        val savedUserLoc = _savedUserLocation.value
        val savedRoute = _savedRouteData.value

        // Always recalculate if we don't have a saved route or locations
        if (savedRoute == null || savedUserLoc == null || currentUserLoc == null) {
            return true
        }

        // Calculate distance between current and saved location
        val distanceFromSavedLocation = LocationDataOSRM.distanceBetween(currentUserLoc, savedUserLoc)

        // Recalculate if user has moved significantly (e.g., more than 100 meters)
        return distanceFromSavedLocation > 100.0
    }

    // Add this method to save the current state
    fun saveNavigationState() {
        _savedRouteData.value = _currentRoute.value
        _savedUserLocation.value = _userLocation.value
        _savedDestinationLocation.value = _destinationLocation.value
    }


    /**
     * Set colors for map display
     */
    fun setColors(routeColor: Int, userMarkerColor: Int, destinationMarkerColor: Int) {
        mapAssistant.setColors(routeColor, userMarkerColor, destinationMarkerColor)
    }

    /**
     * Set up the map view with necessary configuration
     */
    fun setupMapView(mapView: MapView) {
        mapAssistant.setupMapView(mapView)
    }

    /**
     * Update user location
     */
    fun updateUserLocation(location: LocationDataOSRM) {
        _userLocation.value = location
    }

    /**
     * Set destination location
     */
    fun setDestinationLocation(location: LocationDataOSRM) {
        _destinationLocation.value = location
    }

    /**
     * Toggle follow user mode
     */
    fun setFollowUserLocation(follow: Boolean) {
        _followUserLocation.value = follow
    }

    /**
     * Start navigation to destination with improved error handling
     */
    /**
     * Start navigation to destination with improved error handling
     */
    fun startNavigation(destinationLocation: StateFlow<LocationDataOSRM?>, mapView: MapView): Boolean {
        // Don't start another calculation if one is in progress
        if (_isCalculatingRoute.value) {
            Log.d(TAG, "Route calculation already in progress, ignoring duplicate request")
            return true
        }

        // Check if we can reuse existing route
        if (!shouldRecalculateRoute() && _savedRouteData.value != null) {
            Log.d(TAG, "Reusing existing route data instead of recalculating")
            return restoreNavigation(mapView)
        }

        // Set calculation flag
        _isCalculatingRoute.value = true

        // Reset ready states
        _mapTilesLoaded.value = false
        _routeCalculated.value = false
        _isFullyReady.value = false

        // Reset instruction and distance text
        _navigationInstruction.value = "Starten Sie die Navigation"
        _remainingDistance.value = "Entfernung wird berechnet..."

        // Setup timeout for route calculation
        setupRouteCalculationTimeout()

        val userLoc = _userLocation.value
        val destLoc = _destinationLocation.value

        if (userLoc == null || destLoc == null) {
            Log.e(TAG, "Cannot start navigation: missing user or destination location")
            _isCalculatingRoute.value = false
            return false
        }

        // Check if we have this route cached
        val cachedRoute = getCachedRoute(userLoc, destLoc)
        if (cachedRoute != null) {
            Log.d(TAG, "Using cached route instead of making network request")
            _currentRoute.value = cachedRoute
            setRouteCalculated(true)
            _isCalculatingRoute.value = false
            return true
        }

        mapAssistant.startNavigation(mapView, userLoc, destLoc) { success, error ->
            if (success) {
                // This will be called when route is successfully calculated
                // Instructions will be updated via the currentRoute collector

                // Cache successful route
                _currentRoute.value?.let { route ->
                    cacheRoute(userLoc, destLoc, route)
                }
            } else {
                Log.e(TAG, "Failed to start navigation: $error")
                // Force route calculation to be considered complete even on failure
                viewModelScope.launch {
                    setRouteCalculated(true)
                    // Provide a fallback instruction in case of failure
                    _navigationInstruction.value = "Navigation Richtung Ziel"
                    _remainingDistance.value = "Distanz nicht verfügbar"
                    _isCalculatingRoute.value = false
                }
            }
        }

        return true
    }

    /**
     * Stop navigation
     */
    fun stopNavigation() {
        mapAssistant.stopNavigation()
        _isNavigating.value = false
        _navigationInstruction.value = "Navigation beendet"
        _isCalculatingRoute.value = false
    }

    /**
     * Download map area for offline use
     */
    fun downloadMapArea(
        mapView: MapView,
        center: GeoPoint,
        radiusKm: Double,
        callback: (progress: Int, total: Int, completed: Boolean) -> Unit
    ) {
        mapAssistant.downloadMapArea(mapView, center, radiusKm, callback)
    }


    // Add this new method to restore navigation
    private fun restoreNavigation(mapView: MapView): Boolean {
        try {
            // Reset states
            _mapTilesLoaded.value = false
            _routeCalculated.value = false
            _isFullyReady.value = false

            // Get saved data
            val userLoc = _userLocation.value
            val destLoc = _savedDestinationLocation.value
            val route = _savedRouteData.value

            if (userLoc == null || destLoc == null || route == null) {
                Log.e(TAG, "Cannot restore navigation: missing saved data")
                return false
            }

            // Restore route in MapAssistant
            mapAssistant.restoreNavigation(mapView, userLoc, destLoc, route)

            // Mark route as calculated
            setRouteCalculated(true)

            // Update instruction and distance information
            _navigationInstruction.value = mapAssistant.getCurrentInstruction()
            _remainingDistance.value = mapAssistant.getRemainingDistance()

            _isNavigating.value = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore navigation: ${e.message}")
            return false
        }
    }


    /**
     * Update navigation with current user location
     * Modified to include preserveZoomAndRotation parameter and bearing logging
     */
    fun updateNavigation(
        mapView: MapView,
        preserveZoomAndRotation: Boolean = true
    ): Boolean {
        val userLoc = _userLocation.value
        val destLoc = _destinationLocation.value

        if (userLoc == null || destLoc == null) {
            return false
        }

        // Only center map if follow mode is active
        val centerMap = _followUserLocation.value

        // Pass the preserveZoomAndRotation parameter to MapAssistant
        val result = mapAssistant.updateNavigation(
            mapView,
            userLoc,
            destLoc,
            centerMap,
            preserveZoomAndRotation
        )

        // Get the current bearing for logging
        val currentBearing = mapAssistant.getCurrentBearing()
        val adjustedBearing = if (!currentBearing.isNaN()) (currentBearing + 180) % 360 else null

        // Log bearing data for debugging
        logBearingData(currentBearing, adjustedBearing)

        // Update UI state
        _navigationInstruction.value = mapAssistant.getCurrentInstruction()
        _remainingDistance.value = mapAssistant.getRemainingDistance()
        _hasArrived.value = mapAssistant.hasArrived()

        return result
    }

    /**
     * Center map on user location with improved tile loading behavior
     * Uses a temporary lower zoom level to avoid the grid pattern when tiles are loading
     */
    fun centerOnUserLocation(mapView: MapView, preserveZoomAndRotation: Boolean = false) {
        val userLoc = _userLocation.value ?: return

        // Set follow mode to true when explicitly centering
        _followUserLocation.value = true

        // Create GeoPoint from user location
        val userGeoPoint = GeoPoint(userLoc.latitude, userLoc.longitude)

        if (preserveZoomAndRotation) {
            // Store current zoom and rotation
            val currentZoom = mapView.zoomLevelDouble
            val currentRotation = mapView.mapOrientation

            // Only use zoom management if zoom is high (causing tile loading issues)
            if (currentZoom > 16.0) {
                // Use a temporary lower zoom level (has better tile coverage)
                val tempZoom = 14.0

                // First set center position with lower zoom to show already cached tiles
                mapView.controller.setZoom(tempZoom)
                mapView.controller.setCenter(userGeoPoint)

                // Launch a coroutine to restore the zoom after a short delay
                viewModelScope.launch {
                    // Wait for base tiles to load at the lower zoom level
                    delay(300)

                    // Now animate to the original zoom level
                    mapView.controller.animateTo(
                        userGeoPoint,
                        currentZoom,
                        500, // Animation duration in milliseconds
                        currentRotation
                    )
                }
            } else {
                // For lower zoom levels, just set the center without changing zoom
                mapView.controller.setCenter(userGeoPoint)
            }
        } else {
            // Traditional behavior - animate to user location with default zoom
            mapView.controller.animateTo(
                userGeoPoint,
                mapView.zoomLevelDouble,
                400,
                0f
            )
        }
    }

    /**
     * Set map tiles loaded state
     */
    fun setMapTilesLoaded(loaded: Boolean) {
        _mapTilesLoaded.value = loaded
        updateReadyState()
    }

    /**
     * Set route calculated state
     */
    fun setRouteCalculated(calculated: Boolean) {
        _routeCalculated.value = calculated
        updateReadyState()
    }

    /**
     * Update the fully ready state using simpler logic
     */
    private fun updateReadyState() {
        // Two states: minimally ready (just map) and fully ready (map + route)
        val minimallyReady = _mapTilesLoaded.value && !_isLoading.value
        val fullyReady = minimallyReady && _routeCalculated.value

        // Track state changes for partial UI updates
        if (_isMinimallyReady.value != minimallyReady) {
            _isMinimallyReady.value = minimallyReady
            Log.d(TAG, "Map minimally ready: $minimallyReady")
        }

        // Only update if state actually changes to avoid unnecessary recompositions
        if (_isFullyReady.value != fullyReady) {
            _isFullyReady.value = fullyReady
            Log.d(TAG, "App ready state changed to: $fullyReady (tiles: ${_mapTilesLoaded.value}, route: ${_routeCalculated.value}, loading: ${_isLoading.value})")
        }
    }

    /**
     * Check tile load status
     */
    private fun checkTileLoadStatus(mapView: MapView) {
        val hasVisibleTiles = mapView.overlayManager.tilesOverlay != null

        if (hasVisibleTiles) {
            setMapTilesLoaded(true)
        }
    }

    /**
     * Setup map tile loading listener with simplified timeouts
     */
    fun setupMapTileLoadingListener(mapView: MapView) {
        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                checkTileLoadStatus(mapView)
                return false
            }
        })

        // Add a fast initial check followed by a guaranteed timeout
        viewModelScope.launch {
            // Quick check for already cached tiles - much faster initial check
            delay(200)
            checkTileLoadStatus(mapView)

            // Force ready state after shorter timeout
            delay(600) // 600ms is enough for most cached tiles
            setMapTilesLoaded(true)
        }
    }

    /**
     * Setup route calculation timeout with faster response
     */
    fun setupRouteCalculationTimeout() {
        viewModelScope.launch {
            // Use a 5-second timeout which is reasonable for most API calls
            delay(5000)

            if (!_routeCalculated.value) {
                Log.w(TAG, "Route calculation timed out - forcing ready state")
                setRouteCalculated(true)

                // Provide a fallback instruction for timeout
                if (_navigationInstruction.value == "Starten Sie die Navigation" ||
                    _navigationInstruction.value == "Entfernung wird berechnet...") {
                    _navigationInstruction.value = "Navigation Richtung Ziel"
                    _remainingDistance.value = "Distanz nicht verfügbar"
                }

                _isCalculatingRoute.value = false
            }
        }

        // File: MapViewModel.kt
// These are new methods to be added to your existing MapViewModel class

        // NEW METHOD: Change navigation destination without restarting
        fun changeDestination(newDestination: LocationDataOSRM, mapView: MapView): Boolean {
            Log.d(TAG, "Changing destination to: ${newDestination.latitude}, ${newDestination.longitude}")

            // Don't change if we're calculating a route
            if (_isCalculatingRoute.value) {
                Log.d(TAG, "Route calculation in progress, ignoring destination change")
                return false
            }

            // Update destination location
            _destinationLocation.value = newDestination

            // Start navigation to the new destination
            return startNavigation(destinationLocation, mapView)
        }

        // NEW METHOD: Check if we're close to destination
        fun isNearDestination(thresholdMeters: Double = 100.0): Boolean {
            val userLoc = _userLocation.value
            val destLoc = _destinationLocation.value

            if (userLoc == null || destLoc == null) return false

            val distance = LocationDataOSRM.distanceBetween(userLoc, destLoc)
            return distance <= thresholdMeters
        }

        // NEW METHOD: Get current distance to destination
        fun getDistanceToDestination(): Double {
            val userLoc = _userLocation.value
            val destLoc = _destinationLocation.value

            if (userLoc == null || destLoc == null) return Double.MAX_VALUE

            return LocationDataOSRM.distanceBetween(userLoc, destLoc)
        }
    }

    // Add these methods to your MapViewModel class

    /**
     * Cleans up all orientation tracking resources
     */
    fun cleanupOrientation() {
        try {
            // Use the proper cleanup method from MapAssistant
            // MapAssistant internally calls navigationManager.stopOrientationTracking()
            mapAssistant.cleanup()

            Log.d(TAG, "Orientation tracking stopped and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orientation tracking: ${e.message}")
        }
    }

    /**
     * Performs complete cleanup of all map resources
     * Call this when leaving the navigation screen
     */
    fun cleanupMapResources(mapView: MapView) {
        Log.d(TAG, "Starting map resources cleanup")

        try {
            // Stop navigation if active
            if (_isNavigating.value) {
                stopNavigation()
            }

            // Clean up orientation tracking - calls mapAssistant.cleanup() which handles orientation tracking
            cleanupOrientation()

            // Clear any overlays from the map
            mapView.overlays.clear()

            // Reset state values
            _isFullyReady.value = false
            _isMinimallyReady.value = false
            _mapTilesLoaded.value = false
            _routeCalculated.value = false

            // Save navigation state before clearing current values
            saveNavigationState()

            Log.d(TAG, "Map resources fully cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during map cleanup: ${e.message}")
        }
    }



    override fun onCleared() {
        saveNavigationState()
        super.onCleared()
        mapAssistant.cleanup()
    }
}