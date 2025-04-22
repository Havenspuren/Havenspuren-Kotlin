package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator

/**
 * A component that displays the tour progress information.
 *
 * @param tourNavigator The tour navigator containing tour progress data
 * @param visitedLocationsCount Number of locations already visited
 * @param totalLocationsCount Total number of locations in the tour
 */
@Composable
fun TourProgressIndicator(
    tourNavigator: TourNavigator,
    visitedLocationsCount: Int,
    totalLocationsCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0F7FA))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Standort ${tourNavigator.getCurrentLocationIndex() + 1} von $totalLocationsCount",
            fontSize = 16.sp,
            color = Color.Black
        )

        Text(
            text = "${((visitedLocationsCount.toFloat() / totalLocationsCount) * 100).toInt()}% besucht",
            fontSize = 16.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }
}