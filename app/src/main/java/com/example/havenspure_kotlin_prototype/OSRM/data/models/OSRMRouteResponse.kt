package com.h2o.store.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data models for OSRM API responses
 */
data class OSRMResponse(
    val code: String,
    val message: String?,
    val routes: List<OSRMRoute>?,
    val waypoints: List<OSRMWaypoint>?
)

data class OSRMRoute(
    val distance: Double,
    val duration: Double,
    val geometry: String,
    val legs: List<OSRMLeg>,
    @SerializedName("weight_name")
    val weightName: String,
    val weight: Double
)

data class OSRMLeg(
    val distance: Double,
    val duration: Double,
    val steps: List<OSRMStep>,
    val summary: String,
    val weight: Double
)

data class OSRMStep(
    val distance: Double,
    val duration: Double,
    val geometry: String,
    val maneuver: OSRMManeuver,
    val name: String,
    val weight: Double,
    val mode: String,
    val intersections: List<OSRMIntersection>?
)

data class OSRMManeuver(
    @SerializedName("bearing_after")
    val bearingAfter: Int,
    @SerializedName("bearing_before")
    val bearingBefore: Int,
    val location: List<Double>,
    val type: String,
    val modifier: String?
)

data class OSRMIntersection(
    val location: List<Double>,
    val bearings: List<Int>,
    val entry: List<Boolean>,
    val out: Int?,
    val in_: Int?
)

data class OSRMWaypoint(
    val hint: String,
    val distance: Double,
    val name: String,
    val location: List<Double>
)