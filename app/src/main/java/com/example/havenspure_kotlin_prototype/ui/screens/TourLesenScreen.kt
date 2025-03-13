package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

/**
 * TourLesenScreen displays the readable content of a tour.
 *
 * @param tour The tour object containing all details to display
 * @param onBackClick Callback for when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourLesenScreen(
    tour: Tour,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {  Text("${tour.title} - Lesen", color = Color.White)  },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tour title and subtitle
            TourHeader(tour)

            // Main content
            TourContent(tour)

            // Additional information
            AdditionalInfo(tour)

            // Bottom space for comfortable scrolling
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TourHeader(tour: Tour) {
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
                text = tour.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

        }
    }
}

@Composable
private fun TourContent(tour: Tour) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Display the main content text
            // If tour has no description, display a placeholder text
            Text(
                text = tour.description.takeIf { it.isNotBlank() }
                    ?: ("Diese Tour beschreibt den geschichtsträchtigen ${tour.title} in Wilhelmshaven. " +
                            "Der Ort ist bekannt für seine maritime Bedeutung und bietet einen Einblick in die lokale Geschichte. " +
                            "Während Ihres Besuchs werden Sie mehr über die historische und kulturelle Bedeutung dieses Ortes erfahren."),
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun AdditionalInfo(tour: Tour) {
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
                text = "Über diese Tour",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Display tour progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fortschritt:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))

                LinearProgressIndicator(
                    progress = tour.progress / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = PrimaryColor,
                    trackColor = PrimaryColor.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${tour.progress}%",
                    fontSize = 14.sp
                )
            }

            // Location information if available
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Addresse:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tour.address,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }
    }
}