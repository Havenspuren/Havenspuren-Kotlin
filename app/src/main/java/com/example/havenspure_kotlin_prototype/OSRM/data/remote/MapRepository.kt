package com.example.havenspure_kotlin_prototype.OSRM.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.havenspure_kotlin_prototype.OSRM.data.models.*
import com.example.havenspure_kotlin_prototype.OSRM.assistant.MapUtils
import com.h2o.store.data.models.OSRMResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Repository for handling map-related data operations
 * Provides route calculation with fallback mechanisms
 */
class MapRepository(private val context: Context) {
    private val TAG = "MapRepository"

    // API services for different endpoints with failover
    private val apiServices by lazy { RetrofitClient.getAllApiServices(context) }

    /**
     * Get a route between two points
     * Uses multiple fallback mechanisms:
     * 1. Try primary OSRM server with bike profile
     * 2. Try alternative OSRM servers with bike profile
     * 3. Try foot profile as fallback
     * 4. Generate a simple direct route if all network requests fail
     *
     * @param startPoint Starting point
     * @param endPoint Destination point
     * @return RouteData object with route information
     */
    suspend fun getRoute(startPoint: GeoPoint, endPoint: GeoPoint): RouteData {
        // Check if network is available
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, using offline route")
            return createOfflineRoute(startPoint, endPoint)
        }

        // Format coordinates for API
        val coordinates = OSRMApiService.formatCoordinates(
            startPoint.latitude, startPoint.longitude,
            endPoint.latitude, endPoint.longitude
        )

        return withContext(Dispatchers.IO) {
            // First try bike profile on all available servers
            for (apiService in apiServices) {
                try {
                    Log.d(TAG, "Trying to get bike route from ${apiService.javaClass}")

                    // Make API call
                    val response = apiService.getRouteFoot(coordinates)

                    // Process successful response
                    if (response.isSuccessful) {
                        val routeResponse = response.body()

                        if (routeResponse != null && routeResponse.code == "Ok" && routeResponse.routes?.isNotEmpty() == true) {
                            Log.d(TAG, "Successfully retrieved bike route")
                            return@withContext parseOSRMResponse(routeResponse, startPoint, endPoint)
                        }
                    }

                    Log.d(TAG, "Failed to get bike route: ${response.code()}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting bike route: ${e.message}")
                    // Continue to next server on error
                }
            }

            // Try foot profile as fallback
            for (apiService in apiServices) {
                try {
                    Log.d(TAG, "Trying to get foot route from ${apiService.javaClass}")

                    // Make API call
                    val response = apiService.getRouteBike(coordinates)

                    // Process successful response
                    if (response.isSuccessful) {
                        val routeResponse = response.body()

                        if (routeResponse != null && routeResponse.code == "Ok" && routeResponse.routes?.isNotEmpty() == true) {
                            Log.d(TAG, "Successfully retrieved foot route")
                            return@withContext parseOSRMResponse(routeResponse, startPoint, endPoint)
                        }
                    }

                    Log.d(TAG, "Failed to get foot route: ${response.code()}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting foot route: ${e.message}")
                    // Continue to next server on error
                }
            }

            // If all API calls fail, use offline route as last resort
            Log.d(TAG, "All API calls failed, using offline route")
            createOfflineRoute(startPoint, endPoint)
        }
    }

    /**
     * Convert OSRM maneuver type and modifier to internal navigation instruction type
     */
    private fun getInstructionType(type: String, modifier: String?): NavigationInstructionType {
        return when (type) {
            "depart" -> NavigationInstructionType.DEPART
            "arrive" -> NavigationInstructionType.ARRIVE
            "turn" -> {
                when (modifier) {
                    "right" -> NavigationInstructionType.TURN_RIGHT
                    "left" -> NavigationInstructionType.TURN_LEFT
                    "slight right" -> NavigationInstructionType.TURN_SLIGHT_RIGHT
                    "slight left" -> NavigationInstructionType.TURN_SLIGHT_LEFT
                    "sharp right" -> NavigationInstructionType.TURN_SHARP_RIGHT
                    "sharp left" -> NavigationInstructionType.TURN_SHARP_LEFT
                    else -> NavigationInstructionType.CONTINUE
                }
            }
            "continue" -> NavigationInstructionType.CONTINUE
            "new name" -> NavigationInstructionType.CONTINUE
            "straight" -> NavigationInstructionType.GO_STRAIGHT
            "roundabout" -> NavigationInstructionType.ROUNDABOUT
            "exit roundabout" -> NavigationInstructionType.EXIT_ROUNDABOUT
            "uturn" -> {
                when (modifier) {
                    "right" -> NavigationInstructionType.UTURN_RIGHT
                    "left" -> NavigationInstructionType.UTURN_LEFT
                    else -> NavigationInstructionType.UTURN_RIGHT
                }
            }
            else -> NavigationInstructionType.CONTINUE
        }
    }

    /**
     * Parse OSRM API response into RouteData
     */
    private fun parseOSRMResponse(
        response: OSRMResponse,
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ): RouteData {
        try {
            // Get the first (best) route
            val route = response.routes?.firstOrNull() ?: return createOfflineRoute(startPoint, endPoint)

            // Decode the polyline geometry into a list of points
            val routePoints = MapUtils.decodePolyline(route.geometry)

            // Create instructions list
            val instructions = mutableListOf<NavigationInstruction>()

            // Add departure instruction
            instructions.add(
                NavigationInstruction(
                    text = NavigationInstructionType.DEPART.toGermanText(),
                    distance = 0.0,
                    duration = 0.0,
                    type = NavigationInstructionType.DEPART,
                    index = 0
                )
            )

            // Process route steps to create instructions
            if (route.legs.isNotEmpty()) {
                var totalDistance = 0.0
                var totalDuration = 0.0
                var pointIndex = 1 // Start at 1 since we already added the departure

                for (leg in route.legs) {
                    for (step in leg.steps) {
                        val instructionType = getInstructionType(
                            step.maneuver.type,
                            step.maneuver.modifier
                        )

                        // Skip certain instruction types to avoid clutter
                        if (instructionType == NavigationInstructionType.CONTINUE && instructions.last().type != NavigationInstructionType.DEPART) {
                            continue
                        }

                        totalDistance += step.distance
                        totalDuration += step.duration

                        instructions.add(
                            NavigationInstruction(
                                text = instructionType.toGermanText(),
                                distance = totalDistance,
                                duration = totalDuration,
                                type = instructionType,
                                index = pointIndex
                            )
                        )

                        // Increment point index based on the number of points in this step
                        val stepPoints = MapUtils.decodePolyline(step.geometry)
                        pointIndex += stepPoints.size - 1 // -1 because points overlap
                    }
                }
            }

            // Add arrival instruction
            instructions.add(
                NavigationInstruction(
                    text = NavigationInstructionType.ARRIVE.toGermanText(),
                    distance = route.distance,
                    duration = route.duration,
                    type = NavigationInstructionType.ARRIVE,
                    index = routePoints.size - 1
                )
            )

            // Make sure the route starts and ends at the exact requested points
            val adjustedPoints = if (routePoints.isNotEmpty()) {
                val result = ArrayList<GeoPoint>(routePoints.size + 2)
                result.add(startPoint)
                result.addAll(routePoints)
                result.add(endPoint)
                result
            } else {
                listOf(startPoint, endPoint)
            }

            return RouteData(
                points = adjustedPoints,
                distance = route.distance,
                duration = route.duration,
                instructions = instructions,
                status = RouteStatus.SUCCESS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OSRM response: ${e.message}")
            return createOfflineRoute(startPoint, endPoint)
        }
    }

    /**
     * Create a route for offline use or as fallback
     * This creates a more natural path than a straight line by adding intermediate points
     */
    private fun createOfflineRoute(startPoint: GeoPoint, endPoint: GeoPoint): RouteData {
        Log.d(TAG, "Creating offline route")

        // Generate a list of points that form an L-shaped route
        val points = createLShapedRoute(startPoint, endPoint)

        // Calculate distance
        val distance = calculateRouteDistance(points)

        // Create instruction list
        val instructions = mutableListOf<NavigationInstruction>()

        // Add departure instruction
        instructions.add(
            NavigationInstruction(
                text = NavigationInstructionType.DEPART.toGermanText(),
                distance = 0.0,
                duration = 0.0,
                type = NavigationInstructionType.DEPART,
                index = 0
            )
        )

        // If we have a turn point in our L-shaped route
        if (points.size > 2) {
            // Calculate the distance to the turn point
            val distanceToTurn = calculateRouteDistance(points.subList(0, 2))

            // Add turn instruction
            instructions.add(
                NavigationInstruction(
                    text = if (MapUtils.isTurnLeft(
                            points[0].latitude, points[0].longitude,
                            points[1].latitude, points[1].longitude,
                            points[2].latitude, points[2].longitude
                        )) NavigationInstructionType.TURN_LEFT.toGermanText()
                    else NavigationInstructionType.TURN_RIGHT.toGermanText(),
                    distance = distanceToTurn,
                    duration = distanceToTurn / 1.4, // Assuming 1.4 m/s walking speed
                    type = if (MapUtils.isTurnLeft(
                            points[0].latitude, points[0].longitude,
                            points[1].latitude, points[1].longitude,
                            points[2].latitude, points[2].longitude
                        )) NavigationInstructionType.TURN_LEFT
                    else NavigationInstructionType.TURN_RIGHT,
                    index = 1
                )
            )
        }

        // Add arrival instruction
        instructions.add(
            NavigationInstruction(
                text = NavigationInstructionType.ARRIVE.toGermanText(),
                distance = distance,
                duration = distance / 1.4, // Assuming 1.4 m/s walking speed
                type = NavigationInstructionType.ARRIVE,
                index = points.size - 1
            )
        )

        return RouteData(
            points = points,
            distance = distance,
            duration = distance / 1.4, // Assuming 1.4 m/s walking speed
            instructions = instructions,
            status = RouteStatus.SUCCESS
        )
    }

    /**
     * Create an L-shaped route between two points
     * This creates a more natural path than a straight line
     */
    private fun createLShapedRoute(startPoint: GeoPoint, endPoint: GeoPoint): List<GeoPoint> {
        // Create an intermediate point to form an L-shape
        val midPoint = GeoPoint(endPoint.latitude, startPoint.longitude)

        // Calculate distances
        val distanceToMid = startPoint.distanceToAsDouble(midPoint)
        val distanceFromMidToEnd = midPoint.distanceToAsDouble(endPoint)

        // If the points are already aligned (north-south or east-west),
        // just return a direct route
        if (distanceToMid < 10 || distanceFromMidToEnd < 10) {
            return listOf(startPoint, endPoint)
        }

        return listOf(startPoint, midPoint, endPoint)
    }

    /**
     * Calculate the total distance of a route
     */
    private fun calculateRouteDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0

        for (i in 0 until points.size - 1) {
            totalDistance += points[i].distanceToAsDouble(points[i + 1])
        }

        return totalDistance
    }

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get the current instruction based on user location
     */
    fun getCurrentInstruction(
        userLocation: LocationDataOSRM,
        route: RouteData,
        destinationLocation: LocationDataOSRM
    ): NavigationInstruction {
        // If we have no route, return a default instruction
        if (route.points.isEmpty() || route.instructions.isEmpty()) {
            return NavigationInstruction(
                text = "Folgen Sie der Route",
                distance = LocationDataOSRM.distanceBetween(userLocation, destinationLocation),
                duration = 0.0,
                type = NavigationInstructionType.GO_STRAIGHT
            )
        }

        // Convert user location to GeoPoint
        val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

        // Find the closest point on the route
        var closestPointIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in route.points.indices) {
            val distance = userPoint.distanceToAsDouble(route.points[i])
            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = i
            }
        }

        // Check if we're near the destination
        val distanceToDest = LocationDataOSRM.distanceBetween(userLocation, destinationLocation)
        if (distanceToDest < 30) {
            return NavigationInstruction(
                text = NavigationInstructionType.ARRIVE.toGermanText(),
                distance = 0.0,
                duration = 0.0,
                type = NavigationInstructionType.ARRIVE
            )
        }

        // Find the next instruction based on our position in the route
        for (i in route.instructions.indices) {
            val instruction = route.instructions[i]
            val instructionIndex = instruction.index ?: continue

            if (instructionIndex > closestPointIndex) {
                // Calculate distance to this instruction
                var distanceToInstruction = 0.0
                for (j in closestPointIndex until instructionIndex) {
                    distanceToInstruction += route.points[j].distanceToAsDouble(route.points[j + 1])
                }

                // Return this instruction with updated distance
                return instruction.copy(
                    distance = distanceToInstruction,
                    duration = distanceToInstruction / 1.4 // Assuming 1.4 m/s walking speed
                )
            }
        }

        // If we're past all instructions but not at destination, return continue instruction
        return NavigationInstruction(
            text = NavigationInstructionType.CONTINUE.toGermanText(),
            distance = distanceToDest,
            duration = distanceToDest / 1.4, // Assuming 1.4 m/s walking speed
            type = NavigationInstructionType.CONTINUE
        )
    }
}