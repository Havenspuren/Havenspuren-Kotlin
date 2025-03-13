package com.example.havenspure_kotlin_prototype.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.havenspure_kotlin_prototype.AppDrawer
import com.example.havenspure_kotlin_prototype.Utils.LocationUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.screens.LocationPermissionScreen
import com.example.havenspure_kotlin_prototype.ui.screens.MainScreen
import com.example.havenspure_kotlin_prototype.ui.screens.RichtungenZeigenScreen
import com.example.havenspure_kotlin_prototype.ui.screens.SplashScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourDetailScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourHorenScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourLesenScreen
import com.example.havenspure_kotlin_prototype.ui.screens.ToursMainScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(navController: NavHostController, context: Context) {

    // Initialize the LocationViewModel
    val locationViewModel: LocationViewModel = viewModel()

    // Drawer state for the navigation drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Context and LocationUtils
    val context = LocalContext.current
    val locationUtils = LocationUtils(context)

    // Define the navigation graph
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        // In AppNavHost's NavHost setup
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    // Check location permissions
                    if (locationUtils.hasLocationPermission(context)) {
                        locationUtils.getLastKnownLocationAndSetupUpdates(locationViewModel)                        // Permissions granted, navigate to Main
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        // Permissions not granted, navigate to LocationPermission
                        navController.navigate(Screen.LocationPermission.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Location permission screen with ViewModel
        composable(Screen.LocationPermission.route) {
            LocationPermissionScreen(
                viewModel = locationViewModel,
                onNavigateToMain = {
                    // Navigate to main screen after permission handling
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.LocationPermission.route) { inclusive = true }
                    }
                } , locationUtils = locationUtils
            )
        }

        // Main screen destination with navigation drawer
        composable(Screen.Main.route) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawer(
                        onCloseDrawer = {
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onNavigateToScreen = { route ->
                            // Navigate to selected screen
                            navController.navigate(route) {
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when navigating back
                                restoreState = true
                            }
                            // Close drawer after navigation
                            scope.launch {
                                drawerState.close()
                            }
                        }
                    )
                }
            ) {
                MainScreen(
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    // Pass the LocationViewModel to MainScreen if needed
                    locationViewModel = locationViewModel ,
                    onEntedeckenClick = {navController.navigate(Screen.ToursMain.route)}
                )

            }
        }
        composable(Screen.ToursMain.route) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawer(
                        onCloseDrawer = {
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        onNavigateToScreen = { route ->
                            // Navigate to selected screen
                            navController.navigate(route) {
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when navigating back
                                restoreState = true
                            }
                            // Close drawer after navigation
                            scope.launch {
                                drawerState.close()
                            }
                        }
                    )
                }
            ) {
                ToursMainScreen(
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    onTourSelected = { tour ->
                        // Store the complete tour object in savedStateHandle
                        navController.currentBackStackEntry?.savedStateHandle?.set("tour", tour)
                        // Navigate to the detail screen without any parameters
                        navController.navigate(Screen.TourDetail.route)
                    },
                    onTourInfo = { /* Your implementation */ },
                )
            }
        }

        // Tour Detail Screen
        composable(route = Screen.TourDetail.route) {
            // Get the complete tour object from savedStateHandle
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<Tour>("tour")
                ?: Tour(id = "", title = "Unknown Tour", progress = 0)

            TourDetailScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() },
                onLesenClick = {
                    // Store the tour object for the Lesen screen
                    navController.currentBackStackEntry?.savedStateHandle?.set("tour", tour)
                    // Navigate to the Lesen screen
                    navController.navigate(Screen.TourLesen.route)
                },
                onHorenClick = { // Store the tour object for the Hören screen
                    navController.currentBackStackEntry?.savedStateHandle?.set("tour", tour)
                    // Navigate to the Hören screen
                    navController.navigate(Screen.TourHoren.route)
                 },
                onGPSClick = { navController.currentBackStackEntry?.savedStateHandle?.set("tour", tour)
                    navController.navigate(Screen.RichtungenZeigen.route) },
                locationviewmodel = locationViewModel
            )
        }
        composable(route = Screen.RichtungenZeigen.route) {
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<Tour>("tour")
                ?: Tour(id = "", title = "Unknown Tour", progress = 0)

            RichtungenZeigenScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() },
                locationViewModel = locationViewModel
            )
        }

        // Tour Lesen Screen - Using the same pattern of passing the Tour object
        composable(route = Screen.TourLesen.route) {
            // Get the tour object from savedStateHandle
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<Tour>("tour")
                ?: Tour(id = "", title = "Unknown Tour", progress = 0)

            TourLesenScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(route = Screen.TourHoren.route) {
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<Tour>("tour")
                ?: Tour(id = "", title = "Unknown Tour", progress = 0)

            TourHorenScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() }
            )
        }


        // Additional screens can be added here
        // Map screen
        composable(Screen.Map.route) {
            // MapScreen implementation
        }

        // Trophies screen
        composable(Screen.Trophies.route) {
            // TrophiesScreen implementation
        }
    }
}