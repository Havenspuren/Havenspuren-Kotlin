package com.example.havenspure_kotlin_prototype.Map.Routing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
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
     * Create a glowing location marker for user position with reduced size.
     */
    fun createGlowingLocationMarker(context: Context, color: Int): BitmapDrawable {
        val size = 40 // Reduced size for better performance
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create paints for layers
        val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        outerGlowPaint.color = color
        outerGlowPaint.alpha = 40 // Very transparent
        outerGlowPaint.style = Paint.Style.FILL

        val middleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        middleGlowPaint.color = color
        middleGlowPaint.alpha = 80 // Semi-transparent
        middleGlowPaint.style = Paint.Style.FILL

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        centerPaint.color = color
        centerPaint.alpha = 255 // Fully opaque
        centerPaint.style = Paint.Style.FILL

        // Draw outer glow
        canvas.drawCircle(size/2f, size/2f, size/2f, outerGlowPaint)

        // Draw middle glow
        canvas.drawCircle(size/2f, size/2f, size/3f, middleGlowPaint)

        // Draw center dot
        canvas.drawCircle(size/2f, size/2f, size/6f, centerPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Create a custom pin marker for the destination with reduced complexity.
     */
    fun createCustomPin(context: Context, color: Int): BitmapDrawable {
        val size = 60 // Reduced size for better performance
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create paints
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pinPaint.color = color
        pinPaint.style = Paint.Style.FILL

        val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pinStrokePaint.color = AndroidColor.WHITE
        pinStrokePaint.style = Paint.Style.STROKE
        pinStrokePaint.strokeWidth = 3f

        // Create simpler pin shape
        val path = Path()
        val pinRadius = size * 0.3f
        val pinCenterX = size / 2f
        val pinCenterY = size / 4f

        // Simplified pin shape (drop shape)
        path.addCircle(pinCenterX, pinCenterY, pinRadius, Path.Direction.CW)
        path.moveTo(pinCenterX - pinRadius / 2, pinCenterY + pinRadius * 0.5f)
        path.lineTo(pinCenterX, size * 0.75f) // Point of the pin
        path.lineTo(pinCenterX + pinRadius / 2, pinCenterY + pinRadius * 0.5f)
        path.close()

        // Draw pin
        canvas.drawPath(path, pinPaint)
        canvas.drawPath(path, pinStrokePaint)

        return BitmapDrawable(context.resources, bitmap)
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
        val cacheKey = "${startLat.roundTo(5)},${startLon.roundTo(5)}-${endLat.roundTo(5)},${endLon.roundTo(5)}"

        // Check memory cache first
        routeCache[cacheKey]?.let { cacheEntry ->
            if (!cacheEntry.isExpired()) {
                Log.d("DirectionMapHelpers", "Using memory-cached route")
                return@withContext Triple(cacheEntry.routePoints, cacheEntry.distance, cacheEntry.instruction)
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

        // Use optimized street-based route
        val streetRoute = createSimplifiedStreetRoute(startPoint, endPoint)
        val distance = calculateRouteDistance(streetRoute)
        val direction = getDirectionText(
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
     * Calculate total distance of a route
     */
    private fun calculateRouteDistance(route: List<GeoPoint>): Double {
        var distance = 0.0
        for (i in 0 until route.size - 1) {
            distance += calculateDistance(
                route[i].latitude, route[i].longitude,
                route[i + 1].latitude, route[i + 1].longitude
            )
        }
        return distance
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
            val requestUrl = "https://router.project-osrm.org/route/v1/foot/$startLon,$startLat;$endLon,$endLat?overview=full&steps=true&geometries=geojson&alternatives=false"
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
                    response.append(buffer, 0, read)
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
                    routePoints.last().latitude != coordinates.getJSONArray(coordinates.length() - 1).getDouble(1) ||
                    routePoints.last().longitude != coordinates.getJSONArray(coordinates.length() - 1).getDouble(0)) {
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
     * Enhanced street route algorithm that creates more realistic paths
     * Replace this method in your DirectionMapHelpers class
     */
    fun createSimplifiedStreetRoute(
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()

        // Always start with the start point
        points.add(startPoint)

        // Calculate direct distance and angle
        val distance = calculateDistance(
            startPoint.latitude, startPoint.longitude,
            endPoint.latitude, endPoint.longitude
        )

        val bearing = calculateBearing(
            startPoint.latitude, startPoint.longitude,
            endPoint.latitude, endPoint.longitude
        )

        // Based on the bearing, determine our approach
        // For longer distances, use a multi-point approach
        if (distance > 200) {
            // Calculate delta values
            val latDelta = endPoint.latitude - startPoint.latitude
            val lonDelta = endPoint.longitude - startPoint.longitude

            // Use an L-shape approach with intermediate points
            if (Math.abs(latDelta) > Math.abs(lonDelta)) {
                // Vertical first, then horizontal

                // First move vertically 80%
                val midPoint1 = GeoPoint(
                    startPoint.latitude + (latDelta * 0.8),
                    startPoint.longitude
                )
                points.add(midPoint1)

                // Then move horizontally
                points.add(GeoPoint(
                    startPoint.latitude + (latDelta * 0.8),
                    endPoint.longitude
                ))
            } else {
                // Horizontal first, then vertical

                // First move horizontally 80%
                val midPoint1 = GeoPoint(
                    startPoint.latitude,
                    startPoint.longitude + (lonDelta * 0.8)
                )
                points.add(midPoint1)

                // Then move vertically
                points.add(GeoPoint(
                    endPoint.latitude,
                    startPoint.longitude + (lonDelta * 0.8)
                ))
            }
        } else {
            // For shorter routes, use a simpler zigzag approach

            // Add a midpoint with slight offset to create a zigzag
            val midPoint = GeoPoint(
                (startPoint.latitude + endPoint.latitude) / 2,
                (startPoint.longitude + endPoint.longitude) / 2
            )

            // Offset the midpoint perpendicular to the route direction
            val offsetLatitude = Math.cos(Math.toRadians(bearing + 90)) * 0.0003
            val offsetLongitude = Math.sin(Math.toRadians(bearing + 90)) * 0.0003

            val offsetMidPoint = GeoPoint(
                midPoint.latitude + offsetLatitude,
                midPoint.longitude + offsetLongitude
            )

            points.add(offsetMidPoint)
        }

        // Add end point
        points.add(endPoint)

        return points
    }
    /**
     * This method forces the map to use L-shaped routes by adding it to the DirectionMapComponent class.
     * Insert this method call at the beginning of your map initialization.
     */
    private fun forceStreetBasedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routeColor: Int,
        userLocationColor: Int,
        destinationColor: Int,
        context: Context
    ) {
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
        val helpers = DirectionMapHelpers
        helpers.addDirectionRoute(
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
            icon = createCustomPin(context, destinationColor)
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

        // Add user location marker
        val userMarker = Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createGlowingLocationMarker(context, userLocationColor)
            title = null
            setInfoWindow(null)
        }
        mapView.overlays.add(userMarker)

        // Limit number of direction arrows for performance
        if (routePoints.size > 3) {
            addOptimizedDirectionArrows(mapView, routePoints, routeColor, context)
        }

        // Refresh the map
        mapView.invalidate()
    }
    /**
     * Create a small arrow icon for direction indicators along the route.
     */
    fun createDirectionArrow(context: Context, color: Int): BitmapDrawable {
        val size = 24 // Small size for route arrows
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create paint for arrow
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        arrowPaint.color = color
        arrowPaint.style = Paint.Style.FILL
        arrowPaint.strokeWidth = 2f

        // Create arrow pointing up
        val path = Path()
        path.moveTo(size / 2f, 0f) // Top point
        path.lineTo(size.toFloat(), size.toFloat()) // Bottom right
        path.lineTo(size / 2f, size * 0.7f) // Middle bottom
        path.lineTo(0f, size.toFloat()) // Bottom left
        path.close()

        // Draw arrow
        canvas.drawPath(path, arrowPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Add optimized direction arrows (fewer arrows for better performance)
     */
    private fun addOptimizedDirectionArrows(
        mapView: MapView,
        routePoints: List<GeoPoint>,
        arrowColor: Int,
        context: Context
    ) {
        // Need at least 3 points for a meaningful route with direction
        if (routePoints.size < 3) return

        // Calculate total distance
        val totalDistance = calculateRouteDistance(routePoints)

        // Optimize number of arrows based on route length
        val numArrows = when {
            totalDistance < 500 -> 1  // Short route: just 1 arrow
            totalDistance < 2000 -> 2 // Medium route: 2 arrows
            else -> 3                 // Long route: 3 arrows
        }

        // Place arrows at evenly spaced intervals
        for (i in 1..numArrows) {
            val fraction = i.toDouble() / (numArrows + 1)
            val targetDistance = totalDistance * fraction

            // Find the appropriate segment
            var currentDistance = 0.0
            for (j in 0 until routePoints.size - 1) {
                val segmentLength = calculateDistance(
                    routePoints[j].latitude, routePoints[j].longitude,
                    routePoints[j + 1].latitude, routePoints[j + 1].longitude
                )

                val nextDistance = currentDistance + segmentLength

                if (nextDistance >= targetDistance) {
                    // This is the segment where our arrow should be placed
                    val segmentFraction = (targetDistance - currentDistance) / segmentLength

                    val arrowLat = routePoints[j].latitude +
                            segmentFraction * (routePoints[j + 1].latitude - routePoints[j].latitude)
                    val arrowLon = routePoints[j].longitude +
                            segmentFraction * (routePoints[j + 1].longitude - routePoints[j].longitude)

                    // Create arrow marker
                    val arrowPoint = GeoPoint(arrowLat, arrowLon)
                    val arrowMarker = Marker(mapView).apply {
                        position = arrowPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                        // Calculate bearing
                        val bearing = calculateBearing(
                            routePoints[j].latitude, routePoints[j].longitude,
                            routePoints[j + 1].latitude, routePoints[j + 1].longitude
                        )

                        rotation = bearing.toFloat()
                        icon = createDirectionArrow(context, arrowColor)
                        setInfoWindow(null)
                        title = null
                    }

                    mapView.overlays.add(arrowMarker)
                    break
                }

                currentDistance = nextDistance
            }
        }
    }

    /**
     * Calculate distance between two points in meters.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Format distance in a human-readable way.
     */
    fun formatDistance(distanceMeters: Double): String {
        return when {
            distanceMeters < 1000 -> "Entfernung: ${distanceMeters.toInt()} m"
            else -> "Entfernung: ${String.format("%.1f", distanceMeters / 1000)} km"
        }
    }

    /**
     * Get a human-readable direction based on coordinates.
     */
    fun getDirectionText(fromLocation: LocationData, toLocation: LocationData): String {
        // Calculate bearing between points
        val bearing = calculateBearing(
            fromLocation.latitude, fromLocation.longitude,
            toLocation.latitude, toLocation.longitude
        )

        // Convert bearing to cardinal direction
        return when {
            bearing > 337.5 || bearing <= 22.5 -> "Gehen Sie nach Norden"
            bearing > 22.5 && bearing <= 67.5 -> "Gehen Sie nach Nordosten"
            bearing > 67.5 && bearing <= 112.5 -> "Gehen Sie nach Osten"
            bearing > 112.5 && bearing <= 157.5 -> "Gehen Sie nach Südosten"
            bearing > 157.5 && bearing <= 202.5 -> "Gehen Sie nach Süden"
            bearing > 202.5 && bearing <= 247.5 -> "Gehen Sie nach Südwesten"
            bearing > 247.5 && bearing <= 292.5 -> "Gehen Sie nach Westen"
            bearing > 292.5 && bearing <= 337.5 -> "Gehen Sie nach Nordwesten"
            else -> "Folgen Sie der Strecke"
        }
    }

    /**
     * Calculate bearing between two points (0-360 degrees).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val startLat = Math.toRadians(lat1)
        val startLng = Math.toRadians(lon1)
        val endLat = Math.toRadians(lat2)
        val endLng = Math.toRadians(lon2)

        val dLng = endLng - startLng

        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) -
                sin(startLat) * cos(endLat) * cos(dLng)

        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) {
            bearing += 360
        }

        return bearing
    }
}