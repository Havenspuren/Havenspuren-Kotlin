package com.example.havenspure_kotlin_prototype.OSRM.data.remote

import com.h2o.store.data.models.OSRMResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface for OpenStreetMap Routing Machine (OSRM) API
 * This service provides routing capabilities using the OSRM backend
 */
interface OSRMApiService {

    /**
     * Get a route between two points using the bike profile
     *
     * @param coordinates Formatted as {longitude},{latitude};{longitude},{latitude}
     * @param alternatives Whether to return alternative routes (true/false)
     * @param steps Whether to return step-by-step instructions (true/false)
     * @param geometries The format of the returned geometry (polyline/geojson)
     * @param overview Whether to return an overview geometry (full/simplified/false)
     * @return OSRM route response
     */
    @GET("route/v1/bike/{coordinates}")
    suspend fun getRouteBike(
        @Path("coordinates") coordinates: String,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("steps") steps: Boolean = true,
        @Query("geometries") geometries: String = "polyline",
        @Query("overview") overview: String = "full"
    ): Response<OSRMResponse>

    /**
     * Get a route between two points using the foot profile
     *
     * @param coordinates Formatted as {longitude},{latitude};{longitude},{latitude}
     * @param alternatives Whether to return alternative routes (true/false)
     * @param steps Whether to return step-by-step instructions (true/false)
     * @param geometries The format of the returned geometry (polyline/geojson)
     * @param overview Whether to return an overview geometry (full/simplified/false)
     * @return OSRM route response
     */
    @GET("route/v1/foot/{coordinates}")
    suspend fun getRouteFoot(
        @Path("coordinates") coordinates: String,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("steps") steps: Boolean = true,
        @Query("geometries") geometries: String = "polyline",
        @Query("overview") overview: String = "full"
    ): Response<OSRMResponse>

    companion object {
        // OSRM public servers (in order of preference)
        val OSRM_ENDPOINTS = listOf(
            "https://routing.openstreetmap.de/routed-bike/", // Primary bike endpoint
            "https://router.project-osrm.org/",               // Official demo endpoint
            "http://router.project-osrm.org/"                 // Non-https fallback
        )

        // Default primary server
        const val BASE_URL = "https://routing.openstreetmap.de/routed-bike/"

        /**
         * Format coordinates for OSRM API
         * OSRM expects longitude,latitude (not latitude,longitude)
         */
        fun formatCoordinates(
            startLat: Double,
            startLon: Double,
            endLat: Double,
            endLon: Double
        ): String {
            return "$startLon,$startLat;$endLon,$endLat"
        }
    }
}