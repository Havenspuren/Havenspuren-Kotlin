package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import org.osmdroid.tileprovider.MapTileCache
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.InputStream

class CustomOfflineTileProvider(
    private val context: Context,
    private val basePath: String,
    private val useAssets: Boolean = false
) : MapTileProviderBase(null) {

    private val TAG = "CustomTileProvider"

    // Increase cache size to improve performance
    private val TILE_CACHE_SIZE = 200

    // Create the tile source
    private val customTileSource: ITileSource = object : XYTileSource(
        "CustomTiles",
        13, 19, 256, ".png",  // Set your actual min/max zoom levels here
        arrayOf("")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$basePath/$zoom/$x/$y.png"
        }
    }

    // Create our own cache with larger size
    private val tileCache = MapTileCache(TILE_CACHE_SIZE)

    // Required implementation for tile writer
    private var tileWriter: IFilesystemCache? = null

    init {
        // Set the tile source properly
        super.setTileSource(customTileSource)

        // Log the base path to verify it
        Log.d(TAG, "Initialized with base path: $basePath")
        Log.d(TAG, "Using assets: $useAssets")

        // Verify directory structure exists
        if (!useAssets) {
            val baseDir = File(basePath)
            if (baseDir.exists()) {
                Log.d(TAG, "Base directory exists with ${baseDir.listFiles()?.size ?: 0} subdirectories")
                baseDir.listFiles()?.forEach {
                    Log.d(TAG, "Found directory: ${it.name}")
                }
            } else {
                Log.e(TAG, "Base directory does not exist: $basePath")
            }
        }
    }

    // Required methods from MapTileProviderBase
    override fun getQueueSize(): Long = 0L

    override fun getMinimumZoomLevel(): Int = customTileSource.minimumZoomLevel

    override fun getMaximumZoomLevel(): Int = customTileSource.maximumZoomLevel

    override fun getTileWriter(): IFilesystemCache? = tileWriter

    // Override to use our local cache instead of parent's
    override fun getMapTile(pMapTileIndex: Long): Drawable? {
        try {
            // Try to get from our cache first
            val cachedTile = tileCache.getMapTile(pMapTileIndex)
            if (cachedTile != null) {
                return cachedTile
            }

            // Get coordinates
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            Log.d(TAG, "Requesting tile: zoom=$zoom, x=$x, y=$y")

            // Handle loading differently depending on source (assets or file system)
            val bitmap = if (useAssets) {
                loadTileFromAssets(zoom, x, y)
            } else {
                loadTileFromFileSystem(zoom, x, y)
            }

            if (bitmap != null) {
                val drawable = BitmapDrawable(context.resources, bitmap)
                tileCache.putTile(pMapTileIndex, drawable)
                return drawable
            } else {
                Log.d(TAG, "Tile not found for zoom=$zoom, x=$x, y=$y")
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tile: ${e.message}", e)
            return null
        }
    }

    private fun loadTileFromFileSystem(zoom: Int, x: Int, y: Int): android.graphics.Bitmap? {
        // Construct all possible file paths to try
        val possiblePaths = listOf(
            File("$basePath/$zoom/$x/$y.png"),
            File("$basePath/tiles/$zoom/$x/$y.png"),
            File("$basePath/${zoom.toString().padStart(2, '0')}/$x/$y.png")
        )

        // Try each path
        for (tileFile in possiblePaths) {
            Log.d(TAG, "Trying path: ${tileFile.absolutePath}")
            if (tileFile.exists() && tileFile.canRead()) {
                Log.d(TAG, "Found tile at: ${tileFile.absolutePath}")
                return BitmapFactory.decodeFile(tileFile.absolutePath)
            }
        }

        return null
    }

    private fun loadTileFromAssets(zoom: Int, x: Int, y: Int): android.graphics.Bitmap? {
        // Try multiple possible path formats for the assets
        val possiblePaths = listOf(
            "maps/tiles/$zoom/$x/$y.png",
            "maps/$zoom/$x/$y.png",
            "$zoom/$x/$y.png"
        )

        for (path in possiblePaths) {
            try {
                val assetManager = context.assets
                Log.d(TAG, "Trying asset path: $path")

                val inputStream: InputStream = assetManager.open(path)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    Log.d(TAG, "Found tile in assets: $path")
                    return bitmap
                }
            } catch (e: Exception) {
                // Just try the next path
            }
        }

        return null
    }
}