// File: TourProgressIndicator.kt
// This is a new component file

package com.example.havenspure_kotlin_prototype.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor

@Composable
fun TourProgressIndicator(
    currentLocationIndex: Int,
    totalLocations: Int,
    visitedLocations: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location counter text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Standort ${currentLocationIndex + 1} von $totalLocations",
                fontSize = 14.sp,
                color = Color.White
            )

            Text(
                text = "${(visitedLocations.toFloat() / totalLocations * 100).toInt()}% besucht",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { visitedLocations.toFloat() / totalLocations },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }
}