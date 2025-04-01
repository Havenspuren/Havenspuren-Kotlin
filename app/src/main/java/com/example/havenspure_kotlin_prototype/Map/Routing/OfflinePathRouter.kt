package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import android.util.Log
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.BoundingBox
import java.lang.reflect.Field
import kotlin.math.*
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteDebugger
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteValidator

/**
 * Router that uses offline OSM map data to generate paths that follow actual roads and trails
 */
class OfflinePathRouter(private val context: Context) {
    companion object {
        private const val TAG = "OfflinePathRouter"
        private const val MAX_PATH_SEARCH_DISTANCE = 1000.0 // Maximum distance in meters to search for paths
    }

    // Cache of paths extracted from map data
    private val pathCache = mutableMapOf<String, List<List<GeoPoint>>>()

    // Graph representation of the path network
    private val pathGraph = mutableMapOf<GeoPoint, MutableSet<GeoPoint>>()

    // Tolerance for considering points as the same node (in degrees)
    private val nodeTolerance = 0.0001 // Approximately 10 meters

    // Cache for nearest path nodes
    private val nearestNodeCache = mutableMapOf<String, GeoPoint>()

    /**
     * Calculate direct distance between two points in meters
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return 6371000.0 * c // Earth radius in meters
    }

    /**
     * Extract paths from the current map view
     */
    fun extractPathsFromMapView(mapView: MapView): List<List<GeoPoint>> {
        val paths = mutableListOf<List<GeoPoint>>()

        try {
            // Extract polylines from visible overlays
            val polylines = mapView.overlays.filterIsInstance<Polyline>()

            polylines.forEach { polyline ->
                val points = polyline.points
                if (points.size >= 2) {
                    paths.add(points)
                    addPathToGraph(points)
                }
            }

            // If no paths were found (no polylines), try to extract from map data
            if (paths.isEmpty()) {
                Log.d(TAG, "No polylines found, creating path grid")
                val bbox = mapView.boundingBox

                // We need to use a placeholder for start/end when no actual routes are identified yet
                val center = bbox.centerWithDateLine
                val startPoint = GeoPoint(center.latitude - 0.01, center.longitude - 0.01)
                val endPoint = GeoPoint(center.latitude + 0.01, center.longitude + 0.01)

                val extractedPaths = createPathGridForBoundingBox(bbox, startPoint, endPoint)
                paths.addAll(extractedPaths)

                extractedPaths.forEach { path ->
                    addPathToGraph(path)
                }
            }

            Log.d(TAG, "Extracted ${paths.size} paths from map view")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting paths from map view: ${e.message}", e)
        }

        return paths
    }

    /**
     * Create a grid of paths covering a bounding box
     * This is used as a fallback when no actual path data is available
     */
    private fun createPathGridForBoundingBox(bbox: BoundingBox, startPoint: GeoPoint, endPoint: GeoPoint): List<List<GeoPoint>> {
        val paths = mutableListOf<List<GeoPoint>>()

        // Log that we're creating a path grid
        Log.d(TAG, "Creating path grid for bounding box as fallback")
        RouteDebugger.logFallbackUsed("OfflinePathGrid", "No actual path data available")

        // Calculate direct distance
        val directDistance = calculateDistance(startPoint, endPoint)

        // For short distances, just use a direct route
        if (directDistance < 300) {
            Log.d(TAG, "Short distance route, using direct path")
            paths.add(listOf(startPoint, endPoint))
            return paths
        }

        // For longer distances, create a more sophisticated path

        // Create a simple L-shaped route that's safer than a direct line
        // The L shape follows along latitude first, then longitude
        // This avoids crossing water in many coastal scenarios
        val midPoint = GeoPoint(startPoint.latitude, endPoint.longitude)
        paths.add(listOf(startPoint, midPoint, endPoint))

        // Calculate dimensions
        val width = bbox.lonEast - bbox.lonWest
        val height = bbox.latNorth - bbox.latSouth

        // Only create a grid for significant distances
        if (directDistance > 1000) {
            // Create a much simpler grid (2x2 instead of 4x4)
            // to reduce chances of creating paths that cross water
            val cellsX = 2
            val cellsY = 2

            // Create horizontal paths
            for (y in 0..cellsY) {
                val lat = bbox.latSouth + (height * y / cellsY)
                val horizontalPath = mutableListOf<GeoPoint>()

                for (x in 0..cellsX) {
                    val lon = bbox.lonWest + (width * x / cellsX)
                    horizontalPath.add(GeoPoint(lat, lon))
                }

                paths.add(horizontalPath)
            }

            // Create vertical paths
            for (x in 0..cellsX) {
                val lon = bbox.lonWest + (width * x / cellsX)
                val verticalPath = mutableListOf<GeoPoint>()

                for (y in 0..cellsY) {
                    val lat = bbox.latSouth + (height * y / cellsY)
                    verticalPath.add(GeoPoint(lat, lon))
                }

                paths.add(verticalPath)
            }
        }

        return paths
    }

    /**
     * Add a path to the routing graph
     */
    private fun addPathToGraph(path: List<GeoPoint>) {
        if (path.size < 2) return

        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]

            // Add bidirectional connections
            pathGraph.getOrPut(current) { mutableSetOf() }.add(next)
            pathGraph.getOrPut(next) { mutableSetOf() }.add(current)
        }
    }

    /**
     * Find the nearest node in the graph to a given point
     */
    private fun findNearestNode(point: GeoPoint): GeoPoint? {
        // Check cache first
        val cacheKey = "${point.latitude},${point.longitude}"
        nearestNodeCache[cacheKey]?.let { return it }

        var nearestNode: GeoPoint? = null
        var minDistance = Double.MAX_VALUE

        for (node in pathGraph.keys) {
            val distance = calculateDistance(point, node)
            if (distance < minDistance && distance < MAX_PATH_SEARCH_DISTANCE) {
                minDistance = distance
                nearestNode = node
            }
        }

        // Cache the result if found
        if (nearestNode != null) {
            nearestNodeCache[cacheKey] = nearestNode
        }

        return nearestNode
    }

    /**
     * A* search algorithm to find the best path between two nodes
     */
    private fun findPathAStar(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        // Find the nearest nodes in our graph to the start and end points
        val startNode = findNearestNode(start) ?: return createSafeFallbackPath(start, end)
        val endNode = findNearestNode(end) ?: return createSafeFallbackPath(start, end)

        // If start and end are very close, just return direct path
        if (calculateDistance(startNode, endNode) < 50) {
            return listOf(start, startNode, endNode, end)
        }

        // A* search algorithm
        val openSet = mutableSetOf(startNode)
        val cameFrom = mutableMapOf<GeoPoint, GeoPoint>()

        val gScore = mutableMapOf<GeoPoint, Double>().withDefault { Double.MAX_VALUE }
        gScore[startNode] = 0.0

        val fScore = mutableMapOf<GeoPoint, Double>().withDefault { Double.MAX_VALUE }
        fScore[startNode] = calculateDistance(startNode, endNode)

        var iterationCount = 0
        val maxIterations = 1000 // Prevent infinite loops

        while (openSet.isNotEmpty() && iterationCount < maxIterations) {
            iterationCount++

            // Find node with lowest fScore
            val current = openSet.minByOrNull { fScore.getValue(it) } ?: break

            // If we reached the end, reconstruct and return the path
            if (current == endNode) {
                val path = mutableListOf<GeoPoint>()
                var currentNode = current

                path.add(end) // Add actual end point
                path.add(currentNode)

                while (cameFrom.containsKey(currentNode)) {
                    currentNode = cameFrom[currentNode]!!
                    path.add(currentNode)
                }

                path.add(start) // Add actual start point
                val finalPath = path.reversed()

                // Validate the path
                val validationResult = RouteValidator.validateRoute(finalPath, start, end)
                if (!validationResult.isValid) {
                    Log.w(TAG, "A* path validation failed: ${validationResult.reason}")
                    RouteDebugger.logRouteValidationFailed(validationResult.reason, finalPath)
                    // Fall back to a safer path
                    return createSafeFallbackPath(start, end)
                }

                return finalPath
            }

            openSet.remove(current)

            // Check each neighbor
            pathGraph[current]?.forEach { neighbor ->
                val tentativeGScore = gScore.getValue(current) + calculateDistance(current, neighbor)

                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + calculateDistance(neighbor, endNode)

                    if (neighbor !in openSet) {
                        openSet.add(neighbor)
                    }
                }
            }
        }

        // If we couldn't find a path or hit max iterations
        if (iterationCount >= maxIterations) {
            Log.w(TAG, "A* search exceeded maximum iterations")
        } else {
            Log.d(TAG, "No path found in graph")
        }

        return createSafeFallbackPath(start, end)
    }

    /**
     * Create a safe fallback path when no route is found
     */
    private fun createSafeFallbackPath(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        Log.d(TAG, "Creating safe fallback path")

        // Calculate the direct distance
        val directDistance = calculateDistance(start, end)

        // For very short distances, just go direct
        if (directDistance < 100) {
            return listOf(start, end)
        }

        // For longer distances, use an L-shaped path that's less likely to cross water
        // The L shape follows latitude first, then longitude, which is often safer
        val midPoint = GeoPoint(start.latitude, end.longitude)

        RouteDebugger.logFallbackUsed("SafeFallbackL", "L-shaped route for offline fallback")
        return listOf(start, midPoint, end)
    }

    /**
     * Get a route between two points using offline path data
     */
    fun getRoute(startPoint: GeoPoint, endPoint: GeoPoint, mapView: MapView): Road {
        Log.d(TAG, "Getting offline route from ${startPoint.latitude},${startPoint.longitude} to ${endPoint.latitude},${endPoint.longitude}")

        // Extract paths if our graph is empty
        if (pathGraph.isEmpty()) {
            extractPathsFromMapView(mapView)

            // If still empty, create a path grid that covers the area between the points
            if (pathGraph.isEmpty()) {
                val minLat = min(startPoint.latitude, endPoint.latitude) - 0.01
                val maxLat = max(startPoint.latitude, endPoint.latitude) + 0.01
                val minLon = min(startPoint.longitude, endPoint.longitude) - 0.01
                val maxLon = max(startPoint.longitude, endPoint.longitude) + 0.01

                val bbox = BoundingBox(maxLat, maxLon, minLat, minLon)
                // Pass the start and end points to createPathGridForBoundingBox
                val paths = createPathGridForBoundingBox(bbox, startPoint, endPoint)

                paths.forEach { addPathToGraph(it) }
            }
        }

        // Find the best path using A*
        val routePoints = if (pathGraph.isNotEmpty()) {
            findPathAStar(startPoint, endPoint)
        } else {
            // If we still have no path data, fall back to L-shaped route
            createSafeFallbackPath(startPoint, endPoint)
        }

        // Create a Road object with these points
        return createRoadFromPoints(routePoints)
    }

    /**
     * Create a Road object from a list of points
     */
    private fun createRoadFromPoints(points: List<GeoPoint>): Road {
        val road = Road()

        try {
            // Set road status to OK
            val statusField = Road::class.java.getDeclaredField("mStatus")
            statusField.isAccessible = true
            statusField.set(road, Road.STATUS_OK)

            // Set route points
            val routeHighField = Road::class.java.getDeclaredField("mRouteHigh")
            routeHighField.isAccessible = true
            routeHighField.set(road, ArrayList(points))

            // Calculate and set road length
            val lengthField = Road::class.java.getDeclaredField("mLength")
            lengthField.isAccessible = true
            var totalDistance = 0.0
            for (i in 0 until points.size - 1) {
                totalDistance += calculateDistance(points[i], points[i + 1])
            }
            lengthField.set(road, totalDistance)

            Log.d(TAG, "Created road with ${points.size} points and length ${totalDistance}m")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating road from points: ${e.message}", e)
        }

        return road
    }

    /**
     * Initialize the router with existing data from MapView
     */
    fun initialize(mapView: MapView) {
        Log.d(TAG, "Initializing OfflinePathRouter")
        extractPathsFromMapView(mapView)
    }

    /**
     * Get route points from a Road object
     */
    fun getRoutePoints(road: Road): List<GeoPoint> {
        return try {
            val routeHighField = road.javaClass.getDeclaredField("mRouteHigh")
            routeHighField.isAccessible = true
            val routePoints = routeHighField.get(road) as? List<GeoPoint>

            routePoints?.toList() ?: listOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting route points: ${e.message}")
            listOf()
        }
    }

    /**
     * Get basic navigation instructions for the route
     */
    fun getInstructions(road: Road): List<String> {
        val points = getRoutePoints(road)

        // If not enough points for meaningful directions
        if (points.size < 3) {
            return listOf("Folgen Sie der Route", "Erreichen Sie das Ziel")
        }

        val instructions = mutableListOf<String>()
        instructions.add("Starten Sie Ihre Route")

        // Generate turn instructions at significant angles
        for (i in 1 until points.size - 1) {
            val prev = points[i-1]
            val current = points[i]
            val next = points[i+1]

            val bearing1 = calculateBearing(prev, current)
            val bearing2 = calculateBearing(current, next)

            // Calculate angle difference
            var angle = bearing2 - bearing1
            if (angle < -180) angle += 360
            if (angle > 180) angle -= 360

            // Only add instruction if there's a significant turn
            if (abs(angle) > 30) {
                val direction = when {
                    angle > 45 -> "Biegen Sie rechts ab"
                    angle > 30 -> "Halten Sie sich rechts"
                    angle < -45 -> "Biegen Sie links ab"
                    angle < -30 -> "Halten Sie sich links"
                    else -> "Setzen Sie Ihren Weg fort"
                }
                instructions.add(direction)
            }
        }

        instructions.add("Erreichen Sie das Ziel")
        return instructions
    }

    /**
     * Calculate bearing between two points
     */
    private fun calculateBearing(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) {
            bearing += 360
        }

        return bearing
    }
}