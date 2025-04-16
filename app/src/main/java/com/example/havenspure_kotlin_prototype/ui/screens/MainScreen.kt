/*
package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ui.components.MapComponent
import com.example.havenspure_kotlin_prototype.ui.theme.AccentColor
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.TextDark

@Composable
fun MainScreen(
    locationViewModel: LocationViewModel,
    onEntedeckenClick: () -> Unit
) {
    // Get location from ViewModel if available
    val location by locationViewModel.location ?: remember { mutableStateOf(null) }
    val address by locationViewModel.address ?: remember { mutableStateOf(null) }

    // Get screen dimensions
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header section with titles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App name
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // City name
                Text(
                    text = stringResource(R.string.title_wilhelmshaven),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description text
                Text(
                    text = stringResource(R.string.subtitle_explore),
                    fontSize = 20.sp,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

            }

            // Map section (takes most of the screen space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeight * 0.45).dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                MapComponent(locationData = location)

            }
            Spacer(modifier = Modifier.height(16.dp))


            // Location card
            address?.let { addressText ->
                val isInWilhelmshaven = addressText.contains("Wilhelmshaven", ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE1F5FE)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFF00BCD4),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (isInWilhelmshaven)
                                "Ihr Standort: $addressText"
                            else
                                "Achtung: Sie befinden sich nicht in Wilhelmshaven",
                            fontSize = 14.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.25.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom button
            Button(
                onClick = onEntedeckenClick,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .padding(vertical = 16.dp, horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "ENTDECKEN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

 */

package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ui.components.MapComponent
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.TextDark

@Composable
fun MainScreen(
    locationViewModel: LocationViewModel,
    onEntedeckenClick: () -> Unit
) {
    // Get location from ViewModel if available
    val location by locationViewModel.location ?: remember { mutableStateOf(null) }
    val address by locationViewModel.address ?: remember { mutableStateOf(null) }

    // Get screen dimensions
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header section with titles - OPTIMIZED
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App name with improved styling
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // City name with better separation
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xDDFFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_wilhelmshaven),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description text
                Text(
                    text = stringResource(R.string.subtitle_explore),
                    fontSize = 18.sp,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Map section (takes most of the screen space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeight * 0.45).dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                MapComponent(locationData = location)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location card
            address?.let { addressText ->
                val isInWilhelmshaven = addressText.contains("Wilhelmshaven", ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE1F5FE)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFF00BCD4),
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (isInWilhelmshaven)
                                "Ihr Standort: $addressText"
                            else
                                "Achtung: Sie befinden sich nicht in Wilhelmshaven",
                            fontSize = 14.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.25.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom button
            Button(
                onClick = onEntedeckenClick,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .padding(vertical = 16.dp, horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "ENTDECKEN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}