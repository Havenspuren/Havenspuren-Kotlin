package com.havenspure.data.repository

import android.util.Log
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.data.model.Trophy
import com.example.havenspure_kotlin_prototype.data.repository.Default.Companion.createDefaultLocations
import com.havenspure.data.local.TrophyDao
import timber.log.Timber

class DataInitRepository(
    private val tourRepository: TourRepository,
    private val trophyDao: TrophyDao
) {
    // MODIFY this method
    suspend fun initializeDefaultData() {
        try {
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

            // Use the repository to insert tour and locations
            tourRepository.insertDefaultTourData(defaultTour, locations)

            // Create trophies for locations that have them
            val trophies = createTrophiesForLocations(defaultTour.id, locations)
            trophies.forEach { trophy ->
                trophyDao.insertTrophy(trophy)
            }

            Timber.tag("DataInitRepository").d("Default data initialized successfully")
        } catch (e: Exception) {
            Timber.tag("DataInitRepository").e(e, "Error initializing default data")
            // Don't re-throw the exception - allow the app to continue
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