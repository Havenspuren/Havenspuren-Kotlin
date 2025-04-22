package com.example.havenspure_kotlin_prototype.OSRM.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.data.LocationData
import com.example.havenspure_kotlin_prototype.data.model.Location
// Import the LocationData type that comes from locationViewModel
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationState
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator
import org.osmdroid.views.MapView
/**
 * Navigation buttons component that displays different buttons based on the current navigation state.
 *
 * @param navigationState Current navigation state
 * @param isNearLocation Whether the user is near the target location
 * @param userLocationState Current user location
 * @param currentLocation Current target location
 * @param tourNavigator Tour navigator instance
 * @param hasPlayedAudio Whether audio has been played for the current location
 * @param onMarkLocationVisited Callback when marking location as visited
 * @param onProceedToNextLocation Callback when proceeding to next location
 * @param onResetAudioFlag Callback to reset audio flag
 */
@Composable
fun NavigationButtons(
    navigationState: TourNavigationState,
    isNearLocation: Boolean,
    userLocationState: LocationData?,
    currentLocation: Location?,
    tourNavigator: TourNavigator,
    hasPlayedAudio: Boolean,
    onMarkLocationVisited: () -> Unit,
    onProceedToNextLocation: () -> Unit,
    onResetAudioFlag: () -> Unit,
    mapViewModel: MapViewModel, // Add this parameter
    mapView: MapView, // Add this parameter,
    onStartNewNavigation: () -> Unit // Add this callback
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Show the current distance to location when en route
        if (navigationState == TourNavigationState.EnRoute && !isNearLocation && userLocationState != null) {
            // Show distance indicator
            Card(
                modifier = Modifier
                    .padding(bottom = 90.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xAA000000)
                )
            ) {
                Text(
                    text = if (userLocationState != null && currentLocation != null) {
                        val distance = tourNavigator.getDistanceToCurrentLocation(
                            userLocationState.latitude,
                            userLocationState.longitude
                        )
                        "Noch ${tourNavigator.formatDistance(distance)} bis zum Ziel"
                    } else "Entfernung wird berechnet...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (isNearLocation || navigationState == TourNavigationState.AtLocation) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Show "Mark as arrived" button when near but not yet marked
                if (isNearLocation && navigationState == TourNavigationState.EnRoute) {
                    Button(
                        onClick = onMarkLocationVisited,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Standort markieren",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Show "Continue to next" button when already at location
                if (navigationState == TourNavigationState.AtLocation) {
                    Button(
                        onClick = {
                            // First stop current navigation
                            mapViewModel.stopNavigation()

                            // Then proceed to next location (updates currentLocation in TourNavigator)
                            onProceedToNextLocation()

                            // Reset audio flag
                            onResetAudioFlag()

                            // Trigger new navigation calculation
                            onStartNewNavigation()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Weiter zum nächsten Standort",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}