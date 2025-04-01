package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.roundToInt

/**
 * Manages offline map functionality including:
 * - Map tile caching
 * - Offline configuration
 * - Cache management
 */
class OfflineMapManager(private val context: Context) {
    private val TAG = "OfflineMapManager"

    // Default cache directory
    private val cacheDir by lazy {
        File(context.cacheDir, "osmdroid").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Initialize the OSMDroid configuration
     * Call this before creating any MapView
     */
    fun initialize() {
        try {
            val configuration = Configuration.getInstance()
            configuration.load(context, PreferenceManager.getDefaultSharedPreferences(context))

            // Set cache location
            configuration.osmdroidTileCache = cacheDir
            configuration.osmdroidBasePath = context.filesDir

            // Set user agent
            configuration.userAgentValue = context.packageName

            // Optimize for offline use
            configuration.isMapViewRecyclerFriendly = true
            configuration.tileDownloadThreads = 2
            configuration.tileFileSystemThreads = 2
            configuration.tileDownloadMaxQueueSize = 8
            configuration.tileFileSystemMaxQueueSize = 8

            // Extend tile expiration for offline usage
            configuration.expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30 // 30 days

            Log.d(TAG, "OSMDroid configuration initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OSMDroid configuration: ${e.message}")
        }
    }

    /**
     * Configure a MapView for optimal performance
     *
     * @param mapView MapView to configure
     */
    fun configureMapView(mapView: MapView) {
        try {
            // Set tile source
            mapView.setTileSource(TileSourceFactory.MAPNIK)

            // Performance settings
            mapView.isTilesScaledToDpi = true
            mapView.setMultiTouchControls(true)
            mapView.isHorizontalMapRepetitionEnabled = false
            mapView.isVerticalMapRepetitionEnabled = false
            mapView.setUseDataConnection(true) // Allow network tiles but with caching

            // Set hardware acceleration
            mapView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // Disable extras for performance
            mapView.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

            // Set zoom levels - more constrained for better performance
            mapView.minZoomLevel = 12.0
            mapView.maxZoomLevel = 19.0

            // Default zoom
            mapView.controller.setZoom(16.0)

            Log.d(TAG, "MapView configured for optimal performance")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring MapView: ${e.message}")
        }
    }

    /**
     * Download map tiles for offline use around a specific location
     *
     * @param mapView MapView to use for downloading
     * @param center Center point to download around
     * @param radiusKm Radius in kilometers to download
     * @param minZoom Minimum zoom level to download
     * @param maxZoom Maximum zoom level to download
     * @param callback Callback to track download progress
     */
    fun downloadMapArea(
        mapView: MapView,
        center: GeoPoint,
        radiusKm: Double,
        minZoom: Int = 12,
        maxZoom: Int = 17,
        callback: (progress: Int, total: Int, completedSuccessfully: Boolean) -> Unit
    ) {
        try {
            // Approximate bounding box for the given radius
            // Rough calculation: 1 degree latitude ≈ 111 km
            val degreesLat = radiusKm / 111.0
            val degreesLon = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude)))

            val boundingBox = BoundingBox(
                center.latitude + degreesLat,
                center.longitude + degreesLon,
                center.latitude - degreesLat,
                center.longitude - degreesLon
            )

            // Create cache manager
            val cacheManager = CacheManager(mapView)

            // Start download in a background thread
            Thread {
                try {
                    // Download tiles
                    cacheManager.downloadAreaAsync(
                        context,
                        boundingBox,
                        minZoom,
                        maxZoom,
                        object : CacheManager.CacheManagerCallback {
                            override fun onTaskComplete() {
                                callback(100, 100, true)
                                Log.d(TAG, "Map area download completed successfully")
                            }

                            override fun onTaskFailed(errors: Int) {
                                callback(0, 100, false)
                                Log.e(TAG, "Map area download failed with $errors errors")
                            }

                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                                // Calculate overall progress across all zoom levels
                                val zoomLevelsTotal = maxZoom - minZoom + 1
                                val currentZoomProgress = currentZoomLevel - minZoom
                                val zoomProgress = if (zoomLevelsTotal > 0) {
                                    (currentZoomProgress.toFloat() / zoomLevelsTotal) * 100
                                } else 0f

                                val adjustedProgress = (zoomProgress + (progress.toFloat() / zoomLevelsTotal)).roundToInt()
                                callback(adjustedProgress, 100, false)

                                Log.d(TAG, "Map download progress: $adjustedProgress% (Zoom $currentZoomLevel: $progress%)")
                            }

                            override fun downloadStarted() {
                                Log.d(TAG, "Map download started")
                                callback(0, 100, false)
                            }

                            override fun setPossibleTilesInArea(total: Int) {
                                Log.d(TAG, "Possible tiles in area: $total")
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error during map area download: ${e.message}")
                    callback(0, 100, false)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting map area download: ${e.message}")
            callback(0, 100, false)
        }
    }

    /**
     * Clear the tile cache
     *
     * @return True if the cache was cleared successfully
     */
    fun clearCache(): Boolean {
        return try {
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            file.delete()
                        }
                    }
                }
            }
            Log.d(TAG, "Cache cleared successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}")
            false
        }
    }

    /**
     * Get the current cache size in MB
     *
     * @return Cache size in MB
     */
    fun getCacheSizeMB(): Float {
        return try {
            if (cacheDir.exists()) {
                var size = 0L
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            size += file.length()
                        }
                    }
                }
                // Convert to MB
                size.toFloat() / (1024 * 1024)
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache size: ${e.message}")
            0f
        }
    }

    /**
     * Check if a specific area is available offline
     *
     * @param center Center point of the area
     * @param radiusKm Radius in kilometers
     * @param zoom Zoom level to check
     * @return True if the area is available offline
     */
    fun isAreaAvailableOffline(center: GeoPoint, radiusKm: Double, zoom: Int): Boolean {
        try {
            // Approximate bounding box for the given radius
            val degreesLat = radiusKm / 111.0
            val degreesLon = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude)))

            val boundingBox = BoundingBox(
                center.latitude + degreesLat,
                center.longitude + degreesLon,
                center.latitude - degreesLat,
                center.longitude - degreesLon
            )

            // Create a temporary map view to check the cache
            val tempMapView = MapView(context)
            tempMapView.setTileSource(TileSourceFactory.MAPNIK)

            val cacheManager = CacheManager(tempMapView)

            // Check individual tiles in the area
            val tileSource = tempMapView.tileProvider.tileSource
            val projection = tempMapView.projection

            // Calculate tile boundaries for the area
            val north = boundingBox.latNorth
            val south = boundingBox.latSouth
            val east = boundingBox.lonEast
            val west = boundingBox.lonWest

            // Manually check a sample of points in the area
            val latStep = (north - south) / 5
            val lonStep = (east - west) / 5

            for (lat in 0..5) {
                for (lon in 0..5) {
                    val testLat = south + lat * latStep
                    val testLon = west + lon * lonStep

                    // Get tile coordinates for this point
                    val point = GeoPoint(testLat, testLon)
                    val mapTilePoint = projection.toPixels(point, null)

                    // Convert to tile coordinates
                    val x = mapTilePoint.x / 256
                    val y = mapTilePoint.y / 256

                    // Check if this tile exists in cache
                    val tileExists = File(cacheDir, "$zoom/$x/$y.tile").exists()

                    if (!tileExists) {
                        return false
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking offline availability: ${e.message}")
            return false
        }
    }
}