package com.example.havenspure_kotlin_prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

@Composable
fun TourInfoDialog(
    tourWithProgress: TourWithProgress,
    tourWithLocations: TourWithLocations,
    visitedLocationIds: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog header
                DialogHeader(tourWithProgress, onDismiss)

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar
                ProgressSection(tourWithProgress)

                Spacer(modifier = Modifier.height(16.dp))

                // Locations list with visited status
                LocationsList(
                    locations = tourWithLocations.locations,
                    visitedLocationIds = visitedLocationIds
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor
                    )
                ) {
                    Text(
                        text = "Schließen",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    tourWithProgress: TourWithProgress,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Tour Information",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryColor
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Schließen",
                tint = Color.Gray
            )
        }
    }

    Text(
        text = tourWithProgress.tour.title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ProgressSection(tourWithProgress: TourWithProgress) {
    val progress = tourWithProgress.userProgress?.completionPercentage ?: 0f
    val progressPercentage = (progress * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fortschritt",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = PrimaryColor,
            trackColor = PrimaryColor.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$progressPercentage% abgeschlossen",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun LocationsList(
    locations: List<Location>,
    visitedLocationIds: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Standorte",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sort locations by order
        val sortedLocations = locations.sortedBy { it.order }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedLocations) { location ->
                LocationItem(
                    location = location,
                    isVisited = visitedLocationIds.contains(location.id)
                )
            }
        }
    }
}

@Composable
private fun LocationItem(
    location: Location,
    isVisited: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isVisited) PrimaryColor.copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon (visited or not)
        Icon(
            imageVector = if (isVisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isVisited) "Besucht" else "Nicht besucht",
            tint = if (isVisited) PrimaryColor else Color.Gray,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Location details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                fontWeight = if (isVisited) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp
            )

            if (location.bubbleText.isNotBlank()) {
                Text(
                    text = location.bubbleText,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }

        // Location order
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (isVisited) PrimaryColor else Color.LightGray,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${location.order}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}