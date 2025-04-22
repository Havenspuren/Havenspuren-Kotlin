package com.example.havenspure_kotlin_prototype.Utils

import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.di.Graph

/**
 * Extension functions for Location class to work with assets
 */

/**
 * Get the full audio path for this location
 * @param tourId The tour identifier
 * @return The full asset path for this location's audio file
 */
fun Location.getAudioPath(tourId: String = this.tourId): String {
    val assetManager = Graph.getInstance().tourAssetManager
    return assetManager.getAudioPath(tourId, this.audioFileName)
}

/**
 * Get the full image path for this location
 * @param tourId The tour identifier
 * @return The full asset path for this location's image file
 */
fun Location.getImagePath(tourId: String = this.tourId): String {
    val assetManager = Graph.getInstance().tourAssetManager
    return assetManager.getImagePath(tourId, this.id)
}

/**
 * Check if this location's audio file exists
 * @param tourId The tour identifier
 * @return True if the audio file exists
 */
fun Location.audioExists(tourId: String = this.tourId): Boolean {
    val assetManager = Graph.getInstance().tourAssetManager
    return assetManager.audioFileExists(tourId, this.audioFileName)
}

/**
 * Check if this location's image file exists
 * @param tourId The tour identifier
 * @return True if the image file exists
 */
fun Location.imageExists(tourId: String = this.tourId): Boolean {
    val assetManager = Graph.getInstance().tourAssetManager
    return assetManager.imageFileExists(tourId, this.id)
}