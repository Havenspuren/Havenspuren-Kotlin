package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.ViewModels.ToursUiState
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.ui.components.TourInfoDialog
import com.example.havenspure_kotlin_prototype.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToursMainScreen(
    uiState: ToursUiState,
    onOpenDrawer: () -> Unit,
    onTourSelected: (String) -> Unit,
    toursViewModel: ToursViewModel // Pass the ViewModel directly
) {
    // Dialog states
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedTourId by remember { mutableStateOf("") }

    // States to hold async loaded data for the dialog
    var selectedTourWithProgress by remember { mutableStateOf<TourWithProgress?>(null) }
    var selectedTourWithLocations by remember { mutableStateOf<TourWithLocations?>(null) }
    var visitedLocationIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // Effect to load data when selectedTourId changes
    LaunchedEffect(selectedTourId) {
        if (selectedTourId.isNotEmpty() && showInfoDialog) {
            // Load data for the dialog
            toursViewModel.getTourWithProgressAndLocations(selectedTourId) { progress, locations ->
                selectedTourWithProgress = progress
                selectedTourWithLocations = locations

                // Here you would also load visited location IDs
                // For example (you'll need to implement this method):
                // userProgressRepository.getVisitedLocationIds(selectedTourId) { ids ->
                //     visitedLocationIds = ids
                // }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Touren Überblick",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Menu",
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
            when (uiState) {
                is ToursUiState.Loading -> {
                    // Show loading indicator
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                is ToursUiState.Empty -> {
                    // Show empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Keine Touren verfügbar",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }

                is ToursUiState.Success -> {
                    // Tour cards list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.tours) { tourWithProgress ->
                            TourCard(
                                tourWithProgress = tourWithProgress,
                                onContinueClick = { onTourSelected(tourWithProgress.tour.id) },
                                onInfoClick = {
                                    // Handle info click internally
                                    selectedTourId = tourWithProgress.tour.id
                                    selectedTourWithProgress = tourWithProgress // Store immediately available data
                                    showInfoDialog = true
                                }
                            )
                        }

                        // Add some space at the bottom
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Display the dialog when showInfoDialog is true and we have necessary data
                    if (showInfoDialog && selectedTourWithProgress != null && selectedTourWithLocations != null) {
                        TourInfoDialog(
                            tourWithProgress = selectedTourWithProgress!!,
                            tourWithLocations = selectedTourWithLocations!!,
                            visitedLocationIds = visitedLocationIds,
                            onDismiss = {
                                showInfoDialog = false
                                // Clear data when dialog is closed
                                selectedTourId = ""
                                selectedTourWithProgress = null
                                selectedTourWithLocations = null
                                visitedLocationIds = emptyList()
                            }
                        )
                    } else if (showInfoDialog) {
                        // Show loading dialog while data is being fetched
                        AlertDialog(
                            onDismissRequest = { showInfoDialog = false },
                            title = { Text("Laden...") },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = PrimaryColor)
                                    Text("Tourdaten werden geladen")
                                }
                            },
                            confirmButton = {}
                        )
                    }
                }

                is ToursUiState.Error -> {
                    // Show error state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Fehler: ${uiState.message}",
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { toursViewModel.refreshTours() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = TextDark
                            )
                        ) {
                            Text("Erneut versuchen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TourCard(
    tourWithProgress: TourWithProgress,
    onContinueClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val tour = tourWithProgress.tour
    val progress = tourWithProgress.userProgress
    val progressPercentage = progress?.completionPercentage?.toInt() ?: 0

    // Load image from resources
    val context = LocalContext.current
    val imageResId = context.resources.getIdentifier(
        tour.imageUrl.removeSuffix(".jpg"),
        "drawable",
        context.packageName
    )

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Title and progress
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = tour.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = progressPercentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Fortschritt: ${progressPercentage}%",
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Info button
                    OutlinedButton(
                        onClick = onInfoClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White
                        ),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 100.dp)
                    ) {
                        Text(
                            text = "INFO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Continue/Start button
                    Button(
                        onClick = onContinueClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = TextDark
                        ),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 140.dp)
                    ) {
                        Text(
                            text = if (progressPercentage > 0) "FORTSETZEN" else "STARTEN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1, // Prevent text wrapping
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }

            // Right side: Tour image
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = if (imageResId != 0) imageResId else R.drawable.ic_launcher_foreground),
                    contentDescription = tour.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}