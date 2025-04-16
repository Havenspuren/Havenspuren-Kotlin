package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TourNavigationLogic(private val context: Context) {
    private val TAG = "TourNavigationLogic"

    // States for current navigation
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _nextLocation = MutableStateFlow<Location?>(null)
    val nextLocation: StateFlow<Location?> = _nextLocation.asStateFlow()

    private val _visitedLocations = MutableStateFlow<List<String>>(emptyList())
    val visitedLocations: StateFlow<List<String>> = _visitedLocations.asStateFlow()

    private val _tourLocations = MutableStateFlow<List<Location>>(emptyList())
    val tourLocations: StateFlow<List<Location>> = _tourLocations.asStateFlow()

    private val _tourProgress = MutableStateFlow(0f)
    val tourProgress: StateFlow<Float> = _tourProgress.asStateFlow()

    private val _navigationState = MutableStateFlow(TourNavigationState.NotStarted)
    val navigationState: StateFlow<TourNavigationState> = _navigationState.asStateFlow()

    /**
     * Initialize with tour data
     */
    fun initializeWithTour(tourWithLocations: TourWithLocations, visitedLocationIds: List<String>) {
        try {
            Log.d(TAG, "Initializing tour with ${tourWithLocations.locations.size} locations")

            // Sort locations by order
            val sortedLocations = tourWithLocations.locations.sortedBy { it.order }
            _tourLocations.value = sortedLocations

            // Set visited locations
            _visitedLocations.value = visitedLocationIds
            Log.d(TAG, "Visited locations: ${visitedLocationIds.size}")

            // Calculate progress
            calculateProgress()

            // Determine current location (first unvisited location)
            determineCurrentLocation()

            Log.d(TAG, "Current location: ${_currentLocation.value?.name}")
            Log.d(TAG, "Navigation state: ${_navigationState.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tour: ${e.message}")
        }
    }

    /**
     * Calculate tour progress
     */
    private fun calculateProgress() {
        val totalLocations = _tourLocations.value.size
        if (totalLocations > 0) {
            val visitedCount = _visitedLocations.value.size
            _tourProgress.value = visitedCount.toFloat() / totalLocations
            Log.d(TAG, "Progress: ${_tourProgress.value * 100}%")
        }
    }

    /**
     * Determine the current target location based on progress
     */
    private fun determineCurrentLocation() {
        val locations = _tourLocations.value
        val visited = _visitedLocations.value

        if (locations.isEmpty()) {
            Log.w(TAG, "No locations found in tour")
            _navigationState.value = TourNavigationState.NotStarted
            return
        }

        // Find first unvisited location
        val unvisitedLocations = locations.filter { !visited.contains(it.id) }
            .sortedBy { it.order }

        Log.d(TAG, "Unvisited locations: ${unvisitedLocations.size}")

        if (unvisitedLocations.isEmpty()) {
            // All locations visited
            _currentLocation.value = null
            _nextLocation.value = null
            _navigationState.value = TourNavigationState.Completed
            Log.d(TAG, "Tour completed")
        } else {
            // Set current location to the first unvisited location
            _currentLocation.value = unvisitedLocations.first()

            // Set next location if available
            _nextLocation.value = if (unvisitedLocations.size > 1) {
                unvisitedLocations[1]
            } else {
                null
            }

            _navigationState.value = TourNavigationState.EnRoute
            Log.d(TAG, "Setting current location: ${_currentLocation.value?.name}")
        }
    }

    /**
     * Convert Location to LocationDataOSRM for map navigation
     */
    fun getLocationAsOSRM(location: Location?): LocationDataOSRM? {
        return location?.let {
            LocationDataOSRM(
                latitude = it.latitude,
                longitude = it.longitude
            )
        }
    }

    /**
     * Mark current location as visited
     */
    fun markCurrentLocationAsVisited() {
        _currentLocation.value?.let { location ->
            if (!_visitedLocations.value.contains(location.id)) {
                val updatedList = _visitedLocations.value + location.id
                _visitedLocations.value = updatedList
                _navigationState.value = TourNavigationState.AtLocation
                calculateProgress()
                Log.d(TAG, "Marked location as visited: ${location.name}")
            }
        }
    }

    /**
     * Proceed to next location
     */
    fun proceedToNextLocation() {
        Log.d(TAG, "Proceeding to next location")
        // Update navigation state
        _navigationState.value = TourNavigationState.EnRoute

        // Determine new current and next locations
        determineCurrentLocation()
    }

    /**
     * Get current location index (0-based)
     */
    fun getCurrentLocationIndex(): Int {
        val current = _currentLocation.value ?: return 0
        return _tourLocations.value.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    }

    /**
     * Calculate distance between two points (in meters)
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Format distance for display
     */
    fun formatDistance(distanceInMeters: Float): String {
        return when {
            distanceInMeters < 1000 -> "${distanceInMeters.toInt()} m"
            else -> String.format("%.1f km", distanceInMeters / 1000)
        }
    }

    /**
     * Check if user is near the current location
     */
    fun isNearCurrentLocation(userLat: Double, userLng: Double, thresholdMeters: Float = 25f): Boolean {
        val currentLoc = _currentLocation.value ?: return false
        val distance = calculateDistance(userLat, userLng, currentLoc.latitude, currentLoc.longitude)
        return distance <= thresholdMeters
    }
}