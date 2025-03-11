package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.components.MapComponent
import com.example.havenspure_kotlin_prototype.ui.theme.AccentColor
import com.example.havenspure_kotlin_prototype.ui.theme.GradientEnd
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.TextDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenDrawer: () -> Unit,
    onTourSelected: (String) -> Unit = {},
    locationViewModel: LocationViewModel,
    onEntedeckenClick : () -> Unit
) {


    // Get location from ViewModel if available
    val location by locationViewModel?.location ?: remember { mutableStateOf(null) }
    val address by locationViewModel?.address ?: remember { mutableStateOf(null) }

    // Get screen height to calculate proportions
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Calculate fixed heights for sections
    val headerHeight = 150.dp  // Reduced back to original size
    val mapHeight = 400.dp  // Fixed height for map
    val toursHeight = 180.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications"
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share"
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    }
                }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                // Title section with fixed height
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.title_wilhelmshaven),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )


                    Text(
                        text = stringResource(R.string.subtitle_explore),
                        fontSize = 18.sp,
                        color = TextDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                }

                // Map section with fixed height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .padding(horizontal = 10.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Always use the MapComponent - it will show Wilhelmshaven center if location is null
                    MapComponent(locationData = location)
                }

                // Location information section
                address?.let {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
                                contentDescription = "Current Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Ihr Standort: $it",
                                fontSize = 14.sp,
                                color = TextDark,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.25.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Replace the tours list with a centered oval button (like in the screenshot)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .padding(top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onEntedeckenClick()
                        },
                        shape = RoundedCornerShape(70.dp), // Oval shape with rounded corners
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor // Using theme's AccentColor
                        ),
                        modifier = Modifier
                            .width(220.dp)
                            .height(90.dp)
                            .shadow(8.dp, RoundedCornerShape(70.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ENTDECKEN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
