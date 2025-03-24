package com.example.havenspure_kotlin_prototype.Map.routing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.Map.Routing.DirectionMapHelpers
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Helper methods specifically for offline navigation using GraphHopper
 */
object OfflineDirectionMapHelpers {

    private const val TAG = "OfflineMapHelpers"

    /**
     * Draw a GraphHopper route on the map with direction indicators
     */
    fun drawGraphHopperRouteWithDirections(
        mapView: MapView,
        routePoints: List<GeoPoint>,
        routeColor: Int,
        instructionPoints: List<Pair<GeoPoint, String>>? = null,
        context: Context
    ) {
        // Remove any existing GraphHopper route lines (but keep other overlays)
        val routeOverlays = mapView.overlays.filter {
            it is Polyline && it.id == "gh_route"
        }
        mapView.overlays.removeAll(routeOverlays)

        // Remove any existing direction markers
        val directionMarkers = mapView.overlays.filter {
            it is Marker && it.id == "gh_direction"
        }
        mapView.overlays.removeAll(directionMarkers)

        // Create and add the new route line
        val routeLine = Polyline(mapView).apply {
            setPoints(routePoints)
            outlinePaint.color = routeColor
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            id = "gh_route"
        }
        mapView.overlays.add(routeLine)

        // Add direction indicators if we have instruction points
        if (instructionPoints != null && instructionPoints.isNotEmpty()) {
            for ((point, instruction) in instructionPoints) {
                val marker = Marker(mapView).apply {
                    position = point
                    title = instruction
                    icon = createDirectionArrow(context, Color.WHITE, routeColor)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    id = "gh_direction"
                    setInfoWindow(null) // Hide the info window initially
                }
                mapView.overlays.add(marker)
            }
        } else {
            // If no instruction points provided, add direction arrows along the route
            addDirectionArrowsToRoute(mapView, routePoints, routeColor, context)
        }

        // Force refresh
        mapView.invalidate()
    }

    /**
     * Create a simple direction arrow icon
     */
    private fun createDirectionArrow(
        context: Context,
        backgroundColor: Int,
        arrowColor: Int
    ): BitmapDrawable {
        val size = 30
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background circle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = backgroundColor
        bgPaint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        // Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.color = arrowColor
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 2f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, borderPaint)

        // Arrow
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        arrowPaint.color = arrowColor
        arrowPaint.style = Paint.Style.FILL

        val path = Path()
        path.moveTo(size / 2f, size * 0.2f)       // Top point
        path.lineTo(size * 0.7f, size * 0.65f)    // Bottom right
        path.lineTo(size * 0.5f, size * 0.65f)    // Bottom middle right
        path.lineTo(size * 0.5f, size * 0.8f)     // Bottom middle
        path.lineTo(size * 0.5f, size * 0.65f)    // Bottom middle left
        path.lineTo(size * 0.3f, size * 0.65f)    // Bottom left
        path.close()

        canvas.drawPath(path, arrowPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Add direction arrows along a route
     */
    private fun addDirectionArrowsToRoute(
        mapView: MapView,
        routePoints: List<GeoPoint>,
        arrowColor: Int,
        context: Context
    ) {
        if (routePoints.size < 2) return

        // Calculate how many arrows to add based on route length
        val numArrows = min(5, routePoints.size / 3)
        if (numArrows <= 0) return

        // Add arrows at regular intervals
        val interval = routePoints.size / (numArrows + 1)

        for (i in 1..numArrows) {
            val index = i * interval
            if (index >= routePoints.size - 1) continue

            val point = routePoints[index]
            val nextPoint = routePoints[index + 1]

            // Calculate bearing for arrow direction
            val bearing = calculateBearing(
                point.latitude, point.longitude,
                nextPoint.latitude, nextPoint.longitude
            )

            // Create the arrow marker
            val marker = Marker(mapView).apply {
                position = point
                icon = createDirectionArrow(context, Color.WHITE, arrowColor)
                rotation = bearing.toFloat()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                id = "gh_direction"
                setInfoWindow(null) // No info window
            }

            mapView.overlays.add(marker)
        }
    }

    /**
     * Generate instruction points along a route using GraphHopper directions
     */
    fun getInstructionPointsForRoute(
        routePoints: List<GeoPoint>,
        instructions: List<String>,
        routeLength: Double
    ): List<Pair<GeoPoint, String>> {
        val result = mutableListOf<Pair<GeoPoint, String>>()

        if (routePoints.size < 2 || instructions.isEmpty()) {
            return result
        }

        // Simplify by placing instructions at regular intervals
        val maxInstructions = min(instructions.size, 5)  // Limit to 5 instruction points
        val interval = routePoints.size / (maxInstructions + 1)

        for (i in 1..maxInstructions) {
            val index = i * interval
            if (index < routePoints.size && i - 1 < instructions.size) {
                result.add(Pair(routePoints[index], instructions[i - 1]))
            }
        }

        return result
    }

    /**
     * Calculate bearing between two points (0-360 degrees).
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
     * Get a complete GraphHopper route with visualization for offline navigation
     */
    fun getGraphHopperRouteWithVisuals(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routeColor: Int,
        userMarkerColor: Int,
        destinationColor: Int,
        context: Context
    ): List<GeoPoint> {
        try {
            // Get the routing service
            val routingService = OfflineRoutingService.getInstance(context)

            // Calculate the route using GraphHopper
            val routePoints = routingService.calculateRoute(
                startPoint.latitude,
                startPoint.longitude,
                endPoint.latitude,
                endPoint.longitude,
                "foot"
            )

            if (routePoints.isNotEmpty()) {
                Log.d(TAG, "Successfully got GraphHopper route with ${routePoints.size} points")

                // Get turn-by-turn instructions
                val instructions = routingService.getInstructions(
                    startPoint.latitude,
                    startPoint.longitude,
                    endPoint.latitude,
                    endPoint.longitude,
                    "foot"
                )

                Log.d(TAG, "Got ${instructions.size} instructions for the route")

                // Generate instruction points
                val instructionPoints = if (instructions.isNotEmpty()) {
                    getInstructionPointsForRoute(routePoints, instructions, 0.0)
                } else {
                    null
                }

                // Draw the route with directions
                drawGraphHopperRouteWithDirections(
                    mapView,
                    routePoints,
                    routeColor,
                    instructionPoints,
                    context
                )

                // Update start and end markers using existing DirectionMapHelpers
                val origHelpers = DirectionMapHelpers

                // Add user location marker
                origHelpers.updateUserMarkerPosition(mapView, startPoint, userMarkerColor, context)

                // Add destination marker (you need to implement this if not already available)
                addDestinationMarker(mapView, endPoint, destinationColor, context)

                return routePoints
            } else {
                Log.e(TAG, "GraphHopper returned empty route, falling back to DirectionMapHelpers")

                // Fall back to original helper
                return DirectionMapHelpers.forceStreetBasedRoute(
                    mapView, startPoint, endPoint,
                    routeColor, userMarkerColor, destinationColor,
                    context
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in GraphHopper routing: ${e.message}", e)

            // Fall back to original helper on error
            return DirectionMapHelpers.forceStreetBasedRoute(
                mapView, startPoint, endPoint,
                routeColor, userMarkerColor, destinationColor,
                context
            )
        }
    }

    /**
     * Add destination marker to the map
     */
    private fun addDestinationMarker(
        mapView: MapView,
        position: GeoPoint,
        color: Int,
        context: Context
    ) {
        // Remove existing destination markers
        val existingMarkers = mapView.overlays.filter {
            it is Marker && it.id == "gh_destination"
        }
        mapView.overlays.removeAll(existingMarkers)

        // Create new destination marker
        val marker = Marker(mapView).apply {
            this.position = position
            title = "Ziel"
            // Use the original helper's method if available, or create a simple marker
            icon = DirectionMapHelpers.createCustomPin(context, color)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            id = "gh_destination"
        }

        mapView.overlays.add(marker)
    }

    /**
     * Process GraphHopper instructions to get the current direction to show
     */
    fun getGraphHopperNavigationInstruction(
        userLocation: LocationData,
        routePoints: List<GeoPoint>,
        instructions: List<String>,
        context: Context
    ): String {
        if (instructions.isEmpty()) {
            return "Folgen Sie der Route"
        }

        // Find closest point on route to current location
        var closestPointIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in routePoints.indices) {
            val point = routePoints[i]
            val distance = DirectionMapHelpers.calculateDistance(
                userLocation.latitude, userLocation.longitude,
                point.latitude, point.longitude
            )

            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = i
            }
        }

        // If we're off route by more than 50 meters
        if (minDistance > 50) {
            return "Kehren Sie zur Route zurück"
        }

        // Calculate which instruction segment we're in
        val segmentIndex = if (routePoints.isEmpty() || instructions.isEmpty()) {
            0
        } else {
            // Simple approach: divide route into segments matching instructions
            val segmentSize = routePoints.size / instructions.size
            closestPointIndex / segmentSize
        }

        // Return the current or next instruction
        return if (segmentIndex < instructions.size) {
            instructions[segmentIndex]
        } else if (instructions.isNotEmpty()) {
            instructions.last()
        } else {
            "Folgen Sie der Route"
        }
    }
}