package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import kotlin.math.round



/**
 * Coordinates the navigation between different locations in a tour.
 * Manages the state of the current location, next location, and overall tour progress.
 */
class TourNavigationCoordinator(private val context: Context) : ViewModel() {

    // Current navigation state
    private val _navigationState = MutableStateFlow(TourNavigationState.NotStarted)
    val navigationState: StateFlow<TourNavigationState> = _navigationState.asStateFlow()

    // Current and next location in the tour
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _nextLocation = MutableStateFlow<Location?>(null)
    val nextLocation: StateFlow<Location?> = _nextLocation.asStateFlow()

    // Proximity detection
    private val _isInProximity = MutableStateFlow(false)
    val isInProximity: StateFlow<Boolean> = _isInProximity.asStateFlow()

    // Tour completion tracking
    private val _completionPercentage = MutableStateFlow(0f)
    val completionPercentage: StateFlow<Float> = _completionPercentage.asStateFlow()

    private val _visitedLocations = MutableStateFlow<List<String>>(emptyList())
    val visitedLocations: StateFlow<List<String>> = _visitedLocations.asStateFlow()

    // Tour data
    private var tour: TourWithLocations? = null
    private var sortedLocations: List<Location> = emptyList()

    /**
     * Initialize the tour navigation with the provided tour data.
     * Sets up the first location as the current destination.
     */
    fun initializeTour(tourWithLocations: TourWithLocations, mapView: MapView) {
        viewModelScope.launch {
            tour = tourWithLocations

            // Sort locations by order property
            sortedLocations = tourWithLocations.locations.sortedBy { it.order }

            if (sortedLocations.isNotEmpty()) {
                // Set first location as the current destination
                _currentLocation.value = sortedLocations.first()

                // Set second location as the next destination if available
                _nextLocation.value = if (sortedLocations.size > 1) sortedLocations[1] else null

                // Update navigation state
                _navigationState.value = TourNavigationState.EnRoute

                // Initialize or recalculate progress
                calculateProgress()
            }
        }
    }

    /**
     * Mark the current location as visited and update the tour progress.
     */
    fun markLocationAsVisited(mapView: MapView) {
        viewModelScope.launch {
            currentLocation.value?.let { location ->
                // Add current location to visited locations if not already present
                if (!_visitedLocations.value.contains(location.id)) {
                    _visitedLocations.value = _visitedLocations.value + location.id
                }

                // Update navigation state
                _navigationState.value = TourNavigationState.AtLocation

                // Recalculate tour progress
                calculateProgress()
            }
        }
    }

    /**
     * Proceed to the next location in the tour.
     * If there are no more locations, mark the tour as completed.
     */
    fun proceedToNextLocation(mapView: MapView) {
        viewModelScope.launch {
            nextLocation.value?.let { next ->
                // Update current and next locations
                _currentLocation.value = next

                // Find the index of the next location
                val nextIndex = sortedLocations.indexOf(next)

                // Determine if there's a location after the next one
                _nextLocation.value = if (nextIndex < sortedLocations.size - 1) {
                    sortedLocations[nextIndex + 1]
                } else {
                    null
                }

                // Reset proximity detection
                _isInProximity.value = false

                // Update navigation state
                _navigationState.value = TourNavigationState.EnRoute
            } ?: run {
                // No more locations, mark tour as completed
                _navigationState.value = TourNavigationState.Completed
            }
        }
    }

    /**
     * Calculate the tour progress based on visited locations.
     */
    private fun calculateProgress() {
        tour?.let { t ->
            val totalLocations = t.locations.size
            if (totalLocations > 0) {
                val visitedCount = _visitedLocations.value.size
                _completionPercentage.value = round((visitedCount.toFloat() / totalLocations) * 100)
            }
        }
    }

    /**
     * Update the proximity detection based on the user's location.
     * Called periodically from the navigation screen.
     */
    fun updateProximityStatus(isInProximity: Boolean) {
        _isInProximity.value = isInProximity
    }
}