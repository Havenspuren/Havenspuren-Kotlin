package com.example.havenspure_kotlin_prototype.Graph

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.Log
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.Map.Routing.MarkerCreationHelpers
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteDebugger
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteValidator
import com.example.havenspure_kotlin_prototype.Map.Routing.RoutingHelpers
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import android.preference.PreferenceManager
import com.example.havenspure_kotlin_prototype.navigation.OSRMRouter
import com.example.havenspure_kotlin_prototype.navigation.OfflinePathRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/**
 * Centralized routing management singleton that coordinates all navigation services
 */
@SuppressLint("StaticFieldLeak")
object RoutingGraph {
    private const val TAG = "RoutingGraph"

    // The router implementation
    @SuppressLint("StaticFieldLeak")
    private lateinit var router: OSRMRouter

    // Offline router for path-aware routing
    @SuppressLint("StaticFieldLeak")
    private var offlineRouter: OfflinePathRouter? = null

    // Cache for performance
    private var cachedRoad: Road? = null
    private var cachedStartPoint: GeoPoint? = null
    private var cachedEndPoint: GeoPoint? = null
    private var cachedRoute: List<GeoPoint> = emptyList()

    // Default colors
    private var routeColor = Color.GREEN
    private var userMarkerColor = Color.BLUE
    private var destinationMarkerColor = Color.RED

    // Reference to the current MapView for offline routing
    private var currentMapView: MapView? = null

    // Reference to the context for RouteDebugger
    private var context: Context? = null

    // Coroutine scope for background processing
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Calculate direct distance between two points
     */
    private fun calculateDirectDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Format distance in human-readable form
     */
    @SuppressLint("DefaultLocale")
    fun formatDistance(distanceMeters: Double): String {
        return when {
            distanceMeters < 1000 -> "Entfernung: ${distanceMeters.toInt()} m"
            else -> "Entfernung: ${String.format("%.1f", distanceMeters / 1000)} km"
        }
    }

    /**
     * Calculate remaining distance to destination
     */
    fun getRemainingDistance(
        userLocation: LocationData,
        destinationLocation: LocationData
    ): String {
        try {
            if (cachedRoad == null || cachedRoute.isEmpty()) {
                // Direct straight-line distance
                val directDistance = calculateDirectDistance(
                    userLocation.latitude, userLocation.longitude,
                    destinationLocation.latitude, destinationLocation.longitude
                )
                return formatDistance(directDistance)
            }

            // Convert user location to GeoPoint
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            // Find closest point on route
            var closestPointIndex = 0
            var minDistance = Double.MAX_VALUE

            cachedRoute.forEachIndexed { index, point ->
                val distance = userPoint.distanceToAsDouble(point)
                if (distance < minDistance) {
                    minDistance = distance
                    closestPointIndex = index
                }
            }

            // Calculate remaining distance along route
            var remainingDistance = 0.0
            for (i in closestPointIndex until cachedRoute.size - 1) {
                remainingDistance += calculateDirectDistance(
                    cachedRoute[i].latitude, cachedRoute[i].longitude,
                    cachedRoute[i + 1].latitude, cachedRoute[i + 1].longitude
                )
            }

            return formatDistance(remainingDistance)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating remaining distance: ${e.message}", e)
            return "Entfernung: unbekannt"
        }
    }


    /**
     * Get navigation instruction based on current location and route
     */
    fun getNavigationInstruction(
        userLocation: LocationData,
        destinationLocation: LocationData
    ): String {
        try {
            if (cachedRoad == null) {
                return "Keine Route berechnet"
            }

            // Convert locations to GeoPoints
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val destPoint = GeoPoint(destinationLocation.latitude, destinationLocation.longitude)

            // Get the road nodes
            val nodes = cachedRoad?.mNodes ?: return "Folgen Sie der Route"

            if (nodes.isEmpty()) {
                return "Folgen Sie der Route"
            }

            // If we're close to destination, we've arrived
            if (userPoint.distanceToAsDouble(destPoint) < 30) {
                return "Sie haben Ihr Ziel erreicht"
            }

            // Find closest node to user position
            var closestNodeIndex = 0
            var minDistance = Double.MAX_VALUE

            for (i in nodes.indices) {
                val nodePoint = nodes[i].mLocation
                val distance = userPoint.distanceToAsDouble(nodePoint)

                if (distance < minDistance) {
                    minDistance = distance
                    closestNodeIndex = i
                }
            }

            // If we're off route
            if (minDistance > 50) {
                return "Kehren Sie zur Route zurück"
            }

            // Get the next node instruction
            val nextNodeIndex = min(closestNodeIndex + 1, nodes.size - 1)

            if (nextNodeIndex > closestNodeIndex) {
                val instruction = nodes[closestNodeIndex].mInstructions

                // Calculate distance to next instruction
                val distance = nodes[closestNodeIndex].mLength

                // Format distance for display
                val formattedDistance = when {
                    distance < 30 -> "Jetzt"
                    distance < 100 -> "In Kürze"
                    distance < 1000 -> "In ${(distance / 10).toInt() * 10} Metern"
                    else -> "In ${String.format("%.1f", distance / 1000)} km"
                }

                // Return formatted instruction
                return if (instruction.isNullOrBlank()) {
                    "Folgen Sie der Route"
                } else {
                    "$formattedDistance: $instruction"
                }
            }

            // Return default instruction if we can't find a specific one
            return "Folgen Sie der Route"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting navigation instruction: ${e.message}", e)
            return "Folgen Sie der Route"
        }
    }

    /**
     * Get enhanced navigation directions for the current journey
     * This combines OSRM instructions with calculated directions
     */
    fun getEnhancedDirections(
        userLocation: LocationData,
        routePoints: List<GeoPoint>,
        destinationLocation: LocationData
    ): String {
        try {
            // First try to get directions from OSRM road
            val osrmDirections = getNavigationInstruction(userLocation, destinationLocation)

            // If we got specific directions, use them
            if (osrmDirections != "Folgen Sie der Route" && osrmDirections != "Keine Route berechnet") {
                return osrmDirections
            }

            // Fall back to calculated directions from route points
            return RoutingHelpers.getFormattedDirections(
                userLocation,
                routePoints,
                destinationLocation
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enhanced directions: ${e.message}", e)
            return "Folgen Sie der Route"
        }
    }

    /**
     * Initialize the routing system and cache directory
     */
    fun initialize(ctx: Context) {
        Log.d(TAG, "Initializing RoutingGraph")

        // Store context reference
        context = ctx

        // Add this line to initialize RouteDebugger
        RouteDebugger.initialize(ctx)

        // Set up OSMDroid configuration with performance optimizations
        val configuration = Configuration.getInstance().apply {
            load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid")
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.filesDir

            // Performance settings
            tileDownloadThreads = 2
            tileFileSystemThreads = 2
            tileDownloadMaxQueueSize = 8
            tileFileSystemMaxQueueSize = 8
            expirationOverrideDuration = 1000L * 60 * 60 * 24 * 7 // Cache for a week
        }

        // Initialize router
        router = OSRMRouter(ctx)

        // Initialize offline router
        offlineRouter = OfflinePathRouter(ctx)

        Log.d(TAG, "RoutingGraph initialization complete")
    }

    /**
     * Set the current MapView for use in offline routing
     * This should be called when the MapView is available
     */
    fun setCurrentMapView(mapView: MapView) {
        currentMapView = mapView

        // Initialize the offline router with the map view
        if (offlineRouter == null) {
            offlineRouter = OfflinePathRouter(mapView.context)
        }
        offlineRouter?.initialize(mapView)
    }

    /**
     * Initialize cache directory for offline tiles
     */
    fun initializeCacheDir(context: Context) {
        val cacheDir = File(context.cacheDir, "osmdroid")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Set custom colors for route visualization
     */
    fun setColors(route: Int, userMarker: Int, destinationMarker: Int) {
        routeColor = route
        userMarkerColor = userMarker
        destinationMarkerColor = destinationMarker
    }

    /**
     * Validate a route to ensure it is reasonable and follows roads
     */
    private fun validateRoute(routePoints: List<GeoPoint>, startPoint: GeoPoint, endPoint: GeoPoint): Boolean {
        // Use our new RouteValidator for comprehensive validation
        val validationResult = RouteValidator.validateRoute(routePoints, startPoint, endPoint, context)

        if (!validationResult.isValid) {
            Log.w(TAG, "Route validation failed: ${validationResult.reason}")
            RouteDebugger.logRouteValidationFailed(validationResult.reason, routePoints)
            return false
        }

        Log.d(TAG, "Route validation passed: ${validationResult.reason}")
        return true
    }

    /**
     * Create a fallback route when routing fails
     */
    private fun createFallbackRoute(startPoint: GeoPoint, endPoint: GeoPoint): List<GeoPoint> {
        Log.d(TAG, "Creating fallback route from ${startPoint.latitude},${startPoint.longitude} to ${endPoint.latitude},${endPoint.longitude}")

        // First, try to get a route from the offline router if available
        if (offlineRouter != null && currentMapView != null) {
            try {
                val offlineRoad = offlineRouter!!.getRoute(startPoint, endPoint, currentMapView!!)
                val offlinePoints = offlineRouter!!.getRoutePoints(offlineRoad)

                // Validate the offline route
                val validationResult = RouteValidator.validateRoute(offlinePoints, startPoint, endPoint)
                if (validationResult.isValid && offlinePoints.size >= 3) {
                    Log.d(TAG, "Using validated offline route with ${offlinePoints.size} points")
                    RouteDebugger.logRoutingSuccess(offlinePoints, offlineRoad.mLength, "OfflineRouter-Fallback", 0)
                    return offlinePoints
                } else {
                    Log.w(TAG, "Offline route invalid: ${validationResult.reason}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating offline fallback route: ${e.message}")
            }
        }

        // If offline routing fails or is invalid, fall back to direct line with midpoint
        // This creates a very minimal "L" shaped route that's better than a straight line
        try {
            // Calculate midpoint with slight offset to create an L shape
            val latMid = (startPoint.latitude + endPoint.latitude) / 2
            val lonMid = (startPoint.longitude + endPoint.longitude) / 2

            // Calculate direct distance
            val directDistance = calculateDirectDistance(
                startPoint.latitude, startPoint.longitude,
                endPoint.latitude, endPoint.longitude
            )

            // For very short distances, just use a direct line
            if (directDistance < 100) {
                Log.d(TAG, "Using direct line for short distance (${directDistance.toInt()}m)")
                return listOf(startPoint, endPoint)
            }

            // For longer distances, create a simple L-shaped route
            val midPoint1 = GeoPoint(startPoint.latitude, endPoint.longitude)

            // Log the fallback route creation
            Log.d(TAG, "Created L-shaped fallback route with 3 points")
            RouteDebugger.logFallbackUsed("DirectFallback", "L-shaped route")

            return listOf(startPoint, midPoint1, endPoint)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating L-shaped fallback: ${e.message}")
            // Last resort: direct line
            return listOf(startPoint, endPoint)
        }
    }

    /**
     * Display a fallback route when a valid route cannot be found
     */
    private fun displayFallbackRoute(mapView: MapView, startPoint: GeoPoint, endPoint: GeoPoint, routePoints: List<GeoPoint>) {
        try {
            // Clear existing routes
            clearRoutes(mapView)

            // Create a polyline with dashed pattern and different color to indicate fallback route
            val roadOverlay = Polyline(mapView)
            roadOverlay.setPoints(routePoints)

            // Use a different color for fallback routes
            roadOverlay.outlinePaint.color = Color.rgb(255, 165, 0) // Orange for fallback routes
            roadOverlay.outlinePaint.strokeWidth = 8f
            roadOverlay.outlinePaint.strokeJoin = Paint.Join.ROUND
            roadOverlay.outlinePaint.strokeCap = Paint.Cap.ROUND
            roadOverlay.outlinePaint.style = Paint.Style.STROKE

            // Set dashed pattern to indicate this is a fallback route
            roadOverlay.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)

            // Add to map
            mapView.overlays.add(roadOverlay)

            // Add markers
            updateUserMarker(mapView, startPoint)
            addDestinationMarker(mapView, endPoint)

            // Add text overlay explaining this is a fallback route
            val textOverlay = org.osmdroid.views.overlay.FolderOverlay()
            val textMarker = Marker(mapView)
            textMarker.position = GeoPoint(
                (startPoint.latitude + endPoint.latitude) / 2,
                (startPoint.longitude + endPoint.longitude) / 2
            )
            textMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            textMarker.title = "Fallback Route"
            textMarker.snippet = "Actual route unavailable"

            textOverlay.add(textMarker)
            mapView.overlays.add(textOverlay)

            // Show debug toast
            if (context != null) {
                RouteDebugger.showDebugToast(mapView.context, "Displaying fallback route (not following roads)")
            }

            // Refresh map
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying fallback route: ${e.message}", e)
        }
    }

    /**
     * Calculate route between two points
     */


    /**
     * Display the calculated road on the map
     */
    private fun displayRoad(
        mapView: MapView,
        road: Road,
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ) {
        try {
            // Create a road overlay
            val roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay.outlinePaint.color = routeColor
            roadOverlay.outlinePaint.strokeWidth = 10f

            // Add to map
            mapView.overlays.add(roadOverlay)

            // Add markers
            updateUserMarker(mapView, startPoint)
            addDestinationMarker(mapView, endPoint)

            // Refresh map
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying road: ${e.message}", e)
        }
    }

    /**
     * Update user marker position
     */
    fun updateUserMarker(mapView: MapView, position: GeoPoint) {
        try {
            // Remove existing user markers
            val userMarkers =
                mapView.overlays.filterIsInstance<Marker>().filter { it.title == "User" }
            mapView.overlays.removeAll(userMarkers)

            // Add new marker
            val marker = Marker(mapView)
            marker.position = position
            marker.title = "User"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Set icon
            marker.icon =
                MarkerCreationHelpers.createGlowingLocationMarker(mapView.context, userMarkerColor)

            mapView.overlays.add(marker)
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user marker: ${e.message}", e)
        }
    }

    /**
     * Add destination marker
     */
    private fun addDestinationMarker(mapView: MapView, position: GeoPoint) {
        try {
            // Remove existing destination markers
            val destMarkers =
                mapView.overlays.filterIsInstance<Marker>().filter { it.title == "Ziel" }
            mapView.overlays.removeAll(destMarkers)

            // Add new marker
            val marker = Marker(mapView)
            marker.position = position
            marker.title = "Ziel"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Set icon
            marker.icon =
                MarkerCreationHelpers.createCustomPin(mapView.context, destinationMarkerColor)

            mapView.overlays.add(marker)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding destination marker: ${e.message}", e)
        }
    }

    /**
     * Clear all routes from the map
     */
    private fun clearRoutes(mapView: MapView) {
        try {
            // Remove all polylines
            val overlaysToRemove = mapView.overlays.filter {
                it is Polyline || (it is Marker && (it.title == "Ziel"))
            }
            mapView.overlays.removeAll(overlaysToRemove)
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing routes: ${e.message}", e)
        }
    }

    /**
     * Get a route between two points using OSRM (or fallback)
     */
    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        context: Context
    ): Pair<List<GeoPoint>, Double> {
        return withContext(Dispatchers.IO) {
            try {
                // Create GeoPoints
                val startPoint = GeoPoint(startLat, startLon)
                val endPoint = GeoPoint(endLat, endLon)

                // Get the road from the router
                val road = router.getRoad(startPoint, endPoint)

                // Get route points
                val routePoints = getRoutePoints(road)

                if (routePoints.isNotEmpty()) {
                    // Validate the route
                    val validationResult = RouteValidator.validateRoute(routePoints, startPoint, endPoint)
                    if (!validationResult.isValid) {
                        Log.w(TAG, "Route validation failed: ${validationResult.reason}")
                        RouteDebugger.logRouteValidationFailed(validationResult.reason, routePoints)

                        // Fall back to a safer route
                        val fallbackRoute = createFallbackRoute(startPoint, endPoint)
                        var fallbackDistance = 0.0
                        for (i in 0 until fallbackRoute.size - 1) {
                            fallbackDistance += calculateDirectDistance(
                                fallbackRoute[i].latitude, fallbackRoute[i].longitude,
                                fallbackRoute[i + 1].latitude, fallbackRoute[i + 1].longitude
                            )
                        }
                        return@withContext Pair(fallbackRoute, fallbackDistance)
                    }

                    // Calculate total distance for valid route
                    var totalDistance = 0.0
                    for (i in 0 until routePoints.size - 1) {
                        totalDistance += calculateDirectDistance(
                            routePoints[i].latitude, routePoints[i].longitude,
                            routePoints[i + 1].latitude, routePoints[i + 1].longitude
                        )
                    }

                    Pair(routePoints, totalDistance)
                } else {
                    // Use path-aware route creation for better fallback routes
                    val fallbackRoute = createPathAwareRoute(startPoint, endPoint, context)

                    // Calculate fallback distance
                    var fallbackDistance = 0.0
                    for (i in 0 until fallbackRoute.size - 1) {
                        fallbackDistance += calculateDirectDistance(
                            fallbackRoute[i].latitude, fallbackRoute[i].longitude,
                            fallbackRoute[i + 1].latitude, fallbackRoute[i + 1].longitude
                        )
                    }

                    Pair(fallbackRoute, fallbackDistance)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in route calculation: ${e.message}", e)

                // Fall back to path-aware route
                val startPoint = GeoPoint(startLat, startLon)
                val endPoint = GeoPoint(endLat, endLon)
                val fallbackRoute = createPathAwareRoute(startPoint, endPoint, context)

                // Calculate fallback distance
                var fallbackDistance = 0.0
                for (i in 0 until fallbackRoute.size - 1) {
                    fallbackDistance += calculateDirectDistance(
                        fallbackRoute[i].latitude, fallbackRoute[i].longitude,
                        fallbackRoute[i + 1].latitude, fallbackRoute[i + 1].longitude
                    )
                }

                Pair(fallbackRoute, fallbackDistance)
            }
        }
    }

    /**
     * Extract route points from a Road object
     */
    private fun getRoutePoints(road: Road): List<GeoPoint> {
        return try {
            road.mRouteHigh
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting route points: ${e.message}")
            emptyList()
        }
    }

    /**
     * Create a route that attempts to follow actual paths on the map
     * This is used when online routing fails and direct fallback is needed
     */
    private fun createPathAwareRoute(
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        context: Context
    ): List<GeoPoint> {
        // Try to use the offline router if available
        if (currentMapView != null && offlineRouter != null) {
            try {
                val offlineRoad = offlineRouter!!.getRoute(startPoint, endPoint, currentMapView!!)
                val offlinePoints = offlineRouter!!.getRoutePoints(offlineRoad)

                // Validate the offline route
                val validationResult = RouteValidator.validateRoute(offlinePoints, startPoint, endPoint)
                if (validationResult.isValid && offlinePoints.size >= 2) {
                    Log.d(TAG, "Using validated offline route with ${offlinePoints.size} points")
                    return offlinePoints
                } else {
                    Log.w(TAG, "Offline route validation failed: ${validationResult.reason}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating path-aware route: ${e.message}", e)
            }
        }

        // Use a simple L-shaped route as a fallback to avoid water crossings
        val midPoint = GeoPoint(startPoint.latitude, endPoint.longitude)

        // Log that we're using a simple L-shaped route
        Log.d(TAG, "Using L-shaped fallback route for safety")
        RouteDebugger.logFallbackUsed("L-shapedRoute", "No valid path available")

        return listOf(startPoint, midPoint, endPoint)
    }

    /**
     * Create an L-shaped route and display it on the map
     * Now uses path-aware routing for better results
     */
    fun forceStreetBasedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        routeColor: Int,
        userLocationColor: Int,
        destinationColor: Int,
        context: Context
    ): List<GeoPoint> {
        // Update current MapView
        setCurrentMapView(mapView)

        // Clear existing overlays but keep rotation
        val existingOverlays = mapView.overlays.filter { it is RotationGestureOverlay }
        mapView.overlays.clear()
        mapView.overlays.addAll(existingOverlays)

        // Try to get a path-aware route
        val points = createPathAwareRoute(startPoint, endPoint, context)

        // Add the route to the map
        addDirectionRoute(
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

        return points
    }

    /**
     * Add route line, direction arrows, and markers for the journey
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
            icon = MarkerCreationHelpers.createCustomPin(context, destinationColor)
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
        updateUserMarker(mapView, startPoint)

        // Limit number of direction arrows for performance
        if (routePoints.size > 3) {
            MarkerCreationHelpers.addOptimizedDirectionArrows(
                mapView,
                routePoints,
                routeColor,
                context
            )
        }
    }



/*
        suspend fun calculateRoute(
            mapView: MapView,
            startPoint: GeoPoint,
            endPoint: GeoPoint,
            callback: (Boolean, String?, List<GeoPoint>) -> Unit
        ) {
            RouteDebugger.logRoutingAttempt(startPoint, endPoint, "RoutingGraph")
            val startTime = System.currentTimeMillis()

            setCurrentMapView(mapView)

            // Check cache
            if (isRouteCached(startPoint, endPoint)) {
                displayCachedRoute(mapView, startPoint, endPoint, callback)
                return
            }

            clearRoutes(mapView)

            scope.launch {
                try {
                    // Try OSRM first
                    val osrmRoad = try {
                        router.getRoad(startPoint, endPoint)
                    } catch (e: Exception) {
                        null
                    }

                    val osrmPoints = osrmRoad?.mRouteHigh ?: emptyList()
                    val osrmValid = osrmRoad != null &&
                            validateRoute(osrmPoints, startPoint, endPoint).isValid

                    if (osrmValid) {
                        handleValidOSRMRoute(mapView, startPoint, endPoint, osrmRoad, callback)
                        return@launch
                    }

                    // Fallback to offline routing
                    val fallbackPoints = createEnhancedFallbackRoute(startPoint, endPoint, mapView.context)
                    displayFallbackRoute(mapView, startPoint, endPoint, fallbackPoints)

                    callback(true, "Using enhanced fallback route", fallbackPoints)
                    cacheRoute(startPoint, endPoint, fallbackPoints)

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val emergencyRoute = createEmergencyRoute(startPoint, endPoint)
                        displayFallbackRoute(mapView, startPoint, endPoint, emergencyRoute)
                        callback(false, "Routing error: ${e.message}", emergencyRoute)
                    }
                }
            }
        }

        private suspend fun createEnhancedFallbackRoute(
            start: GeoPoint,
            end: GeoPoint,
            context: Context
        ): List<GeoPoint> {
            return withContext(Dispatchers.IO) {
                // Try offline router first
                if (currentMapView != null && offlineRouter != null) {
                    try {
                        val road = offlineRouter!!.getRoute(start, end, currentMapView!!)
                        val points = offlineRouter!!.getRoutePoints(road)
                        if (validateRoute(points, start, end).isValid) {
                            return@withContext points
                        }
                    } catch (e: Exception) {
                        RouteDebugger.logFallbackUsed("OfflineRouter", e.message ?: "Unknown error")
                    }
                }

                // Final fallback - smart L-shaped route
                createSmartLRoute(start, end)
            }
        }

        private fun createSmartLRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
            // Analyze which axis has more consistent direction
            val latDiff = abs(end.latitude - start.latitude)
            val lonDiff = abs(end.longitude - start.longitude)

            return if (latDiff > lonDiff) {
                // Prefer latitude-first route
                val mid = GeoPoint(end.latitude, start.longitude)
                listOf(start, mid, end)
            } else {
                // Prefer longitude-first route
                val mid = GeoPoint(start.latitude, end.longitude)
                listOf(start, mid, end)
            }
        }
        */
suspend fun calculateRoute(
    mapView: MapView,
    startPoint: GeoPoint,
    endPoint: GeoPoint,
    callback: (Boolean, String?, List<GeoPoint>) -> Unit
) {
    RouteDebugger.logRoutingAttempt(startPoint, endPoint, "RoutingGraph")
    val startTime = System.currentTimeMillis()

    setCurrentMapView(mapView)

    // Check cache



}

    private suspend fun createEnhancedFallbackRoute(
        start: GeoPoint,
        end: GeoPoint,
        context: Context
    ): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            // Try offline router first


            // Final fallback - smart L-shaped route
            createSmartLRoute(start, end)
        }
    }

    private fun createSmartLRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        // Analyze which axis has more consistent direction
        val latDiff = abs(end.latitude - start.latitude)
        val lonDiff = abs(end.longitude - start.longitude)

        return if (latDiff > lonDiff) {
            // Prefer latitude-first route
            val mid = GeoPoint(end.latitude, start.longitude)
            listOf(start, mid, end)
        } else {
            // Prefer longitude-first route
            val mid = GeoPoint(start.latitude, end.longitude)
            listOf(start, mid, end)
        }
    }


        // ... rest of the class

}