package com.example.havenspure_kotlin_prototype.Map.Routing

import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteDebugger
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * RouteValidator provides advanced validation for routes to ensure they follow actual roads
 * and don't cross invalid areas like water bodies.
 */
object RouteValidator {
    private const val TAG = "RouteValidator"

    // Thresholds for validation
    private const val MAX_SEGMENT_LENGTH = 300.0 // meters, segments longer than this are suspicious
    private const val MAX_SUSPICIOUS_SEGMENTS = 2 // maximum number of suspicious segments allowed
    private const val MAX_ENDPOINT_DISTANCE = 500.0 // meters, how far route can start/end from requested points
    private const val MIN_ROUTE_POINTS = 3 // minimum number of points for a valid route

    // Threshold for angle-based validation
    private const val MAX_ANGLE_DEVIATION = 60.0 // degrees, maximum angle change to consider natural

    /**
     * Primary validation method to check if a route is valid
     *
     * @param routePoints The list of points in the route
     * @param startPoint The requested start point
     * @param endPoint The requested end point
     * @param context Optional context for additional validation methods
     * @return A ValidationResult with the validation status and reason
     */
    fun validateRoute(
        routePoints: List<GeoPoint>,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        context: Context? = null
    ): ValidationResult {
        // Check if we have enough points
        if (routePoints.size < MIN_ROUTE_POINTS) {
            return ValidationResult(
                false,
                "Route has too few points (${routePoints.size})"
            )
        }

        // Validate route endpoints
        val startEndValidation = validateEndpoints(routePoints, startPoint, endPoint)
        if (!startEndValidation.isValid) {
            return startEndValidation
        }

        // Validate segment lengths (check for water-crossing segments)
        val segmentValidation = validateSegmentLengths(routePoints)
        if (!segmentValidation.isValid) {
            return segmentValidation
        }

        // Validate route smoothness/angles
        val angleValidation = validateRouteAngles(routePoints)
        if (!angleValidation.isValid) {
            return angleValidation
        }

        // Validate route density (points should be more dense in urban areas)
        val densityValidation = validateRouteDensity(routePoints)
        if (!densityValidation.isValid) {
            return densityValidation
        }

        // All validations passed
        return ValidationResult(true, "Route passed all validation checks")
    }

    /**
     * Validate that route endpoints are close to the requested points
     */
    private fun validateEndpoints(
        routePoints: List<GeoPoint>,
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ): ValidationResult {
        // Get first and last points of the route
        val firstRoutePoint = routePoints.first()
        val lastRoutePoint = routePoints.last()

        // Calculate distances to requested start/end
        val distanceToStart = calculateDistance(
            firstRoutePoint.latitude, firstRoutePoint.longitude,
            startPoint.latitude, startPoint.longitude
        )

        val distanceToEnd = calculateDistance(
            lastRoutePoint.latitude, lastRoutePoint.longitude,
            endPoint.latitude, endPoint.longitude
        )

        // Check if the route starts and ends close to the requested points
        if (distanceToStart > MAX_ENDPOINT_DISTANCE) {
            return ValidationResult(
                false,
                "Route start point is too far from requested start (${distanceToStart.toInt()}m)"
            )
        }

        if (distanceToEnd > MAX_ENDPOINT_DISTANCE) {
            return ValidationResult(
                false,
                "Route end point is too far from requested end (${distanceToEnd.toInt()}m)"
            )
        }

        return ValidationResult(true, "Endpoints validated")
    }

    /**
     * Validate segment lengths to detect possible water crossings
     */
    private fun validateSegmentLengths(routePoints: List<GeoPoint>): ValidationResult {
        var suspiciousSegments = 0
        var maxSegmentLength = 0.0
        var suspiciousSegmentIndices = mutableListOf<Int>()

        // Check each segment for suspicious lengths
        for (i in 0 until routePoints.size - 1) {
            val point1 = routePoints[i]
            val point2 = routePoints[i + 1]

            val segmentLength = calculateDistance(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude
            )

            maxSegmentLength = max(maxSegmentLength, segmentLength)

            // Flag suspiciously long segments
            if (segmentLength > MAX_SEGMENT_LENGTH) {
                suspiciousSegments++
                suspiciousSegmentIndices.add(i)

                Log.w(TAG, "Suspicious segment #$suspiciousSegments at index $i: ${segmentLength.toInt()}m")
            }
        }

        // If too many suspicious segments, the route is invalid
        if (suspiciousSegments > MAX_SUSPICIOUS_SEGMENTS) {
            return ValidationResult(
                false,
                "Too many suspicious segments in route: $suspiciousSegments, max allowed: $MAX_SUSPICIOUS_SEGMENTS. " +
                        "Segments at indices: ${suspiciousSegmentIndices.joinToString()}"
            )
        }

        return ValidationResult(true, "Segment lengths validated (max: ${maxSegmentLength.toInt()}m)")
    }

    /**
     * Validate route angles to detect unnatural turns
     */
    private fun validateRouteAngles(routePoints: List<GeoPoint>): ValidationResult {
        if (routePoints.size < 3) {
            return ValidationResult(true, "Too few points to validate angles")
        }

        var suspiciousAngles = 0
        var suspiciousAngleIndices = mutableListOf<Int>()

        // Check angles between consecutive segments
        for (i in 1 until routePoints.size - 1) {
            val prevPoint = routePoints[i - 1]
            val currentPoint = routePoints[i]
            val nextPoint = routePoints[i + 1]

            val angle1 = calculateBearing(prevPoint, currentPoint)
            val angle2 = calculateBearing(currentPoint, nextPoint)

            // Calculate absolute angle difference
            var angleDiff = abs(angle2 - angle1)
            if (angleDiff > 180) {
                angleDiff = 360 - angleDiff
            }

            // Very sharp angles are suspicious in road networks
            if (angleDiff > MAX_ANGLE_DEVIATION) {
                suspiciousAngles++
                suspiciousAngleIndices.add(i)

                Log.w(TAG, "Suspicious angle at index $i: ${angleDiff.toInt()} degrees")
            }
        }

        // More than 3 suspicious angles might indicate a problem
        if (suspiciousAngles > 3) {
            return ValidationResult(
                false,
                "Too many suspicious angles in route: $suspiciousAngles. " +
                        "Angles at indices: ${suspiciousAngleIndices.joinToString()}"
            )
        }

        return ValidationResult(true, "Route angles validated")
    }

    /**
     * Validate route point density
     * Roads should have more points in complex areas and fewer in straight sections
     */
    private fun validateRouteDensity(routePoints: List<GeoPoint>): ValidationResult {
        if (routePoints.size < 10) {
            return ValidationResult(true, "Too few points to validate density")
        }

        // Calculate standard deviation of segment lengths
        val segmentLengths = mutableListOf<Double>()
        for (i in 0 until routePoints.size - 1) {
            val segmentLength = calculateDistance(
                routePoints[i].latitude, routePoints[i].longitude,
                routePoints[i + 1].latitude, routePoints[i + 1].longitude
            )
            segmentLengths.add(segmentLength)
        }

        val meanLength = segmentLengths.average()

        // Calculate variance
        val variance = segmentLengths.map { (it - meanLength) * (it - meanLength) }.average()

        // Calculate standard deviation
        val stdDev = Math.sqrt(variance)

        // Calculate coefficient of variation (standardized measure of dispersion)
        val cv = stdDev / meanLength

        // If coefficient of variation is very low, points might be artificially generated
        // (real roads have variable segment lengths)
        if (cv < 0.1) {
            return ValidationResult(
                false,
                "Route has suspiciously uniform segment lengths (CV=$cv). " +
                        "This suggests artificially generated points rather than actual road data."
            )
        }

        return ValidationResult(true, "Route density validation passed")
    }

    /**
     * Calculate distance between two points in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Calculate bearing between two points
     */
    private fun calculateBearing(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLon = lon2 - lon1

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        var bearing = Math.toDegrees(Math.atan2(y, x))
        if (bearing < 0) {
            bearing += 360
        }

        return bearing
    }

    /**
     * Data class to hold validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String
    )
}