package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationState

/**
 * A component that displays current direction information.
 *
 * @param currentLocation The current target location
 * @param formattedDistance Formatted distance to the current location
 * @param navigationState Current navigation state
 * @param navigationInstruction Current navigation instruction from the routing system
 * @param remainingDistance Remaining distance to destination from the routing system
 */
@Composable
fun DirectionPanel(
    currentLocation: Location?,
    formattedDistance: String,
    navigationState: TourNavigationState,
    navigationInstruction: String,
    remainingDistance: String
) {
    Surface(
        color = Color(0xFF009688),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Location info in one line
            currentLocation?.let { location ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${location.order}. ${location.name} - $formattedDistance",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Direction text
            Text(
                text = if (navigationState == TourNavigationState.AtLocation)
                    "Sie haben Ihr Ziel erreicht"
                else navigationInstruction,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Distance remaining
            Text(
                text = remainingDistance,
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}