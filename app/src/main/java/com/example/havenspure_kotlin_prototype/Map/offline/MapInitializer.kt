package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import com.example.havenspure_kotlin_prototype.Map.offline.OffRouting.OfflineRoutingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MapInitializer(private val context: Context) {

    private val TAG = "MapInitializer"

    // Directory for storing map tiles
    private val mapTilesDir by lazy {
        File(context.filesDir, "map_tiles")
    }

    // Rename this to avoid the JVM signature clash
    private val routingServiceInstance by lazy { OfflineRoutingService.getInstance(context) }

    // Initialize routing
    fun initializeRouting() {
        routingServiceInstance.initialize()
    }

    // Renamed method to avoid conflict with property accessor
    fun getRoutingServiceProvider(): OfflineRoutingService {
        return routingServiceInstance
    }

    /**
     * Initialize OSMDroid configuration
     */
    fun initialize() {
        try {
            // Set OSMDroid user agent
            val config = Configuration.getInstance()
            config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
            config.userAgentValue = context.packageName

            // Set up cache directories
            val cacheDir = File(context.cacheDir, "osmdroid")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            config.osmdroidTileCache = cacheDir

            Log.d(TAG, "OSMDroid initialized with cache: ${cacheDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OSMDroid: ${e.message}")
        }
    }

    /**
     * Import tiles from assets to internal storage
     * This should be called before setting up the map
     */
    suspend fun importTilesFromAssets(): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!mapTilesDir.exists()) {
                    mapTilesDir.mkdirs()
                    Log.d(TAG, "Created map tiles directory: ${mapTilesDir.absolutePath}")
                }

                // List all zoom levels
                val zoomLevels = context.assets.list("maps/tiles") ?: emptyArray()
                Log.d(TAG, "Found ${zoomLevels.size} zoom levels in assets: ${zoomLevels.joinToString()}")

                // Process each zoom level
                zoomLevels.forEach { zoomLevel ->
                    val zoomDir = File(mapTilesDir, zoomLevel)
                    if (!zoomDir.exists()) {
                        zoomDir.mkdir()
                    }

                    // List X directories in this zoom level
                    val xDirs = context.assets.list("maps/tiles/$zoomLevel") ?: emptyArray()
                    Log.d(TAG, "Zoom level $zoomLevel has ${xDirs.size} X directories")

                    // Process each X directory
                    xDirs.forEach { xDir ->
                        val xDirFile = File(zoomDir, xDir)
                        if (!xDirFile.exists()) {
                            xDirFile.mkdir()
                        }

                        // List Y files in this X directory
                        val yFiles = context.assets.list("maps/tiles/$zoomLevel/$xDir") ?: emptyArray()

                        // Copy each Y file
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

                // Verify import
                verifyTiles()

                return@withContext mapTilesDir.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error importing tiles: ${e.message}")
                return@withContext ""
            }
        }
    }

    private fun verifyTiles() {
        // Count directories and files
        var zoomLevels = 0
        var xDirs = 0
        var tiles = 0

        mapTilesDir.listFiles()?.forEach { zoomDir ->
            zoomLevels++
            zoomDir.listFiles()?.forEach { xDir ->
                xDirs++
                tiles += xDir.listFiles()?.size ?: 0
            }
        }

        Log.d(TAG, "Verified tiles: $zoomLevels zoom levels, $xDirs X directories, $tiles total tiles")

        // Check zoom levels present
        val zoomDirs = mapTilesDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        Log.d(TAG, "Available zoom levels: ${zoomDirs.joinToString()}")
    }

    /**
     * Set up the map view with the offline tile provider
     */
    fun setupMap(mapView: MapView, offlineTilesPath: String = mapTilesDir.absolutePath) {
        try {
            // Create custom tile provider
            val tileProvider = CustomOfflineTileProvider(context, offlineTilesPath)

            // Set the tile provider and disable network connection
            mapView.setUseDataConnection(false)
            mapView.tileProvider = tileProvider

            // Configure the map
            mapView.setMultiTouchControls(true)
            mapView.isTilesScaledToDpi = true

            // Set zoom levels
            mapView.minZoomLevel = 13.0
            mapView.maxZoomLevel = 19.0

            // Force a redraw
            mapView.invalidate()

            Log.d(TAG, "Map setup complete with provider using path: $offlineTilesPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map: ${e.message}")
        }
    }
}