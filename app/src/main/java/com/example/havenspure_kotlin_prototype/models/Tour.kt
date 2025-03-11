package com.example.havenspure_kotlin_prototype.models

import com.example.havenspure_kotlin_prototype.Data.LocationData

data class Tour(
    val id: String,
    val title: String,
    val description: String = "",
    val address: String = "",
    val location: LocationData? = null,
    val audioResourceId: Int? = null,
    val imageResId: Int? = null,
    val progress: Int = 0
)