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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToursMainScreen(
    onOpenDrawer: () -> Unit,
    onTourSelected: (String) -> Unit,
    onTourInfo: (String) -> Unit
) {
    // Sample tour data - replace with your actual data source
    val tours = listOf(
        Tour(
            id = "1",
            title = "Hafenarbeiter",
            progress = 7,
            imageResId = R.drawable.ic_launcher_foreground // Replace with actual image
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
            // Tour cards list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tours) { tour ->
                    TourCard(
                        tour = tour,
                        onContinueClick = { onTourSelected(tour.id) },
                        onInfoClick = { onTourInfo(tour.id) }
                    )
                }

                // Add some space at the bottom
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun TourCard(
    tour: Tour,
    onContinueClick: () -> Unit,
    onInfoClick: () -> Unit
) {
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
                    progress = tour.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Fortschritt: ${tour.progress}%",
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
                            text = if (tour.progress > 0) "FORTSETZEN" else "STARTEN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1, // Prevent text wrapping
                            overflow = TextOverflow.Visible // Ensure text doesn't get cut off with ellipsis
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
                // If you have actual images, replace this with your image resources
                Image(
                    painter = painterResource(id = tour.imageResId ?: R.drawable.ic_launcher_foreground),
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