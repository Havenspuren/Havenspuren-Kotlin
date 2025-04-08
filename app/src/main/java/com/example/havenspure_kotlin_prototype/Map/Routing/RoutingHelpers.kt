package com.example.havenspure_kotlin_prototype.Map.Routing

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Helper methods for route calculations, directions, and navigation
 */
object RoutingHelpers {
    private const val TAG = "RoutingHelpers"

    /**
     * Calculate total distance of a route
     */
    fun calculateRouteDistance(route: List<GeoPoint>): Double {
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
     * Enhanced street route algorithm that creates more realistic paths
     * This is only used as a fallback when OSRM routing fails
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

        // Create a more zigzag route that looks a bit more like streets
        // For longer distances, use multiple zigzags to simulate a street grid
        if (distance > 500) {
            // For very long routes, create a multi-segment route
            val segments = max(2, min(5, (distance / 500).toInt()))
            val latStep = (endPoint.latitude - startPoint.latitude) / segments
            val lonStep = (endPoint.longitude - startPoint.longitude) / segments

            var currentLat = startPoint.latitude
            var currentLon = startPoint.longitude

            for (i in 1 until segments) {
                // Calculate next point
                val nextLat = startPoint.latitude + latStep * i
                val nextLon = startPoint.longitude + lonStep * i

                // Add a slight zigzag
                val offsetBearing = bearing + if (i % 2 == 0) 30.0 else -30.0
                val offsetDistance = distance * 0.05 // 5% of total distance

                val offsetLat = currentLat + offsetDistance * sin(Math.toRadians(offsetBearing)) / 111111.0
                val offsetLon = currentLon + offsetDistance * cos(Math.toRadians(offsetBearing)) /
                        (111111.0 * cos(Math.toRadians(currentLat)))

                // Add offset point
                points.add(GeoPoint(offsetLat, offsetLon))

                // Add actual segment point
                points.add(GeoPoint(nextLat, nextLon))

                // Update current position
                currentLat = nextLat
                currentLon = nextLon
            }
        } else if (distance > 200) {
            // For medium distances, use an improved zigzag approach
            // Mid-point with offset perpendicular to main direction
            val midLat = (startPoint.latitude + endPoint.latitude) / 2
            val midLon = (startPoint.longitude + endPoint.longitude) / 2

            // Calculate perpendicular bearing
            val perpBearing = (bearing + 90) % 360

            // Create offset (approximately 15% of the total distance)
            val offsetDistance = distance * 0.15
            val offsetLat = midLat + offsetDistance * sin(Math.toRadians(perpBearing)) / 111111.0
            val offsetLon = midLon + offsetDistance * cos(Math.toRadians(perpBearing)) /
                    (111111.0 * cos(Math.toRadians(midLat)))

            // Add midpoint with offset
            points.add(GeoPoint(offsetLat, offsetLon))

            // Add another point to create a more street-like path
            val thirdLat = midLat + (endPoint.latitude - midLat) * 0.5
            val thirdLon = offsetLon + (endPoint.longitude - offsetLon) * 0.5
            points.add(GeoPoint(thirdLat, thirdLon))
        } else {
            // For shorter routes, use a simple zigzag
            val midLat = (startPoint.latitude + endPoint.latitude) / 2
            val midLon = (startPoint.longitude + endPoint.longitude) / 2

            // Add a slight offset to create a zigzag
            val offsetLatitude = cos(Math.toRadians(bearing + 90)) * 0.0003
            val offsetLongitude = sin(Math.toRadians(bearing + 90)) * 0.0003

            val offsetMidPoint = GeoPoint(
                midLat + offsetLatitude,
                midLon + offsetLongitude
            )

            points.add(offsetMidPoint)
        }

        // Add end point
        points.add(endPoint)

        return points
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
    @SuppressLint("DefaultLocale")
    fun formatDistance(distanceMeters: Double): String {
        return when {
            distanceMeters < 1000 -> "Entfernung: ${distanceMeters.toInt()} m"
            else -> "Entfernung: ${String.format("%.1f", distanceMeters / 1000)} km"
        }
    }

    /**
     * Get a simple human-readable direction based on coordinates.
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
     * Get formal traffic directions based on route and current position.
     * This is used as a fallback when OSRM instructions are not available.
     */
    @SuppressLint("DefaultLocale")
    fun getFormattedDirections(
        currentLocation: LocationData,
        routePoints: List<GeoPoint>,
        destinationLocation: LocationData
    ): String {
        try {
            // Use RoutingGraph to get directions if available
            val routingGraphDirection = RoutingGraph.getNavigationInstruction(
                currentLocation, destinationLocation
            )

            if (routingGraphDirection != "Folgen Sie der Route") {
                return routingGraphDirection
            }

            // Find closest point on route to current location
            var closestPointIndex = 0
            var minDistance = Double.MAX_VALUE

            routePoints.forEachIndexed { index, point ->
                val dist = calculateDistance(
                    currentLocation.latitude, currentLocation.longitude,
                    point.latitude, point.longitude
                )
                if (dist < minDistance) {
                    minDistance = dist
                    closestPointIndex = index
                }
            }

            // Check if we're off route
            if (minDistance > 50) {
                return "Kehren Sie zur Route zurück"
            }

            // If we're at the last segment, we're approaching destination
            if (closestPointIndex >= routePoints.size - 2) {
                val distToDestination = calculateDistance(
                    currentLocation.latitude, currentLocation.longitude,
                    destinationLocation.latitude, destinationLocation.longitude
                )

                return if (distToDestination < 50) {
                    "Sie haben Ihr Ziel erreicht"
                } else {
                    "Sie nähern sich Ihrem Ziel"
                }
            }

            // Look ahead to next significant turn
            var nextTurnIndex = -1
            var maxAngleChange = 25.0 // Minimum angle to consider a turn

            // Look ahead up to 5 points to find significant turns
            val lookAheadLimit = min(closestPointIndex + 5, routePoints.size - 1)

            for (i in closestPointIndex + 1 until lookAheadLimit) {
                if (i > 0 && i < routePoints.size - 1) {
                    val prevBearing = calculateBearing(
                        routePoints[i - 1].latitude, routePoints[i - 1].longitude,
                        routePoints[i].latitude, routePoints[i].longitude
                    )

                    val nextBearing = calculateBearing(
                        routePoints[i].latitude, routePoints[i].longitude,
                        routePoints[i + 1].latitude, routePoints[i + 1].longitude
                    )

                    // Calculate absolute angle difference
                    var angleDiff = abs(nextBearing - prevBearing)
                    if (angleDiff > 180) {
                        angleDiff = 360 - angleDiff
                    }

                    if (angleDiff > maxAngleChange) {
                        maxAngleChange = angleDiff
                        nextTurnIndex = i
                        break
                    }
                }
            }

            // If we found a significant turn
            if (nextTurnIndex != -1) {
                val prevBearing = calculateBearing(
                    routePoints[nextTurnIndex - 1].latitude, routePoints[nextTurnIndex - 1].longitude,
                    routePoints[nextTurnIndex].latitude, routePoints[nextTurnIndex].longitude
                )

                val nextBearing = calculateBearing(
                    routePoints[nextTurnIndex].latitude, routePoints[nextTurnIndex].longitude,
                    routePoints[nextTurnIndex + 1].latitude, routePoints[nextTurnIndex + 1].longitude
                )

                // Distance to the turn
                val distanceToTurn = calculateDistanceBetweenPoints(
                    routePoints.subList(closestPointIndex, nextTurnIndex + 1)
                )

                val formattedDistance = if (distanceToTurn < 30) {
                    "Jetzt"
                } else if (distanceToTurn < 100) {
                    "In Kürze"
                } else if (distanceToTurn < 1000) {
                    "In ${(distanceToTurn / 10).toInt() * 10} Metern"
                } else {
                    "In ${String.format("%.1f", distanceToTurn / 1000)} km"
                }

                // Calculate turn direction
                var angleDiff = nextBearing - prevBearing
                if (angleDiff < -180) angleDiff += 360
                if (angleDiff > 180) angleDiff -= 360

                val turnDirection = when {
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

                return "$formattedDistance: $turnDirection"
            }

            // If no significant turn was found, use the direction to the destination
            val bearing = calculateBearing(
                currentLocation.latitude, currentLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude
            )

            return when {
                bearing > 337.5 || bearing <= 22.5 -> "Fahren Sie weiter Richtung Norden"
                bearing > 22.5 && bearing <= 67.5 -> "Fahren Sie weiter Richtung Nordosten"
                bearing > 67.5 && bearing <= 112.5 -> "Fahren Sie weiter Richtung Osten"
                bearing > 112.5 && bearing <= 157.5 -> "Fahren Sie weiter Richtung Südosten"
                bearing > 157.5 && bearing <= 202.5 -> "Fahren Sie weiter Richtung Süden"
                bearing > 202.5 && bearing <= 247.5 -> "Fahren Sie weiter Richtung Südwesten"
                bearing > 247.5 && bearing <= 292.5 -> "Fahren Sie weiter Richtung Westen"
                bearing > 292.5 && bearing <= 337.5 -> "Fahren Sie weiter Richtung Nordwesten"
                else -> "Folgen Sie der Route"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting formatted directions: ${e.message}", e)
            return "Folgen Sie der Route"
        }
    }

    /**
     * Calculate the total distance between a series of points
     */
    private fun calculateDistanceBetweenPoints(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += calculateDistance(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return totalDistance
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
     * Get formatted directions using GraphHopper or OSRM
     * This method serves as a bridge to newer routing systems
     */
    fun getGraphHopperDirections(
        userLocation: LocationData,
        routePoints: List<GeoPoint>,
        destinationLocation: LocationData,
        context: Context
    ): String {
        try {
            // Try to get directions from RoutingGraph first
            val directions = RoutingGraph.getNavigationInstruction(
                userLocation, destinationLocation
            )

            // If valid directions were found, return them
            if (directions != "Folgen Sie der Route" && directions != "Keine Route berechnet") {
                return directions
            }

            // Fall back to calculated directions from points
            return getFormattedDirections(userLocation, routePoints, destinationLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting directions: ${e.message}", e)

            // Fall back to basic directions
            return getFormattedDirections(userLocation, routePoints, destinationLocation)
        }
    }

    /**
     * Generate navigation from node index of a route
     */
    fun getNavigationFromNodeIndex(
        nodeIndex: Int,
        routePoints: List<GeoPoint>,
        distance: Double
    ): String {
        try {
            if (routePoints.isEmpty() || nodeIndex < 0 || nodeIndex >= routePoints.size - 1) {
                return "Folgen Sie der Route"
            }

            // Format distance
            val formattedDistance = when {
                distance < 30 -> "Jetzt"
                distance < 100 -> "In Kürze"
                distance < 1000 -> "In ${(distance / 10).toInt() * 10} Metern"
                else -> "In ${String.format("%.1f", distance / 1000)} km"
            }

            // Calculate bearing for current segment
            val currentBearing = calculateBearing(
                routePoints[nodeIndex].latitude, routePoints[nodeIndex].longitude,
                routePoints[nodeIndex + 1].latitude, routePoints[nodeIndex + 1].longitude
            )

            // If there's another segment ahead, check for turns
            if (nodeIndex + 2 < routePoints.size) {
                val nextBearing = calculateBearing(
                    routePoints[nodeIndex + 1].latitude, routePoints[nodeIndex + 1].longitude,
                    routePoints[nodeIndex + 2].latitude, routePoints[nodeIndex + 2].longitude
                )

                // Calculate turn angle
                var angleDiff = nextBearing - currentBearing
                if (angleDiff < -180) angleDiff += 360
                if (angleDiff > 180) angleDiff -= 360

                // Determine turn direction
                val direction = when {
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

                return "$formattedDistance: $direction"
            }

            // If this is the last segment, we're approaching the destination
            return "Sie nähern sich Ihrem Ziel"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating navigation: ${e.message}", e)
            return "Folgen Sie der Route"
        }
    }
}