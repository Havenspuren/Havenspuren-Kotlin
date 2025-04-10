package com.havenspure.data.repository

import com.example.havenspure_kotlin_prototype.data.model.Trophy
import com.example.havenspure_kotlin_prototype.data.model.UserProgress
import com.example.havenspure_kotlin_prototype.data.model.VisitedLocation
import com.havenspure.data.local.LocationDao
import com.havenspure.data.local.TrophyDao
import com.havenspure.data.local.UserProgressDao
import com.havenspure.data.local.VisitedLocationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class UserProgressRepository(
    private val userProgressDao: UserProgressDao,
    private val visitedLocationDao: VisitedLocationDao,
    private val locationDao: LocationDao,
    private val trophyDao: TrophyDao
) {
    suspend fun getProgressForTour(tourId: String): Flow<UserProgress?> = flow {
        try {
            emit(userProgressDao.getProgressForTour(tourId))
        } catch (e: Exception) {
            Timber.e(e, "Error getting progress for tour: $tourId")
            emit(null)
        }
    }

    suspend fun initializeProgress(tourId: String) {
        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Error initializing progress for tour: $tourId")
            throw e
        }
    }

    suspend fun visitLocation(tourId: String, locationId: String): Boolean {
        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Error visiting location: $locationId")
            throw e
        }
    }

    private suspend fun unlockTrophyForLocation(locationId: String) {
        try {
            val trophies = trophyDao.getTrophiesForTour("")
            val trophy = trophies.find { it.locationId == locationId }
            trophy?.let {
                trophyDao.unlockTrophy(it.id, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Timber.e(e, "Error unlocking trophy for location: $locationId")
        }
    }

    suspend fun getUnlockedTrophies(): Flow<List<Trophy>> = flow {
        try {
            emit(trophyDao.getUnlockedTrophies())
        } catch (e: Exception) {
            Timber.e(e, "Error getting unlocked trophies")
            emit(emptyList())
        }
    }
}