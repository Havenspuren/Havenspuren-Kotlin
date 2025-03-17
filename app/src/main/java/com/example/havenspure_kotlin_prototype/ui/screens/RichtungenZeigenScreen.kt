package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.Map.DirectionMapComponent
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

/**
 * RichtungenZeigenScreen displays a turn-by-turn navigation interface.
 * This screen provides directions to help the user find a tour location.
 *
 * @param tour The tour to navigate to
 * @param onBackClick Callback for the back button
 * @param locationViewModel ViewModel providing user location updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichtungenZeigenScreen(
    tour: Tour,
    onBackClick: () -> Unit,
    locationViewModel: LocationViewModel
) {
    // Get current user location from the ViewModel
    val location by locationViewModel.location

    // Ensure the tour has a location
    val tourLocation = tour.location ?: LocationData(53.5142, 8.1428) // Default to Wilhelmshaven harbor if null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation zu ${tour.title}", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Zurück",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
        ) {
            // The DirectionMapComponent now handles all UI elements
            // This includes the direction panel, distance indicators, and navigation controls
            DirectionMapComponent(
                userLocation = location,
                destinationLocation = tourLocation,
                destinationName = "Hafen",
                onBackPress = {}
            )
        }
    }
}