package com.example.havenspure_kotlin_prototype.ui.screens
/*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.Map.offline.MapInitializer
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OfflineRichtungenZeigenScreen displays a turn-by-turn navigation interface using offline maps.
 * This screen provides directions to help the user find a tour location without requiring internet.
 *
 * @param tour The tour to navigate to
 * @param onBackClick Callback for the back button
 * @param locationViewModel ViewModel providing user location updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineRichtungenZeigenScreen(
    tour: Tour,
    onBackClick: () -> Unit,
    locationViewModel: LocationViewModel
) {
    // Get current user location from the ViewModel
    val location by locationViewModel.location
    val context = LocalContext.current

    // Ensure the tour has a location
    val tourLocation = tour.location ?: LocationData(53.5142, 8.1428) // Default to Wilhelmshaven harbor if null

    // Initialize map system
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            MapInitializer(context).initialize()
        }
    }

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
            // Use the simplified direction map component
            SimplifiedDirectionMapComponent(
                userLocation = location,
                destinationLocation = tourLocation,
                destinationName = tour.title,
                onBackPress = onBackClick
            )
        }
    }
}

 */
