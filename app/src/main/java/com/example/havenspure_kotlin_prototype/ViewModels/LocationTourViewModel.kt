/*
package com.example.havenspure_kotlin_prototype.ViewModels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.Utils.AudioUtils
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.havenspure.data.repository.TourRepository
import com.havenspure.data.repository.UserProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class LocationTourViewModel(
    private val tourRepository: TourRepository,
    private val userProgressRepository: UserProgressRepository,
    private val audioUtils: AudioUtils
) : ViewModel() {

    // State for tour location data
    private val _uiState = MutableStateFlow<LocationTourUiState>(LocationTourUiState.Loading)
    val uiState: StateFlow<LocationTourUiState> = _uiState.asStateFlow()

    // Audio playback state
    private val _audioState = MutableStateFlow<AudioState>(AudioState.Stopped)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    // Trophy notification state
    private val _trophyState = MutableStateFlow<TrophyState?>(null)
    val trophyState: StateFlow<TrophyState?> = _trophyState.asStateFlow()

    // Location proximity detection
    private val _nearbyLocation = mutableStateOf<Location?>(null)
    val nearbyLocation: State<Location?> = _nearbyLocation

    // Load location details
    fun loadLocation(tourId: String, locationId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = LocationTourUiState.Loading

                val tourWithLocations = tourRepository.getTourWithLocations(tourId).first()
                val location = tourWithLocations.locations.find { it.id == locationId }

                if (location != null) {
                    _uiState.value = LocationTourUiState.Success(location)
                } else {
                    _uiState.value = LocationTourUiState.Error("Location not found")
                }
            } catch (e: Exception) {
                _uiState.value = LocationTourUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Play audio for a location
    fun playAudio(audioFileName: String) {
        _audioState.value = AudioState.Loading

        audioUtils.playAudio(audioFileName) {
            // On completion callback
            _audioState.value = AudioState.Stopped
        }

        _audioState.value = AudioState.Playing(audioFileName)
    }

    // Stop currently playing audio
    fun stopAudio() {
        audioUtils.release()
        _audioState.value = AudioState.Stopped
    }

    // Mark location as visited
    fun visitLocation(tourId: String, locationId: String) {
        viewModelScope.launch {
            try {
                val trophyUnlocked = userProgressRepository.visitLocation(tourId, locationId)

                if (trophyUnlocked) {
                    // Get location to show trophy info
                    val tourWithLocations = tourRepository.getTourWithLocations(tourId).first()
                    val location = tourWithLocations.locations.find { it.id == locationId }

                    location?.let { loc ->
                        if (loc.hasTrophy) {
                            _trophyState.value = TrophyState(
                                title = loc.trophyTitle ?: "",
                                description = loc.trophyDescription ?: "",
                                imageName = loc.trophyImageName ?: ""
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error silently to avoid disrupting user experience
            }
        }
    }

    // Check if user is near a tour location
    fun checkProximityToLocation(userLocation: LocationData, tourId: String, proximityThresholdMeters: Float = 25f) {
        viewModelScope.launch {
            try {
                // Get all locations for the tour
                val tourWithLocations = tourRepository.getTourWithLocations(tourId).first()
                val locationsList = tourWithLocations.locations

                // Find the closest location within threshold
                var closestLocation: Location? = null
                var minDistance = Float.MAX_VALUE

                for (loc in locationsList) {
                    val distance = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        loc.latitude, loc.longitude
                    )

                    if (distance < proximityThresholdMeters && distance < minDistance) {
                        minDistance = distance
                        closestLocation = loc
                    }
                }

                _nearbyLocation.value = closestLocation
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    // Get next location in tour
    fun getNextLocation(tourId: String, currentLocationOrder: Int) {
        viewModelScope.launch {
            try {
                val nextLocation = tourRepository.getNextLocation(tourId, currentLocationOrder)
                if (nextLocation != null) {
                    _uiState.value = LocationTourUiState.Success(nextLocation)
                }
            } catch (e: Exception) {
                _uiState.value = LocationTourUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Clean up resources when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        audioUtils.release()
    }

    // Dismiss trophy notification
    fun dismissTrophy() {
        _trophyState.value = null
    }

    // Helper method to calculate distance between two coordinates
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    // File: LocationTourViewModel.kt
// These are new methods to be added to your existing LocationTourViewModel

    // NEW METHOD: Get all visited locations for a tour
    fun getVisitedLocations(tourId: String): Flow<List<String>> = flow {
        viewModelScope.launch {
            try {
                val visitedLocations = userProgressRepository.getVisitedLocationsForTour(tourId)
                    .map { it.locationId }
                emit(visitedLocations)
            } catch (e: Exception) {
                emit(emptyList<String>())
            }
        }
    }

    // NEW METHOD: Get next location after the given one
    fun getNextLocation(tourId: String, currentLocationOrder: Int, onResult: (Location?) -> Unit) {
        viewModelScope.launch {
            try {
                val nextLocation = tourRepository.getNextLocation(tourId, currentLocationOrder)
                onResult(nextLocation)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // NEW METHOD: Check if a location is visited
    fun isLocationVisited(locationId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val isVisited = userProgressRepository.isLocationVisited(locationId)
                onResult(isVisited)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}

// UI state for location tour
sealed class LocationTourUiState {
    object Loading : LocationTourUiState()
    data class Success(val location: Location) : LocationTourUiState()
    data class Error(val message: String) : LocationTourUiState()
}

// Audio playback state
sealed class AudioState {
    object Loading : AudioState()
    data class Playing(val audioFileName: String) : AudioState()
    object Stopped : AudioState()
}

// Trophy state data class
data class TrophyState(
    val title: String,
    val description: String,
    val imageName: String
)

 */