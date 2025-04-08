package com.example.havenspure_kotlin_prototype.models

import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.R
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// Data class to represent a tour
@Parcelize
data class Tour(
    val id: String,
    val title: String,
    val description: String = "",
    val address: String = "",
    val location: @RawValue LocationData? = null,
    val audioResourceId: Int? = null,
    val imageResId: Int? = null,
    val progress: Int = 0
) : Parcelable

// Sample tour data - replace with your actual data source
val tours = listOf(
    Tour(
        id = "1",
        title = "Hafen",
        progress = 7,
        imageResId = R.drawable.tour_harbour // Replace with actual image
        , location = LocationData(53.5142, 8.1428),
        address = "Hafenstraße 1, 12345 Hafenstadt"
    ),
    Tour(
        id = "2",
        title = "Bewegungstour",
        progress = 0,
        imageResId = R.drawable.ic_launcher_foreground // Replace with actual image
    ),
    Tour(
        id = "3",
        title = "Helen von Wedel",
        progress = 0,
        imageResId = R.drawable.ic_launcher_foreground // Replace with actual image
    )
)