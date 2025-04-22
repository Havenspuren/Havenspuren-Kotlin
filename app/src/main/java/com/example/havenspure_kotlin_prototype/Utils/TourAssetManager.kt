package com.example.havenspure_kotlin_prototype.Utils

import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.data.model.Location
import java.io.IOException

/**
 * Manages access to tour-specific assets like audio and images
 */
class TourAssetManager(private val context: Context) {
    private val TAG = "TourAssetManager"

    /**
     * Gets the full asset path for an audio file
     * @param tourId The tour identifier
     * @param fileName The audio file name
     * @return The full asset path
     */
    fun getAudioPath(tourId: String, fileName: String): String {
        // Clean tourId to ensure proper format
        val cleanTourId = tourId.trim().let {
            if (it.startsWith("tour_")) it else "tour_$it"
        }
        return "$cleanTourId/audio/$fileName"
    }

    /**
     * Gets the full asset path for an image file
     * @param tourId The tour identifier
     * @param locationId The location identifier or order number
     * @return The full asset path
     */
    fun getImagePath(tourId: String, locationId: String): String {
        // Clean tourId to ensure proper format
        val cleanTourId = tourId.trim().let {
            if (it.startsWith("tour_")) it else "tour_$it"
        }

        // Extract location number from locationId (e.g., "tour_default_1" -> "1")
        val locationNumber = when {
            locationId.contains("_") -> locationId.substringAfterLast("_", "0")
            locationId.toIntOrNull() != null -> locationId  // It's already a number
            else -> "0"  // Default fallback
        }

        return "$cleanTourId/images/location$locationNumber"
    }

    /**
     * Checks if an audio file exists in assets
     * @param tourId The tour identifier
     * @param fileName The audio file name
     * @return True if the file exists
     */
    fun audioFileExists(tourId: String, fileName: String): Boolean {
        val path = getAudioPath(tourId, fileName)
        return fileExistsInAssets(path)
    }

    /**
     * Checks if an image file exists in assets
     * @param tourId The tour identifier
     * @param locationId The location identifier
     * @return True if the file exists
     */
    fun imageFileExists(tourId: String, locationId: String): Boolean {
        val path = getImagePath(tourId, locationId)
        return fileExistsInAssets(path)
    }

    /**
     * Validates that all required assets for a tour exist
     * @param tourId The tour identifier
     * @param locations The list of locations to validate assets for
     * @return List of missing assets, empty if all assets exist
     */
    fun validateTourAssets(tourId: String, locations: List<Location>): List<String> {
        val missingAssets = mutableListOf<String>()

        locations.forEach { location ->
            // Check audio file if specified
            if (!location.audioFileName.isNullOrEmpty()) {
                if (!audioFileExists(tourId, location.audioFileName)) {
                    missingAssets.add("Audio: ${location.audioFileName}")
                    Log.w(TAG, "Missing audio file for location ${location.name}: ${location.audioFileName}")
                }
            }

            // Check image file
            if (!imageFileExists(tourId, location.id)) {
                missingAssets.add("Image: location${location.id.substringAfterLast("_", "0")}")
                Log.w(TAG, "Missing image file for location ${location.name}")
            }
        }

        return missingAssets
    }

    /**
     * Helper method to check if a file exists in assets
     * @param path The asset path to check
     * @return True if the file exists
     */
    private fun fileExistsInAssets(path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (e: IOException) {
            Log.d(TAG, "File not found in assets: $path")
            false
        }
    }

    /**
     * Lists all available tours in assets
     * @return List of tour IDs found in assets
     */
    fun listAvailableTours(): List<String> {
        return try {
            context.assets.list("")
                ?.filter { it.startsWith("tour_") }
                ?.map { it.removePrefix("tour_") }
                ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Error listing available tours", e)
            emptyList()
        }
    }

    /**
     * Lists all audio files for a tour
     * @param tourId The tour identifier
     * @return List of audio filenames
     */
    fun listTourAudioFiles(tourId: String): List<String> {
        val cleanTourId = tourId.trim().let {
            if (it.startsWith("tour_")) it else "tour_$it"
        }

        return try {
            val audioPath = "$cleanTourId/audio"
            context.assets.list(audioPath)?.toList() ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Error listing audio files for tour $tourId", e)
            emptyList()
        }
    }

    /**
     * Lists all image files for a tour
     * @param tourId The tour identifier
     * @return List of image filenames
     */
    fun listTourImageFiles(tourId: String): List<String> {
        val cleanTourId = tourId.trim().let {
            if (it.startsWith("tour_")) it else "tour_$it"
        }

        return try {
            val imagesPath = "$cleanTourId/images"
            context.assets.list(imagesPath)?.toList() ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Error listing image files for tour $tourId", e)
            emptyList()
        }
    }
}