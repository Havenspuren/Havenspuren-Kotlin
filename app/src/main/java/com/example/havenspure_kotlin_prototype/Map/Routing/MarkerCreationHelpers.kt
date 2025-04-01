package com.example.havenspure_kotlin_prototype.Map.Routing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AndroidColor
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.min

/**
 * Helper methods for marker creation and display on the map
 */
object MarkerCreationHelpers {

    // Constants for user marker identification
    private const val USER_MARKER_ID = "USER_MARKER"

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
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, outerGlowPaint)

        // Draw middle glow
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, middleGlowPaint)

        // Draw center dot
        canvas.drawCircle(size / 2f, size / 2f, size / 6f, centerPaint)

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
     * Update only the user location marker on the map
     */
    fun updateUserMarkerPosition(
        mapView: MapView,
        userPoint: GeoPoint,
        userLocationColor: Int,
        context: Context
    ) {
        // Find and remove any existing user markers
        val existingUserMarkers = mapView.overlays.filterIsInstance<Marker>()
            .filter { it.id == USER_MARKER_ID }

        mapView.overlays.removeAll(existingUserMarkers)

        // Add new user marker
        val userMarker = Marker(mapView).apply {
            position = userPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createGlowingLocationMarker(context, userLocationColor)
            id = USER_MARKER_ID // Use a consistent identifier
            title = null
            setInfoWindow(null)
        }
        mapView.overlays.add(userMarker)

        // Force refresh
        mapView.invalidate()
    }

    /**
     * Add optimized direction arrows (fewer arrows for better performance)
     */
    fun addOptimizedDirectionArrows(
        mapView: MapView,
        routePoints: List<GeoPoint>,
        arrowColor: Int,
        context: Context
    ) {
        // Need at least 3 points for a meaningful route with direction
        if (routePoints.size < 3) return

        // Calculate total distance
        val totalDistance = RoutingHelpers.calculateRouteDistance(routePoints)

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
                val segmentLength = RoutingHelpers.calculateDistance(
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
                        val bearing = RoutingHelpers.calculateBearing(
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
}