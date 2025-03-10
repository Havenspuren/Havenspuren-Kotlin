package com.example.havenspure_kotlin_prototype.navigation

import android.content.Context
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.havenspure_kotlin_prototype.AppDrawer
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ui.screens.LocationPermissionScreen
import com.example.havenspure_kotlin_prototype.ui.screens.MainScreen
import com.example.havenspure_kotlin_prototype.ui.screens.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(navController: NavHostController, context: Context) {

    // Initialize the LocationViewModel
    val locationViewModel: LocationViewModel = viewModel()

    // Drawer state for the navigation drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Define the navigation graph
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        // Splash screen destination
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    // Go to location permission screen after splash
                    navController.navigate(Screen.LocationPermission.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
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
                }
            )
        }

        // Main screen destination with navigation drawer
        composable(Screen.Main.route) {
            androidx.compose.material3.ModalNavigationDrawer(
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
                    onTourSelected = { tourId ->
                        // Navigate to tour detail when a tour is selected
                        navController.navigate(Screen.TourDetail.createRoute(tourId))
                    },
                    // Pass the LocationViewModel to MainScreen if needed
                    locationViewModel = locationViewModel
                )
            }
        }

        // Tour detail screen with tour ID parameter
        composable(
            route = Screen.TourDetail.route,
            arguments = listOf(
                navArgument("tourId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val tourId = backStackEntry.arguments?.getString("tourId") ?: ""
            // TourDetailScreen implementation to be added
            // For now, we'll just navigate back when it's created
            navController.popBackStack()
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