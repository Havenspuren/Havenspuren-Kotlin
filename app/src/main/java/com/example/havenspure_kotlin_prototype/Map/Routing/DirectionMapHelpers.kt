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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Helper methods for the DirectionMapComponent
 */
object DirectionMapHelpers {

    /**
     * Create a glowing location marker for user position.
     */
    fun createGlowingLocationMarker(context: Context, color: Int): BitmapDrawable {
        val size = 48 // Size of the marker in pixels
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
     * Create a custom pin marker for the destination.
     */
    fun createCustomPin(context: Context, color: Int): BitmapDrawable {
        val size = 72 // Size of the marker in pixels
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

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        circlePaint.color = AndroidColor.WHITE
        circlePaint.style = Paint.Style.FILL

        // Create pin shape
        val path = Path()

        // Draw pin body (drop shape)
        val pinRadius = size * 0.3f
        val pinCenterX = size / 2f
        val pinCenterY = size / 4f

        path.addCircle(pinCenterX, pinCenterY, pinRadius, Path.Direction.CW)

        // Add pin point
        path.moveTo(pinCenterX - pinRadius / 2, pinCenterY + pinRadius * 0.8f)
        path.lineTo(pinCenterX, size * 0.75f) // Point of the pin
        path.lineTo(pinCenterX + pinRadius / 2, pinCenterY + pinRadius * 0.8f)
        path.close()

        // Draw pin with white outline
        canvas.drawPath(path, pinPaint)
        canvas.drawPath(path, pinStrokePaint)

        // Draw white circle in center of pin
        canvas.drawCircle(pinCenterX, pinCenterY, pinRadius * 0.5f, circlePaint)

        return BitmapDrawable(context.resources, bitmap)
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
     * Fetch route from OSRM API. Returns a Triple with:
     * - List of route points
     * - Total distance in meters
     * - First navigation instruction
     */
    suspend fun fetchOsrmRoute(
        startLon: Double, startLat: Double,
        endLon: Double, endLat: Double
    ): Triple<List<GeoPoint>, Double, String>? = withContext(Dispatchers.IO) {
        try {
            // OSRM API URL for foot navigation with proper parameters to ensure street routing
            // geometries=geojson ensures we get detailed path information
            // overview=full ensures we get all route points
            // steps=true ensures we get turn-by-turn information
            val url = URL("https://router.project-osrm.org/route/v1/foot/$startLon,$startLat;$endLon,$endLat?overview=full&steps=true&geometries=geojson&alternatives=false")

            Log.d("DirectionMapHelpers", "Requesting OSRM route: $url")

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // 5 seconds timeout - reduced for better performance
            connection.readTimeout = 7000 // 7 seconds read timeout
            connection.useCaches = true // Use caching for better performance

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("DirectionMapHelpers", "HTTP error code: $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }

            val jsonResponse = JSONObject(response)

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
            val legs = route.getJSONArray("legs")
            val firstLeg = legs.getJSONObject(0)

            // Get total distance in meters
            val distance = route.getDouble("distance")

            // Extract the geometry (GeoJSON format)
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            // Parse into GeoPoints
            val routePoints = mutableListOf<GeoPoint>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                // GeoJSON format is [longitude, latitude]
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                routePoints.add(GeoPoint(lat, lon))
            }

            // Ensure we have enough points for a proper route visualization
            // If needed, interpolate additional points between the existing ones
            val enhancedRoutePoints = if (routePoints.size < 10 && routePoints.size >= 2) {
                interpolateRoutePoints(routePoints)
            } else {
                routePoints
            }

            Log.d("DirectionMapHelpers", "Parsed ${enhancedRoutePoints.size} route points")

            // Get first navigation instruction
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

            Log.d("DirectionMapHelpers", "First instruction: $firstInstruction")
            return@withContext Triple(enhancedRoutePoints, distance, firstInstruction)
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error fetching OSRM route: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Interpolate additional points between existing route points to ensure smooth
     * rendering of the route line, especially when there are very few points.
     */
    private fun interpolateRoutePoints(originalPoints: List<GeoPoint>): List<GeoPoint> {
        if (originalPoints.size < 2) return originalPoints

        val interpolatedPoints = mutableListOf<GeoPoint>()

        // Add the first point
        interpolatedPoints.add(originalPoints.first())

        // Interpolate between each pair of points
        for (i in 0 until originalPoints.size - 1) {
            val startPoint = originalPoints[i]
            val endPoint = originalPoints[i + 1]

            // Calculate distance between points
            val distance = calculateDistance(
                startPoint.latitude, startPoint.longitude,
                endPoint.latitude, endPoint.longitude
            )

            // If points are far apart, add interpolated points
            if (distance > 100) { // For points more than 100m apart
                val segmentCount = (distance / 50).toInt() // One point every 50 meters

                for (j in 1 until segmentCount) {
                    val fraction = j.toDouble() / segmentCount

                    // Linear interpolation between points
                    val lat = startPoint.latitude + fraction * (endPoint.latitude - startPoint.latitude)
                    val lon = startPoint.longitude + fraction * (endPoint.longitude - startPoint.longitude)

                    interpolatedPoints.add(GeoPoint(lat, lon))
                }
            }

            // Add the end point
            interpolatedPoints.add(endPoint)
        }

        return interpolatedPoints
    }

    /**
     * Get street data to help with routing visualization
     */
    suspend fun fetchAllStreets(
        startLon: Double, startLat: Double,
        endLon: Double, endLat: Double
    ): List<GeoPoint> = withContext(Dispatchers.IO) {
        try {
            // Instead of a complex Overpass query, let's create a more realistic route
            return@withContext createRealisticRoute(
                GeoPoint(startLat, startLon),
                GeoPoint(endLat, endLon)
            )
        } catch (e: Exception) {
            Log.e("DirectionMapHelpers", "Error fetching street data: ${e.message}", e)

            // Return a direct route as fallback
            return@withContext createDirectRoute(
                GeoPoint(startLat, startLon),
                GeoPoint(endLat, endLon)
            )
        }
    }

    /**
     * Create a fallback direct route with some interpolated points
     */
    fun createDirectRoute(
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        numWaypoints: Int = 10
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(startPoint)

        // Add some intermediate points to make the route look more natural
        for (i in 1..numWaypoints) {
            val fraction = i.toDouble() / (numWaypoints + 1)
            val lat = startPoint.latitude + fraction * (endPoint.latitude - startPoint.latitude)
            val lon = startPoint.longitude + fraction * (endPoint.longitude - startPoint.longitude)

            // Add small random variation to make it look less straight
            val latVariation = (Math.random() - 0.5) * 0.0005 // ~50m random variation
            val lonVariation = (Math.random() - 0.5) * 0.0005

            points.add(GeoPoint(lat + latVariation, lon + lonVariation))
        }

        points.add(endPoint)
        return points
    }

    /**
     * Create a more realistic route path
     */
    fun createRealisticRoute(
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        numWaypoints: Int = 8
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(startPoint)

        // Get bearing from start to end
        val bearing = calculateBearing(
            startPoint.latitude, startPoint.longitude,
            endPoint.latitude, endPoint.longitude
        )

        // Create a path that resembles a road network by using 90 degree segments
        var currentLat = startPoint.latitude
        var currentLon = startPoint.longitude

        // Decide if we go horizontal first or vertical first based on bearing
        val horizontalFirst = bearing in 45.0..135.0 || bearing in 225.0..315.0

        if (horizontalFirst) {
            // Move horizontally first
            val destLon = endPoint.longitude
            val lonDiff = destLon - currentLon
            val midLon = currentLon + (lonDiff * 0.8) // Go 80% of the way horizontally

            points.add(GeoPoint(currentLat, midLon))
            currentLon = midLon

            // Then move vertically toward destination
            points.add(GeoPoint(endPoint.latitude, currentLon))
        } else {
            // Move vertically first
            val destLat = endPoint.latitude
            val latDiff = destLat - currentLat
            val midLat = currentLat + (latDiff * 0.8) // Go 80% of the way vertically

            points.add(GeoPoint(midLat, currentLon))
            currentLat = midLat

            // Then move horizontally toward destination
            points.add(GeoPoint(currentLat, endPoint.longitude))
        }

        // Add the final destination
        points.add(endPoint)

        return points
    }

    /**
     * Add route line, direction arrows, and markers for the journey.
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
        // Destination marker with red color
        val destinationMarker = Marker(mapView).apply {
            position = endPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Create custom red pin
            val pinDrawable = createCustomPin(context, destinationColor)
            icon = pinDrawable

            title = "Ziel"
            // No snippet to keep UI clean for navigation
        }
        mapView.overlays.add(destinationMarker)

        // Main route line - thick green line like in navigation apps
        if (routePoints.size >= 2) {
            val routeLine = Polyline(mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = routeColor
                outlinePaint.strokeWidth = 10f // Thick line for visibility
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

        // Add glowing user location marker
        val userMarker = Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // Use the glowing marker drawable
            icon = createGlowingLocationMarker(context, userLocationColor)

            title = null // No title for cleaner navigation view
            setInfoWindow(null) // No info window
        }
        mapView.overlays.add(userMarker)

        // Add direction arrows along the route
        addDirectionArrows(mapView, routePoints, routeColor, context)

        // Refresh the map
        mapView.invalidate()
    }

    /**
     * Add direction arrows along the route to indicate travel direction.
     */
    fun addDirectionArrows(
        mapView: MapView,
        routePoints: List<GeoPoint>,
        arrowColor: Int,
        context: Context
    ) {
        // Need at least 2 points for direction
        if (routePoints.size <= 1) return

        // Calculate total distance of the route
        var totalDistance = 0.0
        for (i in 0 until routePoints.size - 1) {
            totalDistance += calculateDistance(
                routePoints[i].latitude, routePoints[i].longitude,
                routePoints[i + 1].latitude, routePoints[i + 1].longitude
            )
        }

        // Place arrows every X meters depending on total distance
        val arrowSpacing = when {
            totalDistance < 100 -> 20.0 // Close - show more arrows
            totalDistance < 500 -> 50.0
            totalDistance < 1000 -> 100.0
            totalDistance < 5000 -> 500.0
            else -> 1000.0 // Far away - fewer arrows
        }

        // Calculate number of arrows (at least 1, at most 20 for performance)
        val numArrows = min(20, max(1, (totalDistance / arrowSpacing).toInt()))

        if (numArrows > 1) {
            // Place arrows at equidistant segments along the route
            var currentDistance = 0.0
            val segmentDistance = totalDistance / numArrows
            var nextArrowAt = segmentDistance

            for (i in 0 until routePoints.size - 1) {
                val segmentLength = calculateDistance(
                    routePoints[i].latitude, routePoints[i].longitude,
                    routePoints[i + 1].latitude, routePoints[i + 1].longitude
                )

                currentDistance += segmentLength

                // If we've passed the distance for the next arrow
                if (currentDistance >= nextArrowAt) {
                    // Calculate what fraction of the current segment to use
                    val segmentFraction = (segmentLength - (currentDistance - nextArrowAt)) / segmentLength

                    // Get coordinates for arrow
                    val arrowLat = routePoints[i].latitude +
                            segmentFraction * (routePoints[i + 1].latitude - routePoints[i].latitude)
                    val arrowLon = routePoints[i].longitude +
                            segmentFraction * (routePoints[i + 1].longitude - routePoints[i].longitude)

                    // Create arrow marker
                    val arrowPoint = GeoPoint(arrowLat, arrowLon)
                    val arrowMarker = Marker(mapView).apply {
                        position = arrowPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                        // Calculate bearing based on the route segment
                        val bearing = calculateBearing(
                            routePoints[i].latitude, routePoints[i].longitude,
                            routePoints[i + 1].latitude, routePoints[i + 1].longitude
                        )

                        // Set rotation angle
                        rotation = bearing.toFloat()

                        // Create small arrow icon
                        icon = createDirectionArrow(context, arrowColor)

                        // No info window or title
                        setInfoWindow(null)
                        title = null
                    }

                    mapView.overlays.add(arrowMarker)

                    // Set the next arrow distance target
                    nextArrowAt += segmentDistance
                }
            }
        } else if (numArrows == 1 && routePoints.size >= 3) {
            // If only one arrow, place it in the middle of the route
            val middleIndex = routePoints.size / 2

            // Create arrow marker at middle point
            val arrowMarker = Marker(mapView).apply {
                position = routePoints[middleIndex]
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Calculate bearing based on surrounding points
                val bearing = calculateBearing(
                    routePoints[middleIndex - 1].latitude, routePoints[middleIndex - 1].longitude,
                    routePoints[middleIndex + 1].latitude, routePoints[middleIndex + 1].longitude
                )

                // Set rotation angle
                rotation = bearing.toFloat()

                // Create small arrow icon
                icon = createDirectionArrow(context, arrowColor)

                // No info window or title
                setInfoWindow(null)
                title = null
            }

            mapView.overlays.add(arrowMarker)
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

    /**
     * Get closest ahead instruction from the route
     */
    fun getClosestAheadInstruction(userPoint: GeoPoint, routePoints: List<GeoPoint>): String {
        // Default instruction if we can't determine a better one
        return "Folgen Sie der Strecke"
    }
}