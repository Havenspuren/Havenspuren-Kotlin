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
        // Remove any existing "tour_" prefix if present
        val cleanTourId = tourId.removePrefix("tour_")
        val path = "$cleanTourId/audio/$fileName"

        // Add extension if missing
        return if (path.contains(".")) path else "$path.mp3"
    }

    /**
     * Gets the full asset path for an image file
     * @param tourId The tour identifier
     * @param locationId The location identifier or order number
     * @return The full asset path
     */
    fun getImagePath(tourId: String, locationId: String): String {
        val cleanTourId = tourId.removePrefix("tour_")
        val locationNumber = locationId.substringAfterLast("_", "0")
        return "$cleanTourId/images/location$locationNumber.jpg" // Add extension
    }

    /**
     * Checks if an audio file exists in assets
     * @param tourId The tour identifier
     * @param fileName The audio file name
     * @return True if the file exists
     */
    fun audioFileExists(tourId: String, fileName: String): Boolean {
        val path = getAudioPath(tourId, fileName)
        val exists = fileExistsInAssets(path)
        if (exists) {
            Log.d(TAG, "✅ Audio file exists: $path")
        } else {
            Log.w(TAG, "❌ Audio file not found: $path")
        }
        return exists
    }

    /**
     * Checks if an image file exists in assets
     * @param tourId The tour identifier
     * @param locationId The location identifier
     * @return True if the file exists
     */
    fun imageFileExists(tourId: String, locationId: String): Boolean {
        val path = getImagePath(tourId, locationId)
        val exists = fileExistsInAssets(path)
        if (exists) {
            Log.d(TAG, "✅ Image file exists: $path")
        } else {
            Log.w(TAG, "❌ Image file not found: $path")
        }
        return exists
    }

    /**
     * Validates that all required assets for a tour exist
     * @param tourId The tour identifier
     * @param locations The list of locations to validate assets for
     * @return List of missing assets, empty if all assets exist
     */
    fun validateTourAssets(tourId: String, locations: List<Location>): List<String> {
        Log.i(TAG, "Validating assets for tour: $tourId with ${locations.size} locations")
        val missingAssets = mutableListOf<String>()
        var audioCount = 0
        var imageCount = 0

        locations.forEach { location ->
            // Check audio file if specified
            if (!location.audioFileName.isNullOrEmpty()) {
                if (!audioFileExists(tourId, location.audioFileName)) {
                    missingAssets.add("Audio: ${location.audioFileName}")
                    Log.w(TAG, "Missing audio file for location ${location.name}: ${location.audioFileName}")
                } else {
                    audioCount++
                }
            }

            // Check image file
            if (!imageFileExists(tourId, location.id)) {
                missingAssets.add("Image: location${location.id.substringAfterLast("_", "0")}")
                Log.w(TAG, "Missing image file for location ${location.name}")
            } else {
                imageCount++
            }
        }

        if (missingAssets.isEmpty()) {
            Log.i(TAG, "✅ All assets validated successfully for tour $tourId: $audioCount audio files, $imageCount image files")
        } else {
            Log.w(TAG, "❌ Found ${missingAssets.size} missing assets for tour $tourId. Found: $audioCount audio files, $imageCount image files")
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
            context.assets.open(path).use {
                Log.v(TAG, "Successfully opened asset file: $path")
                true
            }
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
        Log.d(TAG, "Listing available tours...")
        return try {
            val tours = context.assets.list("")
                ?.filter { !it.contains(".") } // Only directories
                ?: emptyList()

            Log.i(TAG, "Found ${tours.size} available tours: ${tours.joinToString()}")
            tours
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
        Log.d(TAG, "Listing audio files for tour: $tourId")

        return try {
            val audioPath = "$tourId/audio"
            val files = context.assets.list(audioPath)?.toList() ?: emptyList()
            Log.i(TAG, "Found ${files.size} audio files for tour $tourId: ${files.joinToString()}")
            files
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
        Log.d(TAG, "Listing image files for tour: $tourId")

        return try {
            val imagesPath = "$tourId/images"
            val files = context.assets.list(imagesPath)?.toList() ?: emptyList()
            Log.i(TAG, "Found ${files.size} image files for tour $tourId: ${files.joinToString()}")
            files
        } catch (e: IOException) {
            Log.e(TAG, "Error listing image files for tour $tourId", e)
            emptyList()
        }
    }

    /**
     * Load an asset and log its success or failure
     * Can be used to preload assets for verification
     * @param path The asset path to load
     * @return True if successfully loaded
     */
    fun loadAsset(path: String): Boolean {
        Log.d(TAG, "Attempting to load asset: $path")
        return try {
            context.assets.open(path).use { stream ->
                // Just reading the first few bytes to verify it loads
                val buffer = ByteArray(16)
                stream.read(buffer)
                Log.i(TAG, "✅ Successfully loaded asset: $path")
                true
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to load asset: $path", e)
            false
        }
    }

    /**
     * Preload all assets for a tour to verify they can be loaded
     * @param tourId The tour identifier
     * @param locations List of locations
     * @return Map of asset paths to their load status
     */
    fun preloadTourAssets(tourId: String, locations: List<Location>): Map<String, Boolean> {
        Log.i(TAG, "Preloading all assets for tour: $tourId")
        val results = mutableMapOf<String, Boolean>()

        locations.forEach { location ->
            // Preload audio if specified
            if (location.audioFileName.isNotEmpty()) {
                val audioPath = getAudioPath(tourId, location.audioFileName)
                results[audioPath] = loadAsset(audioPath)
            }

            // Preload image
            val imagePath = getImagePath(tourId, location.id)
            results[imagePath] = loadAsset(imagePath)
        }

        val successCount = results.count { it.value }
        Log.i(TAG, "Preloaded ${results.size} assets for tour $tourId: $successCount successful, ${results.size - successCount} failed")

        return results
    }
}