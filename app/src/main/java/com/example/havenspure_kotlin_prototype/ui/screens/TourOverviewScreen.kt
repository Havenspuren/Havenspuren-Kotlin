package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

/**
 * TourOverviewScreen displays an overview of a tour with its locations.
 *
 * @param tourId The ID of the tour to display
 * @param toursViewModel ViewModel to fetch tour data
 * @param onBackClick Callback for when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourOverviewScreen(
    tourId: String,
    toursViewModel: ToursViewModel,
    onBackClick: () -> Unit
) {
    // State for tour data
    var tourWithProgress by remember { mutableStateOf<TourWithProgress?>(null) }
    var tourWithLocations by remember { mutableStateOf<TourWithLocations?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch tour data when the screen is first composed
    LaunchedEffect(tourId) {
        toursViewModel.getTourWithProgressAndLocations(tourId) { progress, locations ->
            tourWithProgress = progress
            tourWithLocations = locations
            isLoading = false
            if (progress == null && locations == null) {
                errorMessage = "Could not load tour data"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tourWithProgress?.tour?.title ?: "Tour Overview",
                        color = Color.White,
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
                    LoadingState(Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    ErrorState(errorMessage!!, Modifier.align(Alignment.Center))
                }
                tourWithProgress != null && tourWithLocations != null -> {
                    TourContent(
                        tourWithProgress = tourWithProgress!!,
                        tourWithLocations = tourWithLocations!!
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = PrimaryColor)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading tour information...",
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TourContent(
    tourWithProgress: TourWithProgress,
    tourWithLocations: TourWithLocations
) {
    val tour = tourWithProgress.tour
    val locations = tourWithLocations.locations.sortedBy { it.order }

    Box(modifier = Modifier.fillMaxSize()) {
        val lazyListState = rememberLazyListState()
        var showScrollToTopButton by remember { mutableStateOf(false) }

        // Check if we should show the scroll button based on scroll position
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .collect { index ->
                    showScrollToTopButton = index > 0
                }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tour title card
            item {
                TourTitleCard(tour.title, tour.description)
            }

            // Tour progress
            item {
                TourProgressCard(tourWithProgress)
            }

            // Locations list header
            item {
                Text(
                    text = "Locations",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Locations list
            if (locations.isNotEmpty()) {
                items(locations) { location ->
                    LocationCard(location)
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No locations found for this tour",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Add some space at the bottom for better scrolling
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Scrollbar indicator that appears during scrolling
        AnimatedVisibility(
            visible = showScrollToTopButton,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            val coroutineScope = rememberCoroutineScope()
            FloatingActionButton(
                onClick = {
                    // Scroll to top when button is clicked
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                containerColor = PrimaryColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Scroll to top",
                    modifier = Modifier.rotate(90f)
                )
            }
        }
    }
}

@Composable
private fun TourTitleCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryColor.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun TourProgressCard(tourWithProgress: TourWithProgress) {
    val progress = tourWithProgress.userProgress?.completionPercentage ?: 0f
    val progressPercentage = (progress * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Tour Progress",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Completion:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = PrimaryColor,
                    trackColor = PrimaryColor.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$progressPercentage%",
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LocationCard(location: Location) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location icon
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Location",
                tint = PrimaryColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Location information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = location.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (location.bubbleText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = location.bubbleText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Order number
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = PrimaryColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${location.order}",
                    color = PrimaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}