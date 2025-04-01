package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.annotation.SuppressLint
import android.location.Location
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Utility class for map-related calculations and conversions
 */
object MapUtils {
    private const val TAG = "MapUtils"

    /**
     * Decode a polyline string into a list of GeoPoints
     * Uses the Google polyline algorithm
     *
     * @param encoded The encoded polyline string
     * @return List of GeoPoints representing the polyline
     */
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = GeoPoint(lat * 1e-5, lng * 1e-5)
            points.add(p)
        }
        return points
    }

    /**
     * Calculate bearing between two points in degrees (0-360)
     *
     * @param lat1 Starting point latitude
     * @param lon1 Starting point longitude
     * @param lat2 Ending point latitude
     * @param lon2 Ending point longitude
     * @return Bearing in degrees (0-360 where 0 is north)
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val startLat = Math.toRadians(lat1)
        val startLng = Math.toRadians(lon1)
        val endLat = Math.toRadians(lat2)
        val endLng = Math.toRadians(lon2)

        val dLong = endLng - startLng
        val dPhi = Math.log(
            tan(endLat / 2.0 + Math.PI / 4.0) / tan(startLat / 2.0 + Math.PI / 4.0)
        )

        val bearing = if (Math.abs(dLong) > Math.PI) {
            if (dLong > 0.0) {
                -(2.0 * Math.PI - dLong)
            } else {
                2.0 * Math.PI + dLong
            }
        } else {
            dLong
        }

        val degrees = (Math.toDegrees(atan2(bearing, dPhi)) + 360) % 360
        return degrees.toFloat()
    }

    /**
     * Determine if a turn from point1 to point2 to point3 is a left turn
     *
     * @param lat1 Point 1 latitude
     * @param lon1 Point 1 longitude
     * @param lat2 Point 2 latitude
     * @param lon2 Point 2 longitude
     * @param lat3 Point 3 latitude
     * @param lon3 Point 3 longitude
     * @return True if the turn is to the left, false otherwise
     */
    fun isTurnLeft(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        lat3: Double, lon3: Double
    ): Boolean {
        val bearing1 = calculateBearing(lat1, lon1, lat2, lon2)
        val bearing2 = calculateBearing(lat2, lon2, lat3, lon3)

        // Calculate turn angle
        var angleDiff = bearing2 - bearing1
        if (angleDiff < -180) angleDiff += 360
        if (angleDiff > 180) angleDiff -= 360

        // Negative angle means turn left
        return angleDiff < 0
    }

    /**
     * Get distance between two points in meters
     */
    fun getDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Find the closest point on a route to a given location
     *
     * @param location Current location
     * @param routePoints List of points that make up the route
     * @return Index of the closest point and distance to it
     */
    fun findClosestPointOnRoute(
        location: LocationDataOSRM,
        routePoints: List<GeoPoint>
    ): Pair<Int, Double> {
        if (routePoints.isEmpty()) {
            return Pair(-1, Double.MAX_VALUE)
        }

        var closestPointIndex = 0
        var minDistance = Double.MAX_VALUE

        routePoints.forEachIndexed { index, point ->
            val distance = getDistance(
                location.latitude, location.longitude,
                point.latitude, point.longitude
            ).toDouble()

            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = index
            }
        }

        return Pair(closestPointIndex, minDistance)
    }

    /**
     * Calculate the remaining distance along a route from a given point
     *
     * @param location Current location
     * @param routePoints List of points that make up the route
     * @return Remaining distance in meters
     */
    fun calculateRemainingDistance(
        location: LocationDataOSRM,
        routePoints: List<GeoPoint>
    ): Double {
        if (routePoints.isEmpty()) {
            return 0.0
        }

        // Find closest point on route
        val (closestPointIndex, distance) = findClosestPointOnRoute(location, routePoints)

        if (closestPointIndex < 0 || closestPointIndex >= routePoints.size - 1) {
            return distance
        }

        // Calculate remaining distance along route
        var remainingDistance = 0.0
        for (i in closestPointIndex until routePoints.size - 1) {
            remainingDistance += routePoints[i].distanceToAsDouble(routePoints[i + 1])
        }

        return remainingDistance
    }

    /**
     * Format distance for display in German
     *
     * @param distance Distance in meters
     * @return Formatted distance string
     */
    private fun formatDistance(distance: Double): String {
        return when {
            distance < 30 -> "Jetzt"
            distance < 100 -> "In Kürze"
            distance < 1000 -> "In ${(distance / 10).toInt() * 10} Metern"
            else -> "In ${String.format("%.1f", distance / 1000)} km"
        }
    }

    /**
     * Format distance for display in German (showing absolute distance)
     *
     * @param distance Distance in meters
     * @return Formatted distance string
     */
    @SuppressLint("DefaultLocale")
    fun formatDistanceAbsolute(distance: Double): String {
        return when {
            distance < 1000 -> "${distance.toInt()} Meter"
            else -> "${String.format("%.1f", distance / 1000)} km"
        }
    }

    /**
     * Check if user is off route
     *
     * @param location Current location
     * @param routePoints List of points that make up the route
     * @param maxDistance Maximum allowed distance from route
     * @return True if user is off route
     */
    fun isOffRoute(
        location: LocationDataOSRM,
        routePoints: List<GeoPoint>,
        maxDistance: Double = 30.0
    ): Boolean {
        val (_, distance) = findClosestPointOnRoute(location, routePoints)
        return distance > maxDistance
    }

    /**
     * Calculate a natural-looking navigation instruction based on user location and route
     *
     * @param location Current location
     * @param routePoints Route points
     * @param destination Destination location
     * @return Navigation instruction text
     */
    fun getNavigationInstruction(
        location: LocationDataOSRM,
        routePoints: List<GeoPoint>,
        destination: LocationDataOSRM
    ): String {
        if (routePoints.isEmpty()) {
            return "Folgen Sie der Route"
        }

        // Check if we're near the destination
        val distToDest = LocationDataOSRM.distanceBetween(location, destination)
        if (distToDest < 30) {
            return "Sie haben Ihr Ziel erreicht"
        }

        // Find closest point on route
        val (closestPointIndex, distance) = findClosestPointOnRoute(location, routePoints)

        // If we're too far from the route
        if (distance > 50) {
            return "Kehren Sie zur Route zurück"
        }

        // If we're near the end of the route
        if (closestPointIndex >= routePoints.size - 2) {
            return "Sie nähern sich Ihrem Ziel"
        }

        // Look ahead for turns
        val lookaheadDistance = 50.0 // Look ahead 50 meters
        var currentDistance = 0.0
        var i = closestPointIndex

        while (i < routePoints.size - 2 && currentDistance < lookaheadDistance) {
            currentDistance += routePoints[i].distanceToAsDouble(routePoints[i + 1])

            // Check if there's a significant turn
            if (i < routePoints.size - 2) {
                val bearing1 = calculateBearing(
                    routePoints[i].latitude, routePoints[i].longitude,
                    routePoints[i + 1].latitude, routePoints[i + 1].longitude
                )
                val bearing2 = calculateBearing(
                    routePoints[i + 1].latitude, routePoints[i + 1].longitude,
                    routePoints[i + 2].latitude, routePoints[i + 2].longitude
                )

                // Calculate turn angle
                var angleDiff = bearing2 - bearing1
                if (angleDiff < -180) angleDiff += 360
                if (angleDiff > 180) angleDiff -= 360

                // If the angle difference is significant, there's a turn
                if (Math.abs(angleDiff) > 30) {
                    val turnInstruction = when {
                        angleDiff > 150 -> "Bitte wenden Sie"
                        angleDiff > 80 -> "Biegen Sie scharf rechts ab"
                        angleDiff > 30 -> "Biegen Sie rechts ab"
                        angleDiff > 10 -> "Halten Sie sich rechts"
                        angleDiff > -10 -> "Fahren Sie geradeaus"
                        angleDiff > -30 -> "Halten Sie sich links"
                        angleDiff > -80 -> "Biegen Sie links ab"
                        angleDiff > -150 -> "Biegen Sie scharf links ab"
                        else -> "Bitte wenden Sie"
                    }

                    return "${formatDistance(currentDistance)}: $turnInstruction"
                }
            }

            i++
        }

        // If no turns found, provide general guidance
        val remainingDistance = calculateRemainingDistance(location, routePoints)
        return when {
            remainingDistance < 100 -> "Sie nähern sich Ihrem Ziel"
            else -> "Folgen Sie der Route (${formatDistanceAbsolute(remainingDistance)} verbleibend)"
        }
    }
}