package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import com.example.havenspure_kotlin_prototype.OSRM.data.models.RouteData
import com.example.havenspure_kotlin_prototype.OSRM.data.remote.MapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.lang.Exception

/**
 * Manages route calculation, display and navigation
 */
class RouteManager(
    private val context: Context,
    private val markerManager: MarkerManager,
    private val mapRepository: MapRepository
) {
    private val TAG = "RouteManager"

    // Keep track of current route
    private var currentRoute: RouteData? = null

    // Color defaults
    private var routeColor: Int = android.graphics.Color.BLUE
    private var userMarkerColor: Int = android.graphics.Color.BLUE
    private var destinationMarkerColor: Int = android.graphics.Color.RED

    /**
     * Set colors for route visualization
     */
    fun setColors(
        routeColor: Int,
        userMarkerColor: Int,
        destinationMarkerColor: Int
    ) {
        this.routeColor = routeColor
        this.userMarkerColor = userMarkerColor
        this.destinationMarkerColor = destinationMarkerColor
    }

    /**
     * Calculate and display a route between two points
     *
     * @param mapView Map view to display route on
     * @param startPoint Starting point
     * @param endPoint Destination point
     * @param callback Callback for route calculation result
     */

    fun calculateRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        callback: (success: Boolean, error: String?, route: RouteData?) -> Unit
    ) {
        try {
            // Show simplified route immediately for better UX
            displaySimplifiedRoute(mapView, startPoint, endPoint)

            // Calculate actual route in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val route = mapRepository.getRoute(startPoint, endPoint)

                    // Store route
                    currentRoute = route

                    // Update display on main thread
                    withContext(Dispatchers.Main) {
                        // Update markers and route on map
                        markerManager.updateUserLocationMarker(mapView, startPoint, userMarkerColor)
                        markerManager.updateDestinationMarker(mapView, endPoint, destinationMarkerColor)
                        markerManager.drawRoute(mapView, route.points, routeColor)

                        // Notify success
                        callback(true, null, route)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating route: ${e.message}")

                    withContext(Dispatchers.Main) {
                        // Notify error but keep simplified route
                        callback(false, e.message ?: "Unknown error", null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateRoute: ${e.message}")
            callback(false, e.message ?: "Unknown error", null)
        }
    }



    /**
     * Display a simplified route immediately while waiting for actual route calculation
     */
    private fun displaySimplifiedRoute(
        mapView: MapView,
        startPoint: GeoPoint,
        endPoint: GeoPoint
    ) {
        try {
            // Create L-shaped route for simplicity
            val midPoint = GeoPoint(endPoint.latitude, startPoint.longitude)

            // Check if points are almost aligned (no need for L-shape)
            val routePoints = if (Math.abs(startPoint.longitude - endPoint.longitude) < 0.0001 ||
                Math.abs(startPoint.latitude - endPoint.latitude) < 0.0001) {
                listOf(startPoint, endPoint)
            } else {
                listOf(startPoint, midPoint, endPoint)
            }

            // Update markers and draw route
            markerManager.updateUserLocationMarker(mapView, startPoint, userMarkerColor)
            markerManager.updateDestinationMarker(mapView, endPoint, destinationMarkerColor)
            markerManager.drawRoute(mapView, routePoints, routeColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying simplified route: ${e.message}")
        }
    }

    /**
     * Update user marker position on the map
     *
     * @param mapView Map view to update
     * @param position User's new position
     * @param bearing Optional bearing (direction) of user
     * @return True if map was updated successfully
     */
    fun updateUserMarker(
        mapView: MapView,
        position: GeoPoint,
        bearing: Float? = null
    ): Boolean {
        return try {
            markerManager.updateUserLocationMarker(
                mapView,
                position,
                userMarkerColor,
                bearing
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user marker: ${e.message}")
            false
        }
    }

    /**
     * Get navigation instruction based on current location
     *
     * @param userLocation Current user location
     * @param destinationLocation Destination location
     * @return German instruction text
     */
    fun getNavigationInstruction(
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM
    ): String {
        return try {
            val route = currentRoute

            if (route == null || route.points.isEmpty()) {
                val distance = LocationDataOSRM.distanceBetween(userLocation, destinationLocation)
                return "Folgen Sie der Route (${MapUtils.formatDistanceAbsolute(distance)})"
            }

            MapUtils.getNavigationInstruction(
                userLocation,
                route.points,
                destinationLocation
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting navigation instruction: ${e.message}")
            "Folgen Sie der Route"
        }
    }

    /**
     * Get remaining distance to destination
     *
     * @param userLocation Current user location
     * @param destinationLocation Destination location
     * @return Formatted distance string
     */
    fun getRemainingDistance(
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM
    ): String {
        return try {
            val route = currentRoute

            val distance = if (route != null && route.points.isNotEmpty()) {
                MapUtils.calculateRemainingDistance(userLocation, route.points)
            } else {
                LocationDataOSRM.distanceBetween(userLocation, destinationLocation)
            }

            "Entfernung: ${MapUtils.formatDistanceAbsolute(distance)}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting remaining distance: ${e.message}")
            "Entfernung wird berechnet..."
        }
    }

    /**
     * Check if rerouting is needed based on distance from route
     *
     * @param userLocation Current user location
     * @param maxDistanceFromRoute Maximum allowed distance from route
     * @return True if rerouting is needed
     */
    fun isReroutingNeeded(
        userLocation: LocationDataOSRM,
        maxDistanceFromRoute: Double = 30.0
    ): Boolean {
        val route = currentRoute ?: return false

        return MapUtils.isOffRoute(
            userLocation,
            route.points,
            maxDistanceFromRoute
        )
    }

    /**
     * Clear the current route
     */
    fun clearRoute() {
        currentRoute = null
    }

    /**
     * Restore route visualization from saved route data
     *
     * @param mapView Map view to display route on
     * @param routeData Previously calculated route
     * @param userPoint Current user location
     * @param destPoint Destination point
     * @param currentBearing Current bearing from NavigationManager (optional)
     * @return True if restoration was successful
     */
    fun restoreRouteVisualization(
        mapView: MapView,
        routeData: RouteData,
        userPoint: GeoPoint,
        destPoint: GeoPoint,
        currentBearing: Float? = null
    ): Boolean {
        try {
            // Store route
            currentRoute = routeData

            // Clear any existing overlays to prevent duplicates
            markerManager.clearRouteOverlays(mapView)

            // Update markers first
            markerManager.updateUserLocationMarker(mapView, userPoint, userMarkerColor, currentBearing)
            markerManager.updateDestinationMarker(mapView, destPoint, destinationMarkerColor)

            // Draw route
            markerManager.drawRoute(mapView, routeData.points, routeColor)

            // Force redraw
            mapView.invalidate()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring route visualization: ${e.message}")
            return false
        }
    }
}