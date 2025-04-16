package com.example.havenspure_kotlin_prototype.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocationData(
    val latitude: Double,
    val longitude: Double
) : Parcelable
{

    companion object {
        // Calculate distance between two locations in meters
        fun distanceBetween(location1: LocationData, location2: LocationData): Float {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                location1.latitude, location1.longitude,
                location2.latitude, location2.longitude,
                results
            )
            return results[0]
        }
    }

    // Convert to OSRM location format
    fun toOSRM(): com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM {
        return com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM(
            latitude = this.latitude,
            longitude = this.longitude
        )
    }
}



