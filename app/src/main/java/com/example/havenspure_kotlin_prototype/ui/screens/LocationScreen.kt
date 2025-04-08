package com.example.havenspure_kotlin_prototype.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.Utils.LocationUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.TextDark

@Composable
fun LocationPermissionScreen(
    viewModel: LocationViewModel,
    onNavigateToMain: () -> Unit,
    locationUtils: LocationUtils) {
    // State to control welcome dialog visibility
    var showWelcomeDialog by remember { mutableStateOf(true) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Theme colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = GradientStart

    // Context and LocationUtils
    val context = LocalContext.current
    // Permission launcher for precise and approximate location
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Use the utility function to check permissions
            if (locationUtils.hasLocationPermission(context)) {
                // Permission granted, request location updates
                locationUtils.requestLocationUpdates(viewModel)
                // Navigate to main screen
                onNavigateToMain()
            } else {
                // Permission denied, show rationale if needed
                val rationaleRequired = ActivityCompat.shouldShowRequestPermissionRationale(
                    context as androidx.activity.ComponentActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                if (rationaleRequired) {
                    // Show a Toast or Snackbar explaining why permission is needed
                    // For now, just navigate to main screen
                    onNavigateToMain()
                } else {
                    // User denied permission and selected "Don't ask again"
                    // Navigate to main screen anyway
                    onNavigateToMain()
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Background content with welcome text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            Text(
                text = "Wilkommen",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
            )

            // Welcome message
            Text(
                text = "Hallo und herzlich willkommen bei Havenspuren!",
                fontSize = 18.sp,
                color = TextDark,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Description
            Text(
                text = "Wir laden Sie ein, die vergangenen und gegenwärtigen Gesichter Wilhelmshavens zu entdecken. Dabei begleiten Sie diejenigen, die die Stadt am besten kennen: Ihre Bewohner und Bewohnerinnen.",
                fontSize = 16.sp,
                color = TextDark,
                lineHeight = 24.sp
            )
        }

        // Permission dialog (only shown after welcome dialog is dismissed)
        if (showPermissionDialog) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Location icon
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = primaryColor,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // German title for permission
                    Text(
                        text = "Standortzugriff erlauben",
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Permission text
                    Text(
                        text = "Möchten Sie Havenspuren Zugriff auf den Standort dieses Geräts gewähren?",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = TextDark
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Location option row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Precise location
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    // Request precise location permission
                                    requestPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F0FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Precise location",
                                    tint = primaryColor,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Genauer Standort",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Approximate location
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    // Request approximate location permission
                                    requestPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF5F5F5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.baseline_add_location_alt_24),
                                    contentDescription = "Approximate location",
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Ungefährer Standort",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Permission duration buttons
                    Button(
                        onClick = {
                            // Request both permissions
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text(
                            text = "Während der App-Nutzung",
                            modifier = Modifier.padding(vertical = 8.dp),
                            fontSize = 16.sp
                        )
                    }

                    Button(
                        onClick = {
                            // Request both permissions (one-time permissions are handled by system UI)
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text(
                            text = "Nur dieses Mal",
                            modifier = Modifier.padding(vertical = 8.dp),
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            // Skip permission and continue to main screen
                            onNavigateToMain()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                    ) {
                        Text(
                            text = "Nicht erlauben",
                            modifier = Modifier.padding(vertical = 8.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Welcome dialog
        if (showWelcomeDialog) {
            Dialog(
                onDismissRequest = {
                    showWelcomeDialog = false
                    // Show permission dialog after welcome dialog is dismissed
                    showPermissionDialog = true
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title
                        Text(
                            text = stringResource(id = R.string.greeting_message_title),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )

                        // First paragraph
                        Text(
                            text = "Hallo und herzlich willkommen bei Havenspuren!",
                            fontSize = 16.sp,
                            color = TextDark,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        // Second paragraph
                        Text(
                            text = "Wir laden Sie ein, die vergangenen und gegenwärtigen Gesichter Wilhelmshavens zu entdecken. Dabei begleiten Sie diejenigen, die die Stadt am besten kennen: Ihre Bewohner und Bewohnerinnen.",
                            fontSize = 16.sp,
                            color = TextDark,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        // Third paragraph
                        Text(
                            text = "Diese App ist im Rahmen eines studentischen Projektes der Jade Hochschule entstanden und befindet sich derzeit noch in der Testphase. Das bedeutet, sie ist einsatzbereit, aber es warten noch ein paar Funktionen und Änderungen darauf, umgesetzt zu werden. Wir freuen uns daher über jede Rückmeldung, die wir bezüglich Verbesserungsvorschlägen erhalten. Klicken Sie dafür im Menü auf Feedback senden oder Support oder folgen Sie dem Link am Ende der Tour.",
                            fontSize = 16.sp,
                            color = TextDark,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        // Fourth paragraph
                        Text(
                            text = "Vielen Dank und viel Spaß beim Entdecken!",
                            fontSize = 16.sp,
                            color = TextDark,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Confirm button
                        Button(
                            onClick = {
                                showWelcomeDialog = false
                                // Show permission dialog after welcome dialog is dismissed
                                showPermissionDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Verstanden",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}