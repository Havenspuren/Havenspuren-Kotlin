package com.havenspure.data.repository

import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.data.model.Trophy
import com.example.havenspure_kotlin_prototype.data.model.UserProgress
import com.example.havenspure_kotlin_prototype.data.model.VisitedLocation
import com.example.havenspure_kotlin_prototype.data.repository.Default.Companion.createDefaultLocations
import com.havenspure.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date

/**
 * Repository to handle tour data operations
 */
class TourRepository(
    private val tourDao: TourDao,
    private val locationDao: LocationDao
) {
    // Get all tours with their progress
    suspend fun getToursWithProgress(): Flow<List<TourWithProgress>> = flow {
        emit(tourDao.getToursWithProgress())
    }

    // Get a single tour with its locations
    suspend fun getTourWithLocations(tourId: String): Flow<TourWithLocations> = flow {
        emit(tourDao.getTourWithLocations(tourId))
    }

    // Insert default tour data from assets
    suspend fun insertDefaultTourData(tour: Tour, locations: List<Location>) {
        tourDao.insertTour(tour)
        locationDao.insertLocations(locations)
    }

    // Get next location based on current progress
    suspend fun getNextLocation(tourId: String, currentLocationOrder: Int): Location? {
        return locationDao.getLocationByOrder(tourId, currentLocationOrder + 1)
    }
}

/**
 * Repository to handle user progress and visited locations
 */
class UserProgressRepository(
    private val userProgressDao: UserProgressDao,
    private val visitedLocationDao: VisitedLocationDao,
    private val locationDao: LocationDao,
    private val trophyDao: TrophyDao
) {
    // Get progress for a specific tour
    suspend fun getProgressForTour(tourId: String): Flow<UserProgress?> = flow {
        emit(userProgressDao.getProgressForTour(tourId))
    }

    // Initialize progress for a new tour
    suspend fun initializeProgress(tourId: String) {
        val firstLocation = locationDao.getLocationByOrder(tourId, 1)
        firstLocation?.let {
            val timestamp = System.currentTimeMillis()
            val newProgress = UserProgress(
                tourId = tourId,
                lastVisitedLocationId = it.id,
                completionPercentage = 0f,
                startedAt = timestamp,
                lastUpdatedAt = timestamp
            )
            userProgressDao.insertOrUpdateProgress(newProgress)
        }
    }

    // Mark a location as visited
    suspend fun visitLocation(tourId: String, locationId: String): Boolean {
        val location = locationDao.getLocationById(locationId)
        val timestamp = System.currentTimeMillis()

        // Add to visited locations
        val visitedLocation = VisitedLocation(
            locationId = locationId,
            tourId = tourId,
            visitedAt = timestamp
        )
        visitedLocationDao.insertVisitedLocation(visitedLocation)

        // Calculate new completion percentage
        val totalLocations = locationDao.getLocationsForTour(tourId).size
        val visitedCount = visitedLocationDao.getVisitedLocationCountForTour(tourId)
        val percentage = (visitedCount.toFloat() / totalLocations) * 100

        // Update progress
        userProgressDao.updateProgress(
            tourId = tourId,
            locationId = locationId,
            percentage = percentage,
            timestamp = timestamp
        )

        // Check if there's a trophy to unlock
        if (location.hasTrophy) {
            unlockTrophyForLocation(locationId)
            return true
        }

        return false
    }

    // Unlock trophy for a location
    private suspend fun unlockTrophyForLocation(locationId: String) {
        val trophy = trophyDao.getTrophiesForTour("").find { it.locationId == locationId }
        trophy?.let {
            trophyDao.unlockTrophy(it.id, System.currentTimeMillis())
        }
    }

    // Get all unlocked trophies
    suspend fun getUnlockedTrophies(): Flow<List<Trophy>> = flow {
        emit(trophyDao.getUnlockedTrophies())
    }
}

/**
 * Repository for handling default tour data initialization
 */
class DataInitRepository(
    private val tourRepository: TourRepository,
    private val trophyDao: TrophyDao
) {
    suspend fun initializeDefaultData() {
        // Create default tour
        val defaultTour = Tour(
            id = "hafenarbeiter_helene_fink",
            title = "Hafenarbeiter mit Helene Fink",
            description = "Tour zu den Hafenarbeitern und Helene Fink's Suche nach Hannes Mirowitsch",
            imageUrl = "default_tour.jpg",
            totalLocations = 13,
            author = "AT"
        )

        // Create locations from the provided data
        val locations = createDefaultLocations(defaultTour.id)

        // Insert tour and locations
        tourRepository.insertDefaultTourData(defaultTour, locations)

        // Create trophies for locations that have them
        val trophies = createTrophiesForLocations(defaultTour.id, locations)
        trophies.forEach { trophy ->
            trophyDao.insertTrophy(trophy)
        }
    }



    private fun createTrophiesForLocations(tourId: String, locations: List<Location>): List<Trophy> {
        return locations.filter { it.hasTrophy }
            .mapIndexed { index, location ->
                Trophy(
                    id = "trophy_${tourId}_${index + 1}",
                    tourId = tourId,
                    locationId = location.id,
                    title = location.trophyTitle ?: "",
                    description = location.trophyDescription ?: "",
                    imageName = location.trophyImageName ?: "",
                    isUnlocked = false,
                    unlockedAt = null
                )
            }
    }
}