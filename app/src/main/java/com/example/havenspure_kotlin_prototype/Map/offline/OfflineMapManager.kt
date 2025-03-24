package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.util.Log
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * SimplifiedOfflineMapManager handles the offline tile management for the map
 */
class SimplifiedOfflineMapManager(private val context: Context) {

    private val TAG = "OfflineMapManager"

    // Base directory for tiles in internal storage
    private val tilesDir: File by lazy {
        File(context.filesDir, "osmdroid/tiles")
    }

    fun logTileDirectoryStructure() {
        val rootDir = File(context.filesDir.absolutePath)
        Log.d(TAG, "Checking files directory structure at: ${rootDir.absolutePath}")
        logDirectoryContents(rootDir, 0)
    }

    private fun logDirectoryContents(dir: File, depth: Int) {
        val indent = "  ".repeat(depth)
        val files = dir.listFiles() ?: return

        for (file in files) {
            Log.d(TAG, "$indent- ${file.name} ${if (file.isDirectory) "/" else ""}")
            if (file.isDirectory && depth < 3) {  // Limit depth to avoid huge logs
                logDirectoryContents(file, depth + 1)
            }
        }
    }

    fun checkTilesExistence() {
        // Check various zoom levels in both source and destination
        Log.d(TAG, "===== CHECKING TILE EXISTENCE =====")

        // Check in assets
        for (zoom in 16..19) {
            try {
                val zoomFiles = context.assets.list("maps/tiles/$zoom")
                Log.d(TAG, "Assets - Zoom level $zoom: Has ${zoomFiles?.size ?: 0} X directories")
                if (zoomFiles?.isNotEmpty() == true) {
                    // Check a few X directories
                    val sampleX = zoomFiles[0]
                    val yFiles = context.assets.list("maps/tiles/$zoom/$sampleX")
                    Log.d(TAG, "  Sample X dir ($sampleX) has ${yFiles?.size ?: 0} tiles")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking zoom $zoom in assets: ${e.message}")
            }
        }

        // Check in file system
        val baseTilesDir = File(context.filesDir, "osmdroid/tiles")
        for (zoom in 16..19) {
            val zoomDir = File(baseTilesDir, zoom.toString())
            if (zoomDir.exists()) {
                val xDirs = zoomDir.listFiles()
                Log.d(TAG, "File system - Zoom level $zoom: Has ${xDirs?.size ?: 0} X directories")
                if (xDirs?.isNotEmpty() == true) {
                    // Check a few X directories
                    val sampleXDir = xDirs[0]
                    val yFiles = sampleXDir.listFiles()
                    Log.d(TAG, "  Sample X dir (${sampleXDir.name}) has ${yFiles?.size ?: 0} tiles")
                }
            } else {
                Log.d(TAG, "File system - Zoom level $zoom: Directory does not exist")
            }
        }
    }

    /**
     * Log details about available tiles for debugging
     */
    fun logTileDetails() {
        try {
            Log.d(TAG, "Checking asset files in maps/tiles:")

            // Check assets
            val assetZoomLevels = context.assets.list("maps/tiles") ?: emptyArray()
            Log.d(TAG, "Found ${assetZoomLevels.size} zoom levels in assets: ${assetZoomLevels.joinToString()}")

            // Sample some zoom levels
            assetZoomLevels.take(5).forEach { zoom ->
                val xDirs = context.assets.list("maps/tiles/$zoom") ?: emptyArray()
                Log.d(TAG, "Zoom level $zoom has ${xDirs.size} X directories")

                // Sample some X directories
                xDirs.take(2).forEach { x ->
                    val yFiles = context.assets.list("maps/tiles/$zoom/$x") ?: emptyArray()
                    Log.d(TAG, "  Zoom $zoom, X dir $x has ${yFiles.size} tiles")
                }
            }

            // Check internal storage if already reorganized
            if (tilesDir.exists()) {
                Log.d(TAG, "Internal tiles directory exists at: ${tilesDir.absolutePath}")
                val zoomDirs = tilesDir.listFiles()
                Log.d(TAG, "Found ${zoomDirs?.size ?: 0} zoom level directories in internal storage")

                zoomDirs?.take(5)?.forEach { zoomDir ->
                    val xDirs = zoomDir.listFiles()
                    Log.d(TAG, "Zoom level ${zoomDir.name} has ${xDirs?.size ?: 0} X directories")

                    xDirs?.take(2)?.forEach { xDir ->
                        val yFiles = xDir.listFiles()
                        Log.d(TAG, "  Zoom ${zoomDir.name}, X dir ${xDir.name} has ${yFiles?.size ?: 0} tiles")
                    }
                }
            } else {
                Log.d(TAG, "Internal tiles directory doesn't exist yet. Will create during reorganization.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging tile details: ${e.message}")
        }
    }

    /**
     * Reorganize tiles from assets to the expected OSMDroid structure
     */
    fun reorganizeTiles() {
        try {
            Log.d(TAG, "Starting tile reorganization...")

            // Ensure the base directory exists
            if (!tilesDir.exists()) {
                tilesDir.mkdirs()
                Log.d(TAG, "Created tiles directory: ${tilesDir.absolutePath}")
            }

            // List zoom levels in assets
            val zoomLevels = context.assets.list("maps/tiles") ?: emptyArray()
            if (zoomLevels.isEmpty()) {
                Log.e(TAG, "No zoom levels found in assets!")
                return
            }

            Log.d(TAG, "Found zoom levels: ${zoomLevels.joinToString()}")

            // Process each zoom level
            zoomLevels.forEach { zoomLevel ->
                val zoomDir = File(tilesDir, zoomLevel)
                if (!zoomDir.exists()) {
                    zoomDir.mkdir()
                    Log.d(TAG, "Created zoom directory: ${zoomDir.absolutePath}")
                }

                // Get X directories for this zoom level
                val xDirs = context.assets.list("maps/tiles/$zoomLevel") ?: emptyArray()
                Log.d(TAG, "Zoom level $zoomLevel has ${xDirs.size} X directories")

                // Process each X directory
                xDirs.forEach { xDir ->
                    val xDirFile = File(zoomDir, xDir)
                    if (!xDirFile.exists()) {
                        xDirFile.mkdir()
                    }

                    // Get Y files for this X directory
                    val yFiles = context.assets.list("maps/tiles/$zoomLevel/$xDir") ?: emptyArray()

                    // Copy each tile file
                    yFiles.forEach { yFile ->
                        val assetPath = "maps/tiles/$zoomLevel/$xDir/$yFile"
                        val destFile = File(xDirFile, yFile)

                        if (!destFile.exists()) {
                            try {
                                context.assets.open(assetPath).use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Failed to copy tile: $assetPath - ${e.message}")
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Tile reorganization completed")

            // Run additional verification after reorganization
            logTileDirectoryStructure()
            checkTilesExistence()

        } catch (e: Exception) {
            Log.e(TAG, "Error reorganizing tiles: ${e.message}")
        }
    }

    /**
     * Set up the map with offline tiles
     */
    fun setupMapWithOfflineTiles(mapView: MapView) {
        try {
            Log.d(TAG, "Setting up map with offline tiles...")

            // Create the custom tile provider
            val tileProvider = CustomOfflineTileProvider(context, tilesDir.absolutePath)

            // Configure the map
            mapView.setUseDataConnection(false)  // Important: disable network
            mapView.tileProvider = tileProvider

            // Configure map view properties
            mapView.setMultiTouchControls(true)
            mapView.isTilesScaledToDpi = true

            // Set zoom levels
            mapView.minZoomLevel = 13.0  // Match with available tiles
            mapView.maxZoomLevel = 17.0  // Match with available tiles

            // Force redraw
            mapView.invalidate()

            Log.d(TAG, "Map setup complete with min zoom: ${mapView.minZoomLevel}, max zoom: ${mapView.maxZoomLevel}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map: ${e.message}")
        }
    }

    /**
     * Get the path to the tiles directory
     */
    fun getTilesPath(): String {
        return tilesDir.absolutePath
    }
}