/*
package com.example.havenspure_kotlin_prototype.Map.Routing


import android.content.Context
import android.graphics.Paint
import android.util.Log
import com.example.havenspure_kotlin_prototype.Data.LocationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Helper methods for the DirectionMapComponent with optimized performance
 */
object DirectionMapHelpers {

    // Improved thread-safe cache for routes with expiration
    private val routeCache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    // Reduced timeouts for faster fallback
    private const val OSRM_TIMEOUT_MS = 5000L // 5 seconds

    // Constants for user marker identification
    private const val USER_MARKER_ID = "USER_MARKER"

    // File cache
    private var cacheDir: File? = null

    // Class to hold cached route data with expiration
    private data class CacheEntry(
        val routePoints: List<GeoPoint>,
        val distance: Double,
        val instruction: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS
    }

    /**
     * Initialize cache directory
     */
    fun initializeCacheDir(context: Context) {
        cacheDir = File(context.cacheDir, "route_cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Update only the user location marker on the map
     */
    fun updateUserMarkerPosition(
        mapView: MapView,
        userPoint: GeoPoint,
        userLocationColor: Int,
        context: Context
    ) {
        // Use method from MarkerCreationHelpers
        MarkerCreationHelpers.updateUserMarkerPosition(
            mapView,
            userPoint,
            userLocationColor,
            context
        )
    }

    /**
     * Optimized main route fetching method with quick fallback
     */
    suspend fun getRoute(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        context: Context? = null
    ): Triple<List<GeoPoint>, Double, String> = withContext(Dispatchers.IO) {
        // Initialize cache if needed
        if (context != null && cacheDir == null) {
            initializeCacheDir(context)
        }

        // Create cache key
        val cacheKey =
            "${startLat.roundTo(5)},${startLon.roundTo(5)}-${endLat.roundTo(5)},${endLon.roundTo(5)}"

        // Check memory cache first
        routeCache[cacheKey]?.let { cacheEntry ->
            if (!cacheEntry.isExpired()) {
                Log.d("DirectionMapHelpers", "Using memory-cached route")
                return@withContext Triple(
                    cacheEntry.routePoints,
                    cacheEntry.distance,
                    cacheEntry.instruction
                )
            } else {
                // Remove expired entry
                routeCache.remove(cacheKey)
            }
        }

        // Check file cache
        val cachedRoute = loadRouteFromFileCache(cacheKey)
        if (cachedRoute != null) {
            Log.d("DirectionMapHelpers", "Using file-cached route")
            // Also store in memory cache
            routeCache[cacheKey] = CacheEntry(
                cachedRoute.first,
                cachedRoute.second,
                cachedRoute.third
            )
            return@withContext cachedRoute
        }

        try {
            // Try OSRM with shorter timeout
            val osrmRoute = withTimeoutOrNull(OSRM_TIMEOUT_MS) {
                fetchOsrmRoute(startLon, startLat, endLon, endLat)
            }

            if (osrmRoute != null) {
                // Cache successful OSRM response
                routeCache[cacheKey] = CacheEntry(
                    osrmRoute.first,
                    osrmRoute.second,
                    osrmRoute.third
                )
                saveRouteToFileCache(cacheKey, osrmRoute)
                return@withContext osrmRoute
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("DirectionMapHelpers", "OSRM request timed out")
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error fetching OSRM route: ${e.message}")
        }

        // Fast fallback to simplified local routing
        Log.d("DirectionMapHelpers", "Using local route generation")
        val startPoint = GeoPoint(startLat, startLon)
        val endPoint = GeoPoint(endLat, endLon)

        // Use optimized street-based route from RoutingHelpers
        val streetRoute = RoutingHelpers.createSimplifiedStreetRoute(startPoint, endPoint)
        val distance = RoutingHelpers.calculateRouteDistance(streetRoute)
        val direction = RoutingHelpers.getDirectionText(
            LocationData(startLat, startLon),
            LocationData(endLat, endLon)
        )

        val fallbackResult = Triple(streetRoute, distance, direction)

        // Cache the fallback result too
        routeCache[cacheKey] = CacheEntry(
            streetRoute,
            distance,
            direction
        )

        return@withContext fallbackResult
    }

    /**
     * Round double to specified decimal places for cache key consistency
     */
    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToLong() / factor
    }

    /**
     * Save route to file cache
     */
    private fun saveRouteToFileCache(key: String, route: Triple<List<GeoPoint>, Double, String>) {
        cacheDir?.let { dir ->
            try {
                val file = File(dir, key.hashCode().toString())
                file.writeText(serializeRoute(route))
            } catch (e: Exception) {
                Log.e("DirectionMapHelpers", "Error saving route to cache: ${e.message}")
            }
        }
    }

    /**
     * Load route from file cache
     */
    private fun loadRouteFromFileCache(key: String): Triple<List<GeoPoint>, Double, String>? {
        if (cacheDir == null) {
            return null
        }

        val dir = cacheDir!!
        try {
            val file = File(dir, key.hashCode().toString())
            if (file.exists() && file.isFile && System.currentTimeMillis() - file.lastModified() < CACHE_EXPIRATION_MS) {
                return deserializeRoute(file.readText())
            }
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error loading route from cache: ${e.message}")
        }
        return null
    }

    /**
     * Serialize route to string for storage
     */
    private fun serializeRoute(route: Triple<List<GeoPoint>, Double, String>): String {
        val points = route.first.joinToString(";") { "${it.latitude},${it.longitude}" }
        return "$points|${route.second}|${route.third}"
    }

    /**
     * Deserialize route from string
     */
    private fun deserializeRoute(data: String): Triple<List<GeoPoint>, Double, String>? {
        try {
            val parts = data.split("|")
            if (parts.size != 3) return null

            val pointsStr = parts[0]
            val distance = parts[1].toDoubleOrNull() ?: return null
            val instruction = parts[2]

            val points = pointsStr.split(";").map {
                val coords = it.split(",")
                if (coords.size != 2) return null
                val lat = coords[0].toDoubleOrNull() ?: return null
                val lon = coords[1].toDoubleOrNull() ?: return null
                GeoPoint(lat, lon)
            }

            return Triple(points, distance, instruction)
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error deserializing route: ${e.message}")
            return null
        }
    }

    /**
     * Fetch route from OSRM API with improved error handling and efficiency
     */
    private suspend fun fetchOsrmRoute(
        startLon: Double, startLat: Double,
        endLon: Double, endLat: Double
    ): Triple<List<GeoPoint>, Double, String>? = withContext(Dispatchers.IO) {
        try {
            // OSRM API URL for foot navigation
            val requestUrl =
                "https://router.project-osrm.org/route/v1/foot/$startLon,$startLat;$endLon,$endLat?overview=full&steps=true&geometries=geojson&alternatives=false"
            Log.d("DirectionMapHelpers", "Requesting OSRM route")

            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000 // 3 seconds connect timeout
            connection.readTimeout = 5000 // 5 seconds read timeout
            connection.useCaches = true

            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("DirectionMapHelpers", "HTTP error code: $responseCode")
                    return@withContext null
                }

                // Efficient reading with buffer
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                val buffer = CharArray(1024)
                var read: Int

                while (reader.read(buffer).also { read = it } != -1) {
                    response.appendRange(buffer, 0, read)
                }
                reader.close()

                val jsonResponse = JSONObject(response.toString())

                // Check if route was found
                if (jsonResponse.getString("code") != "Ok") {
                    Log.e("DirectionMapHelpers", "OSRM Error: ${jsonResponse.getString("code")}")
                    return@withContext null
                }

                val routes = jsonResponse.getJSONArray("routes")
                if (routes.length() == 0) {
                    Log.e("DirectionMapHelpers", "OSRM returned no routes")
                    return@withContext null
                }

                val route = routes.getJSONObject(0)
                val distance = route.getDouble("distance")

                // Extract the geometry (GeoJSON format)
                val geometry = route.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")

                // Parse into GeoPoints
                val routePoints = mutableListOf<GeoPoint>()

                // For long routes, downsample points
                val stride = if (coordinates.length() > 100) 2 else 1

                for (i in 0 until coordinates.length() step stride) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    routePoints.add(GeoPoint(lat, lon))
                }

                // Ensure we always include the last point
                if (routePoints.isEmpty() ||
                    routePoints.last().latitude != coordinates.getJSONArray(coordinates.length() - 1)
                        .getDouble(1) ||
                    routePoints.last().longitude != coordinates.getJSONArray(coordinates.length() - 1)
                        .getDouble(0)
                ) {
                    val lastCoord = coordinates.getJSONArray(coordinates.length() - 1)
                    routePoints.add(GeoPoint(lastCoord.getDouble(1), lastCoord.getDouble(0)))
                }

                // Get first navigation instruction
                val legs = route.getJSONArray("legs")
                val firstLeg = legs.getJSONObject(0)
                var firstInstruction = "Starten Sie Ihre Route"

                if (firstLeg.has("steps")) {
                    val steps = firstLeg.getJSONArray("steps")
                    if (steps.length() > 0) {
                        val firstStep = steps.getJSONObject(0)
                        if (firstStep.has("maneuver")) {
                            val maneuver = firstStep.getJSONObject("maneuver")
                            val type = maneuver.getString("type")
                            val modifier = maneuver.optString("modifier", "")

                            // Convert instruction to German
                            firstInstruction = when (type) {
                                "depart" -> "Starten Sie Ihre Route"
                                "turn" -> {
                                    when (modifier) {
                                        "right" -> "Biegen Sie rechts ab"
                                        "left" -> "Biegen Sie links ab"
                                        "slight right" -> "Biegen Sie leicht rechts ab"
                                        "slight left" -> "Biegen Sie leicht links ab"
                                        "sharp right" -> "Biegen Sie scharf rechts ab"
                                        "sharp left" -> "Biegen Sie scharf links ab"
                                        "straight" -> "Geradeaus weiter"
                                        else -> "Biegen Sie ab"
                                    }
                                }

                                "continue" -> "Weiter geradeaus"
                                "arrive" -> "Sie haben Ihr Ziel erreicht"
                                else -> "Folgen Sie der Strecke"
                            }
                        }
                    }
                }

                return@withContext Triple(routePoints, distance, firstInstruction)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error fetching OSRM route: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Create an L-shaped route and display it on the map
     */
    fun forceStreetBasedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routeColor: Int,
        userLocationColor: Int,
        destinationColor: Int,
        context: Context
    ): List<GeoPoint> {
        // Create an L-shaped route directly
        val points = mutableListOf<GeoPoint>()
        points.add(startPoint)

        // Calculate if we should move horizontally or vertically first
        val latDifference = Math.abs(endPoint.latitude - startPoint.latitude)
        val lonDifference = Math.abs(endPoint.longitude - startPoint.longitude)

        if (latDifference > lonDifference) {
            // Move vertically first, then horizontally
            points.add(GeoPoint(endPoint.latitude, startPoint.longitude))
        } else {
            // Move horizontally first, then vertically
            points.add(GeoPoint(startPoint.latitude, endPoint.longitude))
        }

        points.add(endPoint)

        // Clear existing overlays but keep rotation
        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
        mapView.overlays.clear()
        mapView.overlays.addAll(existingOverlays)

        // Add the L-shaped route
        addDirectionRoute(
            mapView,
            startPoint,
            endPoint,
            points,
            routeColor,
            userLocationColor,
            destinationColor,
            context
        )

        // Force map refresh
        mapView.invalidate()

        return points
    }

    /**
     * Add route line, direction arrows, and markers for the journey with performance optimizations.
     */
    fun addDirectionRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routePoints: List<GeoPoint>,
        routeColor: Int,
        userLocationColor: Int,
        destinationColor: Int,
        context: Context
    ) {
        // Destination marker
        val destinationMarker = Marker(mapView).apply {
            position = endPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = MarkerCreationHelpers.createCustomPin(context, destinationColor)
            title = "Ziel"
        }
        mapView.overlays.add(destinationMarker)

        // Main route line
        if (routePoints.size >= 2) {
            val routeLine = Polyline(mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = routeColor
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(routeLine)
        } else {
            // Fallback to direct line if not enough points
            val directLine = Polyline(mapView).apply {
                setPoints(listOf(startPoint, endPoint))
                outlinePaint.color = routeColor
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(directLine)
        }

        // Add user location marker using the dedicated method
        updateUserMarkerPosition(mapView, startPoint, userLocationColor, context)

        // Limit number of direction arrows for performance
        if (routePoints.size > 3) {
            MarkerCreationHelpers.addOptimizedDirectionArrows(mapView, routePoints, routeColor, context)
        }
    }

    /**
     * Format distance in a human-readable way.
     */
    fun formatDistance(distanceMeters: Double): String {
        return RoutingHelpers.formatDistance(distanceMeters)
    }

    /**
     * Calculate GraphHopper route
     */
    fun calculateGraphHopperRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        destinationPoint: GeoPoint,
        routeColor: Int,
        markerColor: Int,
        destinationMarkerColor: Int,
        context: Context
    ): List<GeoPoint> {
        return RoutingHelpers.calculateGraphHopperRoute(
            mapView,
            startPoint,
            destinationPoint,
            routeColor,
            markerColor,
            destinationMarkerColor,
            context
        )
    }

    /**
     * Get GraphHopper directions
     */
    fun getGraphHopperDirections(
        userLocation: LocationData,
        routePoints: List<GeoPoint>,
        destinationLocation: LocationData,
        context: Context
    ): String {
        return RoutingHelpers.getGraphHopperDirections(
            userLocation,
            routePoints,
            destinationLocation,
            context
        )
    }
}

 */



/*
package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import com.example.havenspure_kotlin_prototype.Map.Routing.MarkerCreationHelpers
import com.example.havenspure_kotlin_prototype.Map.Routing.RoutingHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper methods for map navigation and routing
 */
object DirectionMapHelpers {
    private const val TAG = "DirectionMapHelpers"

    /**
     * Initialize cache directory for offline tiles
     */
    fun initializeCacheDir(context: Context) {
        val cacheDir = File(context.cacheDir, "osmdroid")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Update user marker position on the map
     */
    fun updateUserMarkerPosition(
        mapView: MapView,
        position: GeoPoint,
        markerColor: Int,
        context: Context
    ) {
        // Use the RoutingGraph to update the user position
        RoutingGraph.updateUserMarker(mapView, position)
    }

    /**
     * Create a route using RoutingGraph (which uses OSRM for actual street routing)
     */
    fun addDirectionRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        destinationPoint: GeoPoint,
        routePoints: List<GeoPoint>,
        routeColor: Int,
        markerColor: Int,
        destinationMarkerColor: Int,
        context: Context
    ) {
        // Add the route polyline
        if (routePoints.isNotEmpty()) {
            val routeLine = Polyline(mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = routeColor
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(routeLine)

            // Add user marker
            updateUserMarkerPosition(mapView, startPoint, markerColor, context)

            // Add destination marker
            val destinationMarker = Marker(mapView).apply {
                position = destinationPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = MarkerCreationHelpers.createCustomPin(context, destinationMarkerColor)
                title = "Ziel"
            }
            mapView.overlays.add(destinationMarker)

            // Refresh map
            mapView.invalidate()
        }
    }

    /**
     * Fall back to simple L-shaped route if no OSRM route is available
     * This will only be used as a last resort
     */
    fun forceStreetBasedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        destinationPoint: GeoPoint,
        routeColor: Int,
        markerColor: Int,
        destinationMarkerColor: Int,
        context: Context
    ): List<GeoPoint> {
        // Clear existing routes
        val overlaysToRemove = mapView.overlays.filter {
            it is Polyline || (it is Marker && (it.title == "Ziel"))
        }
        mapView.overlays.removeAll(overlaysToRemove)

        // Create a simplified street route (L-shaped)
        val routePoints = RoutingHelpers.createSimplifiedStreetRoute(startPoint, destinationPoint)

        // Add the route to the map
        addDirectionRoute(
            mapView,
            startPoint,
            destinationPoint,
            routePoints,
            routeColor,
            markerColor,
            destinationMarkerColor,
            context
        )

        return routePoints
    }

    /**
     * Get a route between two points using OSRM (or fallback)
     */
    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        context: Context
    ): Pair<List<GeoPoint>, Double> {
        return withContext(Dispatchers.IO) {
            try {
                // Create GeoPoints
                val startPoint = GeoPoint(startLat, startLon)
                val endPoint = GeoPoint(endLat, endLon)

                // List to hold resulting route points
                val resultList = mutableListOf<GeoPoint>()
                var totalDistance = 0.0

                // Set up to get the result on the main thread
                val mainHandler = Handler(Looper.getMainLooper())

                // Calculate route with RoutingGraph (which uses OSRM)
                var routeSuccess = false

                // This is a workaround since we're in a suspend function but RoutingGraph
                // has a callback-based API
                val latch = java.util.concurrent.CountDownLatch(1)

                mainHandler.post {
                    val mapView = MapView(context) // Temporary map view for calculations

                    RoutingGraph.calculateRoute(mapView, startPoint, endPoint) { success, _, points ->
                        if (success && points.isNotEmpty()) {
                            resultList.addAll(points)

                            // Calculate total distance
                            for (i in 0 until points.size - 1) {
                                totalDistance += RoutingHelpers.calculateDistance(
                                    points[i].latitude, points[i].longitude,
                                    points[i + 1].latitude, points[i + 1].longitude
                                )
                            }

                            routeSuccess = true
                        }
                        latch.countDown()
                    }
                }

                // Wait for result
                latch.await()

                if (routeSuccess) {
                    Log.d(TAG, "OSRM route calculation successful, route has ${resultList.size} points")
                    return@withContext Pair(resultList, totalDistance)
                } else {
                    // Fallback to L-shaped route
                    Log.d(TAG, "OSRM route calculation failed, using L-shaped route")
                    val fallbackRoute =
                        RoutingHelpers.createSimplifiedStreetRoute(startPoint, endPoint)

                    // Calculate fallback distance
                    var fallbackDistance = 0.0
                    for (i in 0 until fallbackRoute.size - 1) {
                        fallbackDistance += RoutingHelpers.calculateDistance(
                            fallbackRoute[i].latitude, fallbackRoute[i].longitude,
                            fallbackRoute[i + 1].latitude, fallbackRoute[i + 1].longitude
                        )
                    }

                    return@withContext Pair(fallbackRoute, fallbackDistance)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in route calculation: ${e.message}", e)

                // Fall back to L-shaped route
                val startPoint = GeoPoint(startLat, startLon)
                val endPoint = GeoPoint(endLat, endLon)
                val fallbackRoute = RoutingHelpers.createSimplifiedStreetRoute(startPoint, endPoint)

                // Calculate fallback distance
                var fallbackDistance = 0.0
                for (i in 0 until fallbackRoute.size - 1) {
                    fallbackDistance += RoutingHelpers.calculateDistance(
                        fallbackRoute[i].latitude, fallbackRoute[i].longitude,
                        fallbackRoute[i + 1].latitude, fallbackRoute[i + 1].longitude
                    )
                }

                return@withContext Pair(fallbackRoute, fallbackDistance)
            }
        }
    }

    /**
     * Try to check for network connectivity to OSRM server
     */
    fun isOSRMServerReachable(): Boolean {
        try {
            val url = URL("https://router.project-osrm.org/route/v1/foot/1,1;2,2")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000 // 2 seconds
            connection.connect()

            val responseCode = connection.responseCode
            return responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Error checking OSRM server: ${e.message}")
            return false
        }
    }
}

 */