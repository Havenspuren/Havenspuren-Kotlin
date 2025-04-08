package com.example.havenspure_kotlin_prototype.ViewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.havenspure.data.repository.TourRepository
import com.havenspure.data.repository.UserProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
            try {
                _uiState.value = ToursUiState.Loading

                // Get tours with progress
                val toursWithProgress = tourRepository.getToursWithProgress().first()

                if (toursWithProgress.isEmpty()) {
                    _uiState.value = ToursUiState.Empty
                } else {
                    _uiState.value = ToursUiState.Success(toursWithProgress)
                }
            } catch (e: Exception) {
                _uiState.value = ToursUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Start a tour by initializing progress
    fun startTour(tourId: String) {
        viewModelScope.launch {
            userProgressRepository.initializeProgress(tourId)
            // Reload tours to show updated progress
            loadTours()
        }
    }

    // Get a specific tour with progress
    fun getTourWithProgress(tourId: String, onResult: (TourWithProgress?) -> Unit) {
        viewModelScope.launch {
            try {
                val toursWithProgress = tourRepository.getToursWithProgress().first()
                val tourWithProgress = toursWithProgress.find { it.tour.id == tourId }
                onResult(tourWithProgress)
            } catch (e: Exception) {
                onResult(null)
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