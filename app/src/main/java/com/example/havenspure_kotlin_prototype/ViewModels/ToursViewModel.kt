package com.example.havenspure_kotlin_prototype.ViewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.havenspure.data.repository.TourRepository
import com.havenspure.data.repository.UserProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ToursViewModel(
    private val tourRepository: TourRepository,
    private val userProgressRepository: UserProgressRepository
) : ViewModel() {

    // State for tours list
    private val _uiState = MutableStateFlow<ToursUiState>(ToursUiState.Loading)
    val uiState: StateFlow<ToursUiState> = _uiState.asStateFlow()

    init {
        loadTours()
    }

    // Load all tours with their progress information
    private fun loadTours() {
        viewModelScope.launch {
            _uiState.value = ToursUiState.Loading

            tourRepository.getToursWithProgress()
                .flowOn(Dispatchers.IO)
                .catch { exception ->
                    Log.e("ToursViewModel", "Error loading tours", exception)
                    _uiState.value = ToursUiState.Error(exception.message ?: "Unknown error")
                }
                .collectLatest { toursWithProgress ->
                    if (toursWithProgress.isEmpty()) {
                        _uiState.value = ToursUiState.Empty
                    } else {
                        _uiState.value = ToursUiState.Success(toursWithProgress)
                    }
                }
        }
    }

    // Start a tour by initializing progress
    fun startTour(tourId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userProgressRepository.initializeProgress(tourId)
                // Reload tours to show updated progress
                loadTours()
            } catch (e: Exception) {
                Log.e("ToursViewModel", "Error starting tour", e)
                _uiState.value = ToursUiState.Error("Failed to start tour: ${e.message}")
            }
        }
    }

    // Get a specific tour with progress - using proper Flow handling
    fun getTourWithProgress(tourId: String, onResult: (TourWithProgress?) -> Unit) {
        viewModelScope.launch {
            tourRepository.getToursWithProgress()
                .flowOn(Dispatchers.IO)
                .catch {
                    Log.e("ToursViewModel", "Error getting tour with progress", it)
                    onResult(null)
                }
                .collectLatest { toursWithProgress ->
                    val tourWithProgress = toursWithProgress.find { it.tour.id == tourId }
                    onResult(tourWithProgress)
                }
        }
    }

    // NEW METHOD: Get a specific tour with its locations
    fun getTourWithLocations(tourId: String, onResult: (TourWithLocations?) -> Unit) {
        viewModelScope.launch {
            try {
                // Assuming tourRepository has this method - if not, you'll need to add it
                tourRepository.getTourWithLocations(tourId)
                    .flowOn(Dispatchers.IO)
                    .catch {
                        Log.e("ToursViewModel", "Error getting tour locations", it)
                        onResult(null)
                    }
                    .collectLatest { tourWithLocations ->
                        onResult(tourWithLocations)
                    }
            } catch (e: Exception) {
                Log.e("ToursViewModel", "Error fetching tour locations", e)
                onResult(null)
            }
        }
    }

    // NEW METHOD: Get both tour progress and locations in one call
    fun getTourWithProgressAndLocations(
        tourId: String,
        onResult: (tourWithProgress: TourWithProgress?, tourWithLocations: TourWithLocations?) -> Unit
    ) {
        viewModelScope.launch {
            var progress: TourWithProgress? = null
            var locations: TourWithLocations? = null

            // Get progress
            tourRepository.getToursWithProgress()
                .flowOn(Dispatchers.IO)
                .catch {
                    Log.e("ToursViewModel", "Error getting tour progress", it)
                }
                .collectLatest { toursWithProgress ->
                    progress = toursWithProgress.find { it.tour.id == tourId }

                    // After getting progress, get locations
                    try {
                        tourRepository.getTourWithLocations(tourId)
                            .flowOn(Dispatchers.IO)
                            .catch {
                                Log.e("ToursViewModel", "Error getting tour locations", it)
                                onResult(progress, null)
                            }
                            .collectLatest { tourWithLocations ->
                                locations = tourWithLocations
                                onResult(progress, locations)
                            }
                    } catch (e: Exception) {
                        Log.e("ToursViewModel", "Error fetching tour locations", e)
                        onResult(progress, null)
                    }
                }
        }
    }

    // Refresh the tours list
    fun refreshTours() {
        loadTours()
    }
}

// UI State classes
sealed class ToursUiState {
    object Loading : ToursUiState()
    object Empty : ToursUiState()
    data class Success(val tours: List<TourWithProgress>) : ToursUiState()
    data class Error(val message: String) : ToursUiState()
}