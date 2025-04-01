package com.example.havenspure_kotlin_prototype.OSRM.data.models

/**
 * Represents geographic coordinates for a location.
 * Used for tracking user location and destinations.
 *
 * @property latitude The latitude coordinate
 * @property longitude The longitude coordinate
 * @property bearing Optional bearing/direction in degrees (0-360), where 0 is north
 */
data class LocationDataOSRM(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null
) {
    companion object {
        // Default location (Wilhelmshaven harbor)
        val DEFAULT = LocationDataOSRM(53.5142, 8.1428)

        // Check if location coordinates are valid
        fun isValid(location: LocationDataOSRM?): Boolean {
            return location != null &&
                    location.latitude >= -90 && location.latitude <= 90 &&
                    location.longitude >= -180 && location.longitude <= 180
        }

        // Calculate distance between two locations in meters
        fun distanceBetween(start: LocationDataOSRM, end: LocationDataOSRM): Double {
            val earthRadius = 6371000.0 // Earth radius in meters

            val lat1Rad = Math.toRadians(start.latitude)
            val lat2Rad = Math.toRadians(end.latitude)
            val lon1Rad = Math.toRadians(start.longitude)
            val lon2Rad = Math.toRadians(end.longitude)

            val dLat = lat2Rad - lat1Rad
            val dLon = lon2Rad - lon1Rad

            val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                    Math.sin(dLon/2) * Math.sin(dLon/2)

            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

            return earthRadius * c
        }
    }
}