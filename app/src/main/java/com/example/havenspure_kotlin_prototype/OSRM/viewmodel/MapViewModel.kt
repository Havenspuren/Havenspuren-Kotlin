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
    fun startNavigation(mapView: MapView): Boolean {
        // Don't start another calculation if one is in progress
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

        mapAssistant.startNavigation(mapView, userLoc, destLoc) { success, error ->
            if (success) {
                // This will be called when route is successfully calculated
                // Instructions will be updated via the currentRoute collector
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
     */
    fun updateNavigation(mapView: MapView): Boolean {
        val userLoc = _userLocation.value
        val destLoc = _destinationLocation.value

        if (userLoc == null || destLoc == null) {
            return false
        }

        // Only center map if follow mode is active
        val centerMap = _followUserLocation.value
        val result = mapAssistant.updateNavigation(mapView, userLoc, destLoc, centerMap)

        // Update UI state
        _navigationInstruction.value = mapAssistant.getCurrentInstruction()
        _remainingDistance.value = mapAssistant.getRemainingDistance()
        _hasArrived.value = mapAssistant.hasArrived()

        return result
    }

    /**
     * Center map on user location
     */
    fun centerOnUserLocation(mapView: MapView) {
        val userLoc = _userLocation.value ?: return

        // Set follow mode to true when explicitly centering
        _followUserLocation.value = true
        mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude))
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
        // Consider fully ready when maps are loaded, route is calculated, and not loading
        val newReadyState = _mapTilesLoaded.value && _routeCalculated.value && !_isLoading.value

        // Only update if state actually changes to avoid unnecessary recompositions
        if (_isFullyReady.value != newReadyState) {
            _isFullyReady.value = newReadyState
            Log.d(TAG, "App ready state changed to: $newReadyState (tiles: ${_mapTilesLoaded.value}, route: ${_routeCalculated.value}, loading: ${_isLoading.value})")
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
            // Quick check for already cached tiles
            delay(500)
            checkTileLoadStatus(mapView)

            // Force ready state after timeout
            delay(1500) // 1.5 seconds is enough for most cached tiles
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
    }

    override fun onCleared() {
        saveNavigationState()
        super.onCleared()
        mapAssistant.cleanup()
    }
}