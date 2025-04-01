package com.example.havenspure_kotlin_prototype.Map.offline.OffRouting

import android.content.Context
import android.util.Log
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.Parameters
import com.graphhopper.util.PointList
import org.osmdroid.util.GeoPoint
import java.io.File

class OfflineRoutingService(private val context: Context) {
    private val TAG = "OfflineRoutingService"
    private var hopper: GraphHopper? = null
    var isInitialized = false

    companion object {
        @Volatile
        private var instance: OfflineRoutingService? = null

        fun getInstance(context: Context): OfflineRoutingService {
            return instance ?: synchronized(this) {
                instance ?: OfflineRoutingService(context.applicationContext).also {
                    instance = it
                    it.initialize()
                }
            }
        }
    }

    // Update the initialize() method in OfflineRoutingService.kt

    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "GraphHopper already initialized, skipping")
            return
        }

        try {
            Log.d(TAG, "Initializing GraphHopper...")

            // Create a destination directory in internal storage
            val graphFolder = File(context.filesDir, "routing_data")
            if (graphFolder.exists()) {
                Log.d(TAG, "Clearing existing routing data directory")
                graphFolder.deleteRecursively()
            }
            graphFolder.mkdirs()

            // Copy all GraphHopper files from assets to internal storage
            copyAssetsToStorage("routing/whv-gh", graphFolder.absolutePath)

            // Initialize GraphHopper
            hopper = GraphHopper().apply {
                // Set location for graph storage
                graphHopperLocation = graphFolder.absolutePath

                // IMPORTANT: Configure BOTH bike and foot profiles to match your graph data
                val bikeProfile = Profile("bike").setVehicle("bike").setWeighting("fastest")
                val footProfile = Profile("foot").setVehicle("foot").setWeighting("shortest")

                // Set BOTH profiles to match your graph data
                setProfiles(bikeProfile, footProfile)

                // Load the graph
                importOrLoad()

                if (graphHopperStorage != null) {
                    isInitialized = true
                    Log.d(TAG, "GraphHopper initialized successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GraphHopper: ${e.message}", e)
        }
    }
    // Helper method to copy assets to internal storage
    private fun copyAssetsToStorage(assetPath: String, destPath: String) {
        try {
            val assetManager = context.assets
            val files = assetManager.list(assetPath) ?: return

            Log.d(TAG, "Found ${files.size} files/directories in assets path: $assetPath")

            val destDir = File(destPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
                Log.d(TAG, "Created destination directory: $destPath")
            }

            for (fileName in files) {
                val fullAssetPath = "$assetPath/$fileName"
                val subDirs = assetManager.list(fullAssetPath)

                if (subDirs != null && subDirs.isNotEmpty()) {
                    // This is a directory, recursively copy it
                    Log.d(TAG, "Found subdirectory: $fileName with ${subDirs.size} files")
                    copyAssetsToStorage(fullAssetPath, "$destPath/$fileName")
                } else {
                    // This is a file, copy it
                    val destFile = File(destDir, fileName)
                    if (!destFile.exists()) {
                        try {
                            assetManager.open(fullAssetPath).use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Copied file $fileName to $destPath")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error copying file $fileName: ${e.message}")
                        }
                    } else {
                        Log.d(TAG, "File already exists: ${destFile.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying assets: ${e.message}", e)
        }
    }

    // Calculate a route between two points
    fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: String = "foot"  // Changed default to foot
    ): List<GeoPoint> {
        if (!isInitialized || hopper == null) {
            Log.e(TAG, "GraphHopper not initialized")
            return emptyList()
        }

        try {
            Log.d(TAG, "Starting route calculation from $startLat,$startLon to $endLat,$endLon")
            Log.d(TAG, "Using profile: $mode")

            // Create a GHRequest directly
            val req = com.graphhopper.GHRequest(
                startLat, startLon,
                endLat, endLon
            )
            // Set parameters - forcing "foot" regardless of input
            req.setProfile("foot")
            req.setAlgorithm(Parameters.Algorithms.ASTAR_BI)
            req.setLocale(java.util.Locale.GERMAN)

            Log.d(TAG, "Request created with profile: ${req.getProfile()} and algorithm: ${req.getAlgorithm()}")

            // Calculate the route
            Log.d(TAG, "Executing route calculation...")
            val rsp = hopper!!.route(req)

            if (rsp.hasErrors()) {
                Log.e(TAG, "Routing error: ${rsp.errors}")
                return emptyList()
            }

            // Get the path
            val path = rsp.best.points

            // Log details about the calculated route
            Log.d(TAG, "Route calculated successfully with ${path.size()} points")
            if (path.size() > 2) {
                Log.d(TAG, "First 3 points: ${path.getLat(0)},${path.getLon(0)} to ${path.getLat(1)},${path.getLon(1)} to ${path.getLat(2)},${path.getLon(2)}")
            }
            Log.d(TAG, "Distance: ${rsp.best.distance}m, Time: ${rsp.best.time/1000}s")
            Log.d(TAG, "Turns: ${rsp.best.instructions.size}")

            // Convert to OSMDroid GeoPoints
            return convertToGeoPoints(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating route: ${e.message}", e)
            return emptyList()
        }
    }

    // Get turn-by-turn instructions
    fun getInstructions(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        mode: String = "foot"  // Changed default to foot
    ): List<String> {
        if (!isInitialized || hopper == null) {
            Log.e(TAG, "GraphHopper not initialized for instructions")
            return emptyList()
        }

        try {
            Log.d(TAG, "Getting turn-by-turn instructions from $startLat,$startLon to $endLat,$endLon using foot")

            // Create a GHRequest directly
            val req = com.graphhopper.GHRequest(
                startLat, startLon,
                endLat, endLon
            )
            // Set parameters - forcing "foot" regardless of input
            req.setProfile("foot")
            req.setAlgorithm(Parameters.Algorithms.ASTAR_BI)
            req.setLocale(java.util.Locale.GERMAN)

            val rsp = hopper!!.route(req)

            if (rsp.hasErrors()) {
                Log.e(TAG, "Instruction error: ${rsp.errors}")
                return emptyList()
            }

            // Log instruction details
            val instructions = rsp.best.instructions
            Log.d(TAG, "Got ${instructions.size} instructions for route")

            // Extract instructions with safer approach
            return instructions.mapIndexed { index, instruction ->
                try {
                    // Try to get instruction text in the safest way possible
                    val text = try {
                        // First try getName() which doesn't need a Translation
                        val name = instruction.getName()
                        Log.d(TAG, "Instruction $index name: $name")
                        name
                    } catch (e: Exception) {
                        try {
                            // If that fails, try to get the translation object
                            val tr = hopper!!.getTranslationMap().getWithFallBack(req.getLocale())
                            val desc = instruction.getTurnDescription(tr)
                            Log.d(TAG, "Instruction $index turn description: $desc")
                            desc
                        } catch (e2: Exception) {
                            Log.e(TAG, "Could not get instruction text: ${e2.message}")
                            "Continue"
                        }
                    }

                    val formattedInstruction = "$text (${String.format("%.1f", instruction.distance / 1000)} km)"
                    Log.d(TAG, "Formatted instruction $index: $formattedInstruction")
                    formattedInstruction
                } catch (e: Exception) {
                    // Ultimate fallback
                    Log.e(TAG, "Error formatting instruction $index: ${e.message}")
                    "Continue for ${String.format("%.1f", instruction.distance / 1000)} km"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting instructions: ${e.message}", e)
            return emptyList()
        }
    }

    // Helper to convert GH points to OSMDroid GeoPoints
    private fun convertToGeoPoints(points: PointList): List<GeoPoint> {
        val geoPoints = mutableListOf<GeoPoint>()
        for (i in 0 until points.size()) {
            geoPoints.add(GeoPoint(points.getLat(i), points.getLon(i)))
        }
        Log.d(TAG, "Converted ${points.size()} GraphHopper points to ${geoPoints.size} GeoPoints")
        return geoPoints
    }

    // Diagnostic method to test all available profiles
    fun testAllProfiles(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        if (!isInitialized || hopper == null) {
            Log.e(TAG, "Cannot test profiles, GraphHopper not initialized")
            return results
        }

        // Only test foot profile
        val profiles = listOf("foot")

        for (profile in profiles) {
            try {
                Log.d(TAG, "Testing profile: $profile")
                val req = com.graphhopper.GHRequest(startLat, startLon, endLat, endLon)
                req.setProfile(profile)
                req.setAlgorithm(Parameters.Algorithms.ASTAR_BI)

                val rsp = hopper!!.route(req)

                if (rsp.hasErrors()) {
                    Log.e(TAG, "Profile $profile failed: ${rsp.errors}")
                    results[profile] = false
                } else {
                    val points = rsp.best.points.size()
                    Log.d(TAG, "Profile $profile succeeded with $points points and ${rsp.best.instructions.size} instructions")
                    results[profile] = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception testing profile $profile: ${e.message}")
                results[profile] = false
            }
        }

        return results
    }

    // Check if the GraphHopper database is properly loaded and has coverage for a point
    fun checkCoverageForPoint(lat: Double, lon: Double): Boolean {
        if (!isInitialized || hopper == null) {
            Log.e(TAG, "Cannot check coverage, GraphHopper not initialized")
            return false
        }

        try {
            val storage = hopper!!.graphHopperStorage
            if (storage == null) {
                Log.e(TAG, "GraphHopper storage is null")
                return false
            }

            val bounds = storage.bounds
            Log.d(TAG, "Storage bounds: $bounds")

            if (bounds.contains(lat, lon)) {
                Log.d(TAG, "Point ($lat, $lon) is within graph bounds")
                return true
            } else {
                Log.e(TAG, "Point ($lat, $lon) is outside graph bounds!")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking coverage: ${e.message}")
            return false
        }
    }
}