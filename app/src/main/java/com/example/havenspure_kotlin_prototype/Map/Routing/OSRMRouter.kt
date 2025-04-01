package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import org.osmdroid.views.MapView
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import com.example.havenspure_kotlin_prototype.Map.Routing.RouteDebugger

class OSRMRouter(val context: Context) {
    companion object {
        private const val TAG = "OSRMRouter"

        // Define the routing profiles
        enum class RoutingProfile {
            FOOT,
            BICYCLE
        }

        // List of free OSRM endpoints to try for each profile
        // Added more alternative endpoints for better reliability
        private val OSRM_ENDPOINTS = mapOf(
            RoutingProfile.FOOT to listOf(
                "https://routing.openstreetmap.de/routed-foot/route/v1/foot/", // Main endpoint
                "https://router.project-osrm.org/route/v1/foot/",              // Official demo endpoint
                "https://maps.openrouteservice.org/directions?n1=null&n2=null&n3=null&b=0&c=0&k1=en-US&k2=km", // Alternative
                "http://router.project-osrm.org/route/v1/foot/"                // Non-https fallback
            ),
            RoutingProfile.BICYCLE to listOf(
                "https://routing.openstreetmap.de/routed-bike/route/v1/bike/", // Main bike endpoint
                "https://router.project-osrm.org/route/v1/bike/",              // Official demo bike endpoint
                "http://router.project-osrm.org/route/v1/bicycle/",            // Non-https fallback
                "https://routing.openstreetmap.de/routed-bike/route/v1/cycling/" // Alternative syntax
            )
        )

        // Constants for request timeout control
        private const val CONNECTION_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000

        // Added retry parameters
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    // Default to bicycle routing
    private var currentProfile = RoutingProfile.BICYCLE

    // Get primary endpoint based on profile
    private fun getPrimaryEndpoint(): String {
        return OSRM_ENDPOINTS[currentProfile]?.get(0) ?: OSRM_ENDPOINTS[RoutingProfile.FOOT]!![0]
    }

    // Get fallback endpoints based on profile
    private fun getFallbackEndpoints(): List<String> {
        return OSRM_ENDPOINTS[currentProfile]?.drop(1) ?: OSRM_ENDPOINTS[RoutingProfile.FOOT]!!.drop(1)
    }

    // Offline router for path-aware routing
    private var offlineRouter: OfflinePathRouter? = null
    private var mapView: MapView? = null

    /**
     * Set the routing profile to use
     */
    fun setRoutingProfile(profile: RoutingProfile) {
        this.currentProfile = profile
        Log.d(TAG, "Routing profile set to: $profile")
    }

    /**
     * Check network availability
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Set the map view for use by the offline router
     */
    fun setMapView(mapView: MapView) {
        this.mapView = mapView

        // Initialize the offline path router if not already initialized
        if (offlineRouter == null) {
            offlineRouter = OfflinePathRouter(context)
        }

        // Initialize with map data
        offlineRouter?.initialize(mapView)
    }

    /**
     * Get road between two points with improved resilience
     */
    /**
     * Get road between two points with improved resilience
     */
    fun getRoad(startPoint: GeoPoint, endPoint: GeoPoint): Road {
        // Declare startTime at the beginning of the method so it's available in all scopes
        val startTime = System.currentTimeMillis()

        // Check for valid inputs
        if (!isValidPoint(startPoint) || !isValidPoint(endPoint)) {
            Log.e(TAG, "Invalid start or end point coordinates")
            RouteDebugger.logRoutingFailure("OSRMRouter", "Invalid coordinates", 0, true)
            return createSimplifiedFallbackRoad(startPoint, endPoint, "Invalid coordinates")
        }

        // Log routing attempt
        RouteDebugger.logRoutingAttempt(startPoint, endPoint, "OSRMRouter")

        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No internet connectivity, using offline routing")
            RouteDebugger.logFallbackUsed("OfflineRouter", "No internet connectivity")
            return getOfflineRoad(startPoint, endPoint)
        }

        // Try all available endpoints with retry logic
        try {
            // First try primary endpoint with retries
            val primaryEndpoint = getPrimaryEndpoint()
            Log.d(TAG, "Using primary endpoint for ${currentProfile}: $primaryEndpoint")

            for (attempt in 1..MAX_RETRIES) {
                try {
                    val road = getDirectOSRMRoad(startPoint, endPoint, primaryEndpoint)
                    if (road != null && isValidRoad(road, startPoint, endPoint)) {
                        val routePoints = getRoutePoints(road)
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Successfully routed using primary endpoint on attempt $attempt")
                        RouteDebugger.logRoutingSuccess(routePoints, road.mLength,
                            "OSRMRouter-Primary", duration)
                        return road
                    } else {
                        Log.w(TAG, "Primary endpoint returned invalid road on attempt $attempt")
                        RouteDebugger.logRouteValidationFailed("Invalid road from primary endpoint",
                            road?.let { getRoutePoints(it) })
                    }
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "Primary endpoint failed on attempt $attempt: ${e.message}")
                    RouteDebugger.logRoutingFailure("OSRMRouter-Primary",
                        "Attempt $attempt: ${e.message}", duration, false)

                    if (attempt < MAX_RETRIES) {
                        Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                        Thread.sleep(RETRY_DELAY_MS)
                    }
                }
            }

            // If primary endpoint fails after retries, try all fallback endpoints
            for (endpoint in getFallbackEndpoints()) {
                Log.d(TAG, "Trying fallback endpoint for ${currentProfile}: $endpoint")

                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val fallbackRoad = getDirectOSRMRoad(startPoint, endPoint, endpoint)
                        if (fallbackRoad != null && isValidRoad(fallbackRoad, startPoint, endPoint)) {
                            val fallbackPoints = getRoutePoints(fallbackRoad)
                            val duration = System.currentTimeMillis() - startTime
                            Log.d(TAG, "Successfully routed using fallback endpoint $endpoint on attempt $attempt")
                            RouteDebugger.logRoutingSuccess(fallbackPoints, fallbackRoad.mLength,
                                "OSRMRouter-Fallback", duration)
                            return fallbackRoad
                        } else {
                            Log.w(TAG, "Fallback endpoint $endpoint returned invalid road on attempt $attempt")
                            RouteDebugger.logRouteValidationFailed("Invalid road from fallback endpoint $endpoint",
                                fallbackRoad?.let { getRoutePoints(it) })
                        }
                    } catch (e: Exception) {
                        val duration = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Fallback endpoint $endpoint failed on attempt $attempt: ${e.message}")
                        RouteDebugger.logRoutingFailure("OSRMRouter-Fallback",
                            "Endpoint $endpoint, attempt $attempt: ${e.message}", duration, false)

                        if (attempt < MAX_RETRIES) {
                            Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                            Thread.sleep(RETRY_DELAY_MS)
                        }
                    }
                }
            }

            Log.e(TAG, "All OSRM endpoints failed, falling back to offline routing")
            RouteDebugger.logFallbackUsed("OfflineRouter", "All OSRM endpoints failed")
        } catch (e: Exception) {
            Log.e(TAG, "Major error in routing process: ${e.message}", e)
            RouteDebugger.logRoutingFailure("OSRMRouter", "Major error: ${e.message}",
                System.currentTimeMillis() - startTime, true)
        }

        // Show debug toast to notify user
        if (context != null) {
            RouteDebugger.showDebugToast(context, "Online routing failed, using offline mode")
        }

        // Fall back to offline routing
        val offlineRoad = getOfflineRoad(startPoint, endPoint)
        val offlineRoutePoints = getRoutePoints(offlineRoad)
        RouteDebugger.logRoutingSuccess(offlineRoutePoints, offlineRoad.mLength,
            "OfflineRouter", System.currentTimeMillis() - startTime)
        return offlineRoad
    }

    /**
     * Validate if a GeoPoint has reasonable coordinates
     */
    private fun isValidPoint(point: GeoPoint): Boolean {
        return point.latitude >= -90 && point.latitude <= 90 &&
                point.longitude >= -180 && point.longitude <= 180
    }

    /**
     * Get a road using the offline path router
     */
    private fun getOfflineRoad(startPoint: GeoPoint, endPoint: GeoPoint): Road {
        return if (offlineRouter != null && mapView != null) {
            Log.d(TAG, "Using offline path router")
            offlineRouter!!.getRoute(startPoint, endPoint, mapView!!)
        } else {
            Log.w(TAG, "Offline path router not initialized, using simplified fallback")
            createSimplifiedFallbackRoad(startPoint, endPoint, "Offline router not initialized")
        }
    }

    /**
     * Make a direct HTTP request to OSRM and parse the response with improved error handling
     */
    private fun getDirectOSRMRoad(startPoint: GeoPoint, endPoint: GeoPoint, endpoint: String): Road? {
        // Create a default road object in case we need to return early
        val road = Road()

        try {
            // Build the URL with optimization parameters
            val urlString = "$endpoint${startPoint.longitude},${startPoint.latitude};${endPoint.longitude},${endPoint.latitude}?alternatives=true&overview=full&steps=true&geometries=polyline&continue_straight=true"

            Log.d(TAG, "OSRM Request URL: $urlString")

            // Make the HTTP request
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "HavenspureApp/1.0") // Set a user-agent

            // Read the response
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error code: $responseCode")
                RouteDebugger.logHttpFailure(endpoint, "HTTP error code: $responseCode", responseCode)
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            val responseContent = response.toString()

            // Log the response for debugging
            Log.d(TAG, "OSRM Response (first 200 chars): ${responseContent.take(200)}...")
            RouteDebugger.logServerResponse(endpoint, responseCode, responseContent)

            // Parse the JSON response
            val jsonResponse = JSONObject(responseContent)

            // Check for routing errors
            if (jsonResponse.has("code")) {
                val code = jsonResponse.getString("code")
                if (code != "Ok") {
                    Log.e(TAG, "OSRM error: ${jsonResponse.optString("message", "Unknown error")}")
                    return null
                }
            }

            // Validate that we have routes
            if (!jsonResponse.has("routes")) {
                Log.e(TAG, "OSRM response missing 'routes' field")
                return null
            }

            val routes = jsonResponse.getJSONArray("routes")
            if (routes.length() == 0) {
                Log.e(TAG, "No routes found in response")
                return null
            }

            try {
                // Set road status to OK
                val statusField = Road::class.java.getDeclaredField("mStatus")
                statusField.isAccessible = true
                statusField.set(road, Road.STATUS_OK)

                // Get the best route (either fastest or shortest)
                val bestRoute = selectBestRoute(routes)

                // Set road length
                val lengthField = Road::class.java.getDeclaredField("mLength")
                lengthField.isAccessible = true
                val distanceInMeters = bestRoute.getDouble("distance")
                lengthField.set(road, distanceInMeters)

                // Validate the geometry exists
                if (!bestRoute.has("geometry")) {
                    Log.e(TAG, "Selected route missing geometry")
                    RouteDebugger.logHttpFailure(endpoint, "Selected route missing geometry field")
                    return null
                }

                // Set route points - THIS IS THE CRITICAL PART FOR CORRECT ROUTE DISPLAY
                val geometry = bestRoute.getString("geometry")
                if (geometry.isBlank()) {
                    Log.e(TAG, "Empty geometry string in route")
                    RouteDebugger.logHttpFailure(endpoint, "Empty geometry string in route")
                    return null
                }

                val routePoints = decodePolyline(geometry)

                // Validate decoded points
                if (routePoints.isEmpty()) {
                    Log.e(TAG, "Failed to decode route polyline")
                    RouteDebugger.logHttpFailure(endpoint, "Failed to decode route polyline")
                    return null
                }

                // Log decoded points for verification
                Log.d(TAG, "First 3 route points: ${routePoints.take(3)}")
                RouteDebugger.logPolylineDecoding(geometry.length, routePoints.size, routePoints)

                val routeHighField = Road::class.java.getDeclaredField("mRouteHigh")
                routeHighField.isAccessible = true
                routeHighField.set(road, routePoints)

                // Log route information
                Log.d(TAG, "Successfully parsed route with ${routePoints.size} points, distance: ${distanceInMeters}m")

                return road
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing OSRM response: ${e.message}", e)
                return null
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timed out for OSRM request: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error making direct OSRM request: ${e.message}", e)
            return null
        }
    }

    /**
     * Validate the generated road to ensure it follows actual roads
     */
    private fun isValidRoad(road: Road, startPoint: GeoPoint, endPoint: GeoPoint): Boolean {
        try {
            // Get the route points
            val routePoints = road.mRouteHigh

            // Check if we have enough points for a meaningful route
            if (routePoints.size < 3) {
                Log.e(TAG, "Route has too few points: ${routePoints.size}")
                return false
            }

            // Check if route actually starts near the requested start point
            val distanceToStart = calculateDirectDistance(
                routePoints.first().latitude, routePoints.first().longitude,
                startPoint.latitude, startPoint.longitude
            )

            // Check if route actually ends near the requested end point
            val distanceToEnd = calculateDirectDistance(
                routePoints.last().latitude, routePoints.last().longitude,
                endPoint.latitude, endPoint.longitude
            )

            // A valid route should start and end close to the requested points
            if (distanceToStart > 500 || distanceToEnd > 500) {
                Log.e(TAG, "Route endpoints are too far from requested points: start=$distanceToStart, end=$distanceToEnd")
                return false
            }

            // Check if the route has reasonable segment lengths
            for (i in 0 until routePoints.size - 1) {
                val segmentLength = calculateDirectDistance(
                    routePoints[i].latitude, routePoints[i].longitude,
                    routePoints[i+1].latitude, routePoints[i+1].longitude
                )

                // Warn about suspiciously long segments (might indicate cutting across water/terrain)
                if (segmentLength > 500) {
                    Log.w(TAG, "Route contains a suspiciously long segment: ${segmentLength}m between points $i and ${i+1}")
                    // We don't return false here, but log for debugging
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating road: ${e.message}", e)
            return false
        }
    }

    /**
     * Calculate direct distance between two points
     */
    private fun calculateDirectDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lon1Rad = Math.toRadians(lon1)
        val lon2Rad = Math.toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon/2) * Math.sin(dLon/2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

        return radius * c
    }

    /**
     * Select the best route from alternatives
     * This selects based on a heuristic that considers both duration and distance
     */
    private fun selectBestRoute(routes: JSONArray): JSONObject {
        if (routes.length() == 1) {
            return routes.getJSONObject(0)
        }

        // Score each route and pick the best one
        var bestScore = Double.MAX_VALUE
        var bestRouteIndex = 0

        for (i in 0 until routes.length()) {
            val route = routes.getJSONObject(i)
            val distance = route.getDouble("distance")
            val duration = route.getDouble("duration")

            // Score is a weighted combination of distance and duration
            // For bicycle, we might prioritize safety and ease over pure speed
            val score = when (currentProfile) {
                RoutingProfile.BICYCLE -> (0.6 * duration) + (0.4 * distance)
                else -> (0.7 * duration) + (0.3 * distance) // Default for foot
            }

            if (score < bestScore) {
                bestScore = score
                bestRouteIndex = i
            }
        }

        return routes.getJSONObject(bestRouteIndex)
    }

    /**
     * Decode polyline from OSRM response with additional validation
     * This implements the polyline algorithm for decoding the geometry string
     */
    private fun decodePolyline(encoded: String): ArrayList<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        try {
            while (index < len) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    if (index >= len) {
                        Log.e(TAG, "Polyline decoding error: index out of bounds")
                        break
                    }
                    b = encoded[index++].toInt() - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlat = if (result and 1 != 0) -(result shr 1) else result shr 1
                lat += dlat

                shift = 0
                result = 0
                do {
                    if (index >= len) {
                        Log.e(TAG, "Polyline decoding error: index out of bounds")
                        break
                    }
                    b = encoded[index++].toInt() - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlng = if (result and 1 != 0) -(result shr 1) else result shr 1
                lng += dlng

                // Validate decoded coordinates
                val latValue = lat * 1e-5
                val lngValue = lng * 1e-5

                if (latValue >= -90 && latValue <= 90 && lngValue >= -180 && lngValue <= 180) {
                    val p = GeoPoint(latValue, lngValue)
                    points.add(p)
                } else {
                    Log.e(TAG, "Invalid coordinates from polyline: lat=$latValue, lng=$lngValue")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding polyline: ${e.message}", e)
        }

        // Do a final validation of the decoded path
        if (points.size < 2) {
            Log.e(TAG, "Decoded polyline has too few points: ${points.size}")
        }

        return points
    }

    /**
     * Create a simplified fallback road
     */
    private fun createSimplifiedFallbackRoad(
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        reason: String
    ): Road {
        Log.d(TAG, "Creating fallback road: $reason")

        val road = Road()

        try {
            // Set status
            val statusField = road.javaClass.getDeclaredField("mStatus")
            statusField.isAccessible = true
            statusField.set(road, Road.STATUS_TECHNICAL_ISSUE)

            // Set route points - Try to use a more natural path
            val routeHighField = road.javaClass.getDeclaredField("mRouteHigh")
            routeHighField.isAccessible = true

            // Use offline router if available, otherwise create minimal path
            val routePoints = if (offlineRouter != null && mapView != null) {
                val offlineRoad = offlineRouter!!.getRoute(startPoint, endPoint, mapView!!)
                offlineRouter!!.getRoutePoints(offlineRoad)
            } else {
                // Fall back to direct line - no random zigzag
                listOf(startPoint, endPoint)
            }

            routeHighField.set(road, ArrayList(routePoints))

            // Set length
            val lengthField = road.javaClass.getDeclaredField("mLength")
            lengthField.isAccessible = true
            var totalDistance = 0.0
            for (i in 0 until routePoints.size - 1) {
                totalDistance += calculateDirectDistance(
                    routePoints[i].latitude, routePoints[i].longitude,
                    routePoints[i + 1].latitude, routePoints[i + 1].longitude
                )
            }
            lengthField.set(road, totalDistance)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating simplified fallback road: ${e.message}")
        }

        return road
    }

    /**
     * Get route points from a Road object
     */
    fun getRoutePoints(road: Road): List<GeoPoint> {
        return try {
            val routeHighField = road.javaClass.getDeclaredField("mRouteHigh")
            routeHighField.isAccessible = true
            val routePoints = routeHighField.get(road) as? List<GeoPoint>

            routePoints?.toList() ?: listOfFallbackPoints(road)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting route points: ${e.message}")
            listOfFallbackPoints(road)
        }
    }

    /**
     * Get fallback points when route points cannot be retrieved
     */
    private fun listOfFallbackPoints(road: Road): List<GeoPoint> {
        // Try to get path data from map view if available
        if (offlineRouter != null && mapView != null) {
            val offlineRoad = offlineRouter!!.getRoute(
                GeoPoint(53.548554, 8.0840844),
                GeoPoint(53.5142, 8.1428),
                mapView!!
            )
            val offlinePoints = offlineRouter!!.getRoutePoints(offlineRoad)
            if (offlinePoints.isNotEmpty()) {
                return offlinePoints
            }
        }

        // Very basic fallback if all else fails - direct line
        return listOf(
            GeoPoint(53.548554, 8.0840844),
            GeoPoint(53.5142, 8.1428)
        )
    }

    /**
     * Get navigation instructions
     */
    fun getInstructions(road: Road): List<String> {
        // Use offline path router for better instructions if available
        if (offlineRouter != null) {
            val offlineInstructions = offlineRouter!!.getInstructions(road)
            if (offlineInstructions.isNotEmpty()) {
                return offlineInstructions
            }
        }

        // Enhanced instructions based on profile
        return when (currentProfile) {
            RoutingProfile.BICYCLE -> listOf(
                "Start cycling",
                "Continue along the bike path",
                "Arrive at destination"
            )
            RoutingProfile.FOOT -> listOf(
                "Start walking",
                "Continue straight",
                "Arrive at destination"
            )
        }
    }
}