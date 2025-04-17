package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView

/**
 * Navigation states for the tour
 */
enum class TourNavigationState {
    NotStarted,
    EnRoute,
    AtLocation,
    Completed
}

/**
 * Consolidated class that handles all tour navigation functionality.
 */
class TourNavigator(private val context: Context) : ViewModel() {
    private val TAG = "TourNavigator"

    // Default proximity threshold - now set to 150 meters
    private val DEFAULT_PROXIMITY_THRESHOLD = 150f

    // Current navigation state
    private val _navigationState = MutableStateFlow(TourNavigationState.NotStarted)
    val navigationState: StateFlow<TourNavigationState> = _navigationState.asStateFlow()

    // Current and next location in the tour
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _nextLocation = MutableStateFlow<Location?>(null)
    val nextLocation: StateFlow<Location?> = _nextLocation.asStateFlow()

    // Tour data
    private val _tourLocations = MutableStateFlow<List<Location>>(emptyList())
    val tourLocations: StateFlow<List<Location>> = _tourLocations.asStateFlow()

    // Visited locations tracking
    private val _visitedLocations = MutableStateFlow<List<String>>(emptyList())
    val visitedLocations: StateFlow<List<String>> = _visitedLocations.asStateFlow()

    // Tour progress
    private val _tourProgress = MutableStateFlow(0f)
    val tourProgress: StateFlow<Float> = _tourProgress.asStateFlow()

    // Proximity detection
    private val _isInProximity = MutableStateFlow(false)
    val isInProximity: StateFlow<Boolean> = _isInProximity.asStateFlow()

    // Current tour ID for reference
    private var currentTourId: String? = null

    /**
     * Initialize with tour data
     * @param tourWithLocations The tour data containing all locations
     * @param visitedLocationIds List of already visited location IDs
     */
    fun initializeWithTour(tourWithLocations: TourWithLocations, visitedLocationIds: List<String>) {
        try {
            Log.d(TAG, "Initializing tour with ${tourWithLocations.locations.size} locations")
            currentTourId = tourWithLocations.tour.id

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
     * Mark current location as visited and update database
     */
    fun markCurrentLocationAsVisited() {
        _currentLocation.value?.let { location ->
            if (!_visitedLocations.value.contains(location.id)) {
                // Update local state
                val updatedList = _visitedLocations.value + location.id
                _visitedLocations.value = updatedList
                _navigationState.value = TourNavigationState.AtLocation
                calculateProgress()

                // Update database
                viewModelScope.launch {
                    currentTourId?.let { tourId ->
                        try {
                            Graph.getInstance().userProgressRepository.visitLocation(tourId, location.id)
                            Log.d(TAG, "Location marked as visited in database: ${location.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating database: ${e.message}")
                        }
                    }
                }

                Log.d(TAG, "Marked location as visited: ${location.name}")
            }
        }
    }

    /**
     * Proceed to next location in the tour
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
     * Check if user is near the current location using the default threshold
     * @param userLat User's current latitude
     * @param userLng User's current longitude
     * @return True if user is within threshold distance of current location
     */
    fun isNearCurrentLocation(userLat: Double, userLng: Double): Boolean {
        return isNearCurrentLocation(userLat, userLng, DEFAULT_PROXIMITY_THRESHOLD)
    }

    /**
     * Check if user is near the current location
     * @param userLat User's current latitude
     * @param userLng User's current longitude
     * @param thresholdMeters Distance threshold in meters to consider "near"
     * @return True if user is within threshold distance of current location
     */
    fun isNearCurrentLocation(userLat: Double, userLng: Double, thresholdMeters: Float): Boolean {
        val currentLoc = _currentLocation.value ?: return false
        val distance = calculateDistance(userLat, userLng, currentLoc.latitude, currentLoc.longitude)
        val isNear = distance <= thresholdMeters

        // Update proximity state
        if (_isInProximity.value != isNear) {
            _isInProximity.value = isNear
            Log.d(TAG, "Proximity changed to: $isNear (distance: ${formatDistance(distance)})")
        }

        return isNear
    }

    /**
     * Update proximity status directly
     * This can be called from UI components that need to update the proximity state
     */
    fun updateProximityStatus(isInProximity: Boolean) {
        if (this._isInProximity.value != isInProximity) {
            this._isInProximity.value = isInProximity
            Log.d(TAG, "Proximity status updated to: $isInProximity")
        }
    }

    /**
     * Gets the audio filename for the current location if available
     * @return The audio filename that should be played, or null if none
     */
    fun getCurrentLocationAudioFile(): String? {
        return _currentLocation.value?.audioFileName
    }

    /**
     * Gets the current distance to the target location
     * @param userLat User's current latitude
     * @param userLng User's current longitude
     * @return Distance in meters to the current location, or -1 if no current location
     */
    fun getDistanceToCurrentLocation(userLat: Double, userLng: Double): Float {
        val currentLoc = _currentLocation.value ?: return -1f
        return calculateDistance(userLat, userLng, currentLoc.latitude, currentLoc.longitude)
    }
}