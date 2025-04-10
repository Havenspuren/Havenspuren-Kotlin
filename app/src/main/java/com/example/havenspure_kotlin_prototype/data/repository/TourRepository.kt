package com.havenspure.data.repository

import android.util.Log
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.havenspure.data.local.LocationDao
import com.havenspure.data.local.TourDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class TourRepository(
    private val tourDao: TourDao,
    private val locationDao: LocationDao
) {
    // MODIFY this method
    suspend fun getToursWithProgress(): Flow<List<TourWithProgress>> = flow {
        try {
            emit(tourDao.getToursWithProgress())
        } catch (e: Exception) {
            // Don't emit from catch block directly
            Timber.tag("TourRepository").e(e, "Error getting tours with progress")
            emit(emptyList()) // Emit empty list as fallback
        }
    }

    suspend fun getTourWithLocations(tourId: String): Flow<TourWithLocations> = flow {
        try {
            emit(tourDao.getTourWithLocations(tourId))
        } catch (e: Exception) {
            Timber.e(e, "Error getting tour with locations: $tourId")
            throw e
        }
    }

    suspend fun insertDefaultTourData(tour: Tour, locations: List<Location>) {
        try {
            tourDao.insertTour(tour)
            locationDao.insertLocations(locations)
        } catch (e: Exception) {
            Timber.e(e, "Error inserting default tour data")
            throw e
        }
    }

    suspend fun getNextLocation(tourId: String, currentLocationOrder: Int): Location? {
        return try {
            locationDao.getLocationByOrder(tourId, currentLocationOrder + 1)
        } catch (e: Exception) {
            Timber.e(e, "Error getting next location")
            null
        }
    }
}