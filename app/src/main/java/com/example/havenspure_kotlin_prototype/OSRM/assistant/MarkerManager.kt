package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Overlay
import kotlin.math.max
import kotlin.math.min

/**
 * Manages markers and overlays on the map
 */
class MarkerManager(private val context: Context) {
    private val TAG = "MarkerManager"

    // Keep track of current markers and overlays for easier updates
    private var userMarker: DirectionMarker? = null
    private var destinationMarker: Marker? = null
    private var routeOverlay: Polyline? = null
    private var userBearing: Float = 0f
    private var currentZoomLevel: Double = 17.0
    private var lastUpdateTime = 0L

    private val directionIconCache = HashMap<String, BitmapDrawable>() // Key: "bearing_zoom_color"
    private var lastUserPosition = GeoPoint(0.0, 0.0)
    /**
     * Custom marker class that maintains its own bearing regardless of map rotation
     */
    /**
     * Custom marker class that maintains its own bearing regardless of map rotation
     */
    inner class DirectionMarker(mapView: MapView) : Overlay() {
        var position: GeoPoint = GeoPoint(0.0, 0.0)
        var color: Int = Color.BLUE
        var bearing: Float = 0f
        private val mapView: MapView = mapView
        private var lastUsedBearing: Float = -1f
        private var lastUsedZoom: Double = -1.0
        private var lastUsedColor: Int = 0
        private var cachedBitmap: Bitmap? = null

        private var lastBitmap: Bitmap? = null

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow || position.latitude == 0.0) return

            // Use getOrCreateBitmap instead of the current bitmap creation logic
            val bitmap = getOrCreateBitmap()

            val screenPos = mapView.projection.toPixels(position, null)

            // Save canvas state for drawing
            canvas.save()

            // We no longer counteract map rotation here
            // Removed: canvas.rotate(-mapView.mapOrientation, screenPos.x.toFloat(), screenPos.y.toFloat())

            canvas.drawBitmap(
                bitmap,
                screenPos.x - bitmap.width / 2f,
                screenPos.y - bitmap.height / 2f,
                null
            )

            canvas.restore()
        }

        // New method to handle caching
        private fun getOrCreateBitmap(): Bitmap {
            // Check if we need a new bitmap
            val zoomChanged = Math.abs(lastUsedZoom - mapView.zoomLevelDouble) > 0.5
            val bearingChanged = Math.abs(lastUsedBearing - bearing) > 5.0
            val colorChanged = lastUsedColor != color

            if (cachedBitmap != null && !zoomChanged && !bearingChanged && !colorChanged) {
                // Re-use existing bitmap
                return cachedBitmap!!
            }

            // Round bearing to nearest 15 degrees to reduce cache size
            val roundedBearing = (Math.round(bearing / 15f) * 15)

            // Create cache key
            val cacheKey = "${roundedBearing}_${Math.round(mapView.zoomLevelDouble)}_$color"

            // Check if we have this icon in the global cache
            if (directionIconCache.containsKey(cacheKey)) {
                val drawable = directionIconCache[cacheKey]!!
                cachedBitmap = drawable.bitmap
            } else {
                // Create new icon
                val drawable = createUserLocationIcon(color, bearing, mapView.zoomLevelDouble)
                directionIconCache[cacheKey] = drawable as BitmapDrawable
                cachedBitmap = drawable.bitmap

                // Limit cache size to prevent memory issues
                if (directionIconCache.size > 48) { // Allow more cache entries for different zoom levels
                    val iterator = directionIconCache.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }

            // Update last used values
            lastUsedBearing = bearing
            lastUsedZoom = mapView.zoomLevelDouble
            lastUsedColor = color

            return cachedBitmap!!
        }

        /**
         * Create a custom icon for user location with direction indicator and accuracy circle
         * Note: The bearing value received here is already adjusted in updateNavigation
         */
        /**
         * Create a custom icon for user location with direction indicator and accuracy circle
         */
        private fun createUserLocationIcon(color: Int, bearing: Float, zoomLevel: Double): Drawable {
            val centerSize = 48f // Center dot size as float
            val glowRadius = calculateAccuracyRadius(zoomLevel) // Dynamic accuracy circle based on zoom

            // Ensure bitmap is large enough for the glow and convert to int for bitmap creation
            val totalSize = max(centerSize.toInt(), (glowRadius * 2).toInt())

            val bitmap = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val centerX = totalSize / 2f
            val centerY = totalSize / 2f

            // Create paints
            val circlePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                this.color = color
            }

            val glowPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                shader = RadialGradient(
                    centerX, centerY, glowRadius,
                    Color.argb(100, 66, 133, 244), // Semi-transparent Google Maps blue
                    Color.argb(0, 66, 133, 244),   // Transparent at the edges
                    Shader.TileMode.CLAMP
                )
            }

            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                this.color = Color.WHITE
                strokeWidth = 3f
            }

            // Draw accuracy glow circle
            canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

            // Draw outer circle (white border)
            // Use min of two float values for correct type
            val innerCircleRadius = min(centerSize / 2f - 4f, totalSize / 2f * 0.3f)
            canvas.drawCircle(centerX, centerY, innerCircleRadius, strokePaint)

            // Draw inner circle (filled)
            canvas.drawCircle(centerX, centerY, innerCircleRadius - 2f, circlePaint)

            // Draw direction indicator if bearing is provided
            if (bearing != 0f) {
                // Create path for direction arrow
                val directionPath = Path()

                // Save canvas state to restore after rotation
                canvas.save()

                // MODIFIED: Add 180 degrees to the bearing to rotate the arrow correctly
                canvas.rotate(bearing + 180, centerX, centerY)

                // Create arrow/triangle shape pointing outward from the circle
                // Position it at the edge of the inner circle
                val arrowBase = innerCircleRadius + 2f  // Start arrow just outside of inner circle

                // Arrow length
                val arrowLength = glowRadius * 0.15f

                // Doubled width as requested
                val arrowWidth = innerCircleRadius * 1.0f

                // Define the arrow pointing outward - keep original drawing unchanged
                directionPath.moveTo(centerX, centerY - arrowBase - arrowLength)  // Tip of arrow
                directionPath.lineTo(centerX - arrowWidth/2, centerY - arrowBase) // Bottom left
                directionPath.lineTo(centerX + arrowWidth/2, centerY - arrowBase) // Bottom right
                directionPath.close()

                // Draw arrow
                circlePaint.color = Color.WHITE
                canvas.drawPath(directionPath, circlePaint)

                // Draw border around arrow with blue color to match the dot
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.5f
                strokePaint.color = color  // Use the same blue color as the dot
                canvas.drawPath(directionPath, strokePaint)

                // Reset stroke paint color to white for other elements
                strokePaint.color = Color.WHITE

                // Restore canvas to original state
                canvas.restore()
            }

            return BitmapDrawable(context.resources, bitmap)
        }
    }

    /**
     * Update or create the user location marker
     *
     * @param mapView Map view to add the marker to
     * @param position User's position
     * @param color Marker color
     * @param bearing Optional bearing to show direction (0-360 degrees, 0 is north)
     */
    fun updateUserLocationMarker(
        mapView: MapView,
        position: GeoPoint,
        color: Int,
        bearing: Float? = null
    ) {
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime < 200) return

            // Update last position and time
            lastUserPosition = position
            lastUpdateTime = currentTime

            // Create or update marker
            if (userMarker == null) {
                userMarker = DirectionMarker(mapView).apply {
                    this.position = position
                    this.color = color
                    bearing?.let { this.bearing = it }
                    // Removed setAnchor() since we're using Overlay
                }
                mapView.overlays.add(userMarker)
            } else {
                userMarker?.apply {
                    this.position = position
                    this.color = color
                    bearing?.let { this.bearing = it }
                }
            }

            // Bring marker to front
            mapView.overlays.remove(userMarker)
            mapView.overlays.add(userMarker)

            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user marker: ${e.message}")
        }
    }

    /**
     * Add or update destination marker
     *
     * @param mapView Map view to add marker to
     * @param position Destination position
     * @param color Marker color
     * @param title Marker title/label
     */
    fun updateDestinationMarker(
        mapView: MapView,
        position: GeoPoint,
        color: Int,
        title: String = "Ziel"
    ) {
        try {
            // Remove existing destination marker if it exists to avoid duplicates
            if (destinationMarker != null) {
                mapView.overlays.remove(destinationMarker)
            }

            // Create fresh destination marker
            destinationMarker = Marker(mapView).apply {
                setPosition(position)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = createDestinationIcon(color)
                this.title = title
                infoWindow = null // No info window
            }

            // Ensure marker is added to overlays
            mapView.overlays.add(destinationMarker)

            // Log successful marker update
            Log.d(TAG, "Destination marker updated at position: ${position.latitude}, ${position.longitude}")

            // Ensure map is refreshed
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating destination marker: ${e.message}")
        }
    }
    /**
     * Draw a route path on the map
     *
     * @param mapView Map view to add route to
     * @param points List of route points
     * @param color Route color
     * @param width Route line width
     */
    fun drawRoute(
        mapView: MapView,
        points: List<GeoPoint>,
        color: Int,
        width: Float = 10f
    ) {
        try {
            // Remove existing route if any
            if (routeOverlay != null) {
                mapView.overlays.remove(routeOverlay)
            }

            // Create new route
            routeOverlay = Polyline().apply {
                outlinePaint.color = color
                outlinePaint.strokeWidth = width
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(points)
            }

            // Add to map
            mapView.overlays.add(routeOverlay)

            // Make sure route is below markers (draw order)
            moveRouteToBottom(mapView)

            // Refresh map
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing route: ${e.message}")
        }
    }

    /**
     * Move route to bottom of overlay stack so markers appear on top
     */
    private fun moveRouteToBottom(mapView: MapView) {
        routeOverlay?.let { route ->
            mapView.overlays.remove(route)
            mapView.overlays.add(0, route) // Add at index 0 (bottom)
        }
    }

    /**
     * Clear all markers and overlays from map
     */
    fun clearMap(mapView: MapView) {
        try {
            mapView.overlays.clear()
            userMarker = null
            destinationMarker = null
            routeOverlay = null
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing map: ${e.message}")
        }
    }

    /**
     * Calculate appropriate radius for accuracy circle based on zoom level
     * Larger when zoomed out, smaller when zoomed in, with hard limits
     */
    private fun calculateAccuracyRadius(zoomLevel: Double): Float {
        // Define absolute limits
        val absoluteMinRadius = 35f    // Never smaller than this (at highest zoom)
        val absoluteMaxRadius = 120f   // Never larger than this (at lowest zoom)

        // Zoom level boundaries
        val minZoom = 17.0  // Lowest supported zoom
        val maxZoom = 30.0  // Highest supported zoom

        // Clamp zoom level to supported range
        val clampedZoom = when {
            zoomLevel < minZoom -> minZoom
            zoomLevel > maxZoom -> maxZoom
            else -> zoomLevel
        }

        // Calculate zoom percentage (0 to 1) where 1 is max zoom
        val zoomPercentage = (clampedZoom - minZoom) / (maxZoom - minZoom)

        // Linear interpolation: Start large at min zoom, get smaller at max zoom
        // Inverted from previous version - now bigger when zoomed out
        return absoluteMaxRadius - ((absoluteMaxRadius - absoluteMinRadius) * zoomPercentage.toFloat())
    }

    /**
     * Create a custom icon for destination marker
     */
    private fun createDestinationIcon(color: Int): Drawable {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            this.color = color
        }

        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            this.color = Color.WHITE
            strokeWidth = 3f
        }

        // Draw map pin/marker shape
        val path = Path()
        path.moveTo(size / 2f, size.toFloat())       // Bottom point
        path.quadTo(0f, size * 0.7f, size / 2f, 0f)  // Left curve
        path.quadTo(size.toFloat(), size * 0.7f, size / 2f, size.toFloat()) // Right curve
        path.close()

        // Draw marker
        canvas.drawPath(path, paint)
        canvas.drawPath(path, strokePaint)

        // Draw inner circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 4f, size / 6f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Clear route-related overlays from the map
     */
    fun clearRouteOverlays(mapView: MapView) {
        try {
            // Find and remove route polylines
            val overlaysToRemove = mapView.overlays
                .filterIsInstance<org.osmdroid.views.overlay.Polyline>()
                .toList()

            mapView.overlays.removeAll(overlaysToRemove)

            // Force refresh
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing route overlays: ${e.message}")
        }
    }
}