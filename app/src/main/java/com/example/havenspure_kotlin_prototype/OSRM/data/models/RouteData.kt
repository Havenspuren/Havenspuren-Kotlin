package com.example.havenspure_kotlin_prototype.OSRM.data.models

import org.osmdroid.util.GeoPoint

/**
 * Data class representing a route with its properties.
 *
 * @property points List of GeoPoints that make up the route
 * @property distance Total distance of the route in meters
 * @property duration Estimated duration in seconds
 * @property instructions Navigation instructions for this route
 * @property status The status of the route (success, error, etc.)
 */
data class RouteData(
    val points: List<GeoPoint>,
    val distance: Double,
    val duration: Double,
    val instructions: List<NavigationInstruction>,
    val status: RouteStatus
) {
    companion object {
        // Create a simple direct route between two points
        fun createDirectRoute(
            start: GeoPoint,
            end: GeoPoint
        ): RouteData {
            val points = listOf(start, end)
            val distance = start.distanceToAsDouble(end)

            return RouteData(
                points = points,
                distance = distance,
                duration = distance / 1.4, // Assuming 1.4 m/s walking speed
                instructions = listOf(
                    NavigationInstruction(
                        text = "Folgen Sie der Route",
                        distance = distance,
                        duration = distance / 1.4,
                        type = NavigationInstructionType.GO_STRAIGHT
                    )
                ),
                status = RouteStatus.SUCCESS
            )
        }
    }
}

/**
 * Enum representing the status of a route
 */
enum class RouteStatus {
    SUCCESS,
    NETWORK_ERROR,
    SERVER_ERROR,
    NO_ROUTE_FOUND,
    INVALID_POINTS
}