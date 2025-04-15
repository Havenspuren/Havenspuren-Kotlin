package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.Map.StableRouteMapComponent
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourDetailScreen(
    tourId: String,
    toursViewModel: ToursViewModel,
    locationViewModel: LocationViewModel,
    onBackClick: () -> Unit,
    onOverviewClick: (String) -> Unit,
    onStartClick: (String) -> Unit
) {
    val location by locationViewModel.location

    // State to hold the loaded tour data
    var tourWithProgress by remember { mutableStateOf<TourWithProgress?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tourWithLocations by remember { mutableStateOf<TourWithLocations?>(null) }

    // Load tour data when the screen is first composed
    LaunchedEffect(tourId) {
        toursViewModel.getTourWithProgressAndLocations(tourId) { progress, locations ->
            tourWithProgress = progress
            tourWithLocations = locations  // Store the locations in state
            isLoading = false
            if (progress == null && locations == null) {
                errorMessage = "Failed to load tour details"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tourWithProgress?.tour?.title ?: "Loading...",
                        color = Color.White ,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
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
            when {
                isLoading -> {
                    // Show loading indicator
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                errorMessage != null -> {
                    // Show error message
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "An unknown error occurred",
                            color = Color.White,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                toursViewModel.getTourWithProgress(tourId) { result ->
                                    if (result != null) {
                                        tourWithProgress = result
                                        isLoading = false
                                    } else {
                                        errorMessage = "Failed to load tour details"
                                        isLoading = false
                                    }
                                }
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }

                tourWithProgress != null -> {
                    // Show tour details
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Action Cards
                        ActionCard(
                            title = "Überblick",
                            icon = Icons.Default.MenuBook,
                            onClick = { onOverviewClick(tourId) }
                        )

                        ActionCard(
                            title = "Starten die Reise",
                            icon = Icons.Default.LocationOn,
                            onClick = { onStartClick(tourId) }
                        )

                        // Map Component
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Use the tour's locations for the map component
                                if (tourWithProgress?.tour != null) {
                                    // We'll assume the TourWithLocations relationship has been established elsewhere
                                    // and that tourLocations would be available either in the ViewModel or through another method
                                    // For now, we'll simulate with a placeholder until you confirm how your data is structured

                                    // This is where we would pass the list of locations from the tour to the StableRouteMapComponent
                                    // We'll assume locations are available on your tour object
                                    StableRouteMapComponent(
                                        userLocation = location,
                                        destinationLocations = tourWithLocations?.locations?.map {
                                            LocationData(
                                                latitude = it.latitude,
                                                longitude = it.longitude
                                            )
                                        } ?: emptyList()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryColor
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}