package com.example.havenspure_kotlin_prototype.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.havenspure_kotlin_prototype.AppDrawer
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.Utils.LocationUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationTourViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.di.Graph
import com.example.havenspure_kotlin_prototype.models.Tour
import com.example.havenspure_kotlin_prototype.ui.screens.LocationPermissionScreen
import com.example.havenspure_kotlin_prototype.ui.screens.MainScreen
import com.example.havenspure_kotlin_prototype.ui.screens.NavigationScreen
import com.example.havenspure_kotlin_prototype.ui.screens.RichtungenZeigenScreen
import com.example.havenspure_kotlin_prototype.ui.screens.SplashScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourDetailScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourHorenScreen
import com.example.havenspure_kotlin_prototype.ui.screens.TourOverviewScreen
import com.example.havenspure_kotlin_prototype.ui.screens.ToursMainScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(navController: NavHostController, context: Context, toursViewModel: ToursViewModel) {

    // Initialize the LocationViewModel
    val locationViewModel: LocationViewModel = viewModel()

    // Get viewModels from dependency injection
    val locationTourViewModel: LocationTourViewModel = Graph.getInstance().locationTourViewModel

    // Initialize TourNavigationCoordinator from dependency injection
    val tourNavigator: TourNavigator = Graph.getInstance().tourNavigator

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
                    // Pass the LocationViewModel to MainScreen if needed
                    locationViewModel = locationViewModel,
                    onEntedeckenClick = {navController.navigate(Screen.ToursMain.route)}
                )
            }
        }

        // Tours main screen showing available tours
        composable(Screen.ToursMain.route) {
            val uiState by toursViewModel.uiState.collectAsStateWithLifecycle()

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
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch {
                                drawerState.close()
                            }
                        }
                    )
                }
            ) {
                ToursMainScreen(
                    uiState = uiState,
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    onTourSelected = { tourId ->
                        // Navigate using the tour ID instead of the full object
                        navController.navigate("${Screen.TourDetail.route}/$tourId")
                    },
                    toursViewModel = toursViewModel  // Pass the ViewModel directly
                )
            }
        }

        // Tour Detail Screen - now using ID-based navigation
        composable(
            route = "${Screen.TourDetail.route}/{tourId}",
            arguments = listOf(navArgument("tourId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Extract tour ID from navigation arguments
            val tourId = backStackEntry.arguments?.getString("tourId") ?: ""

            TourDetailScreen(
                tourId = tourId,
                toursViewModel = toursViewModel,
                locationViewModel = locationViewModel,
                onBackClick = { navController.popBackStack() },
                onOverviewClick = { id ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("tourId", id)
                    navController.navigate(Screen.TourOverviewScreen.route)
                },
                onStartClick = { id ->
                    // Navigate to Navigation screen with the ID
                    navController.navigate("${Screen.NavigationScreen.route}/$id")
                }
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

        // NavigationScreen - Updated to pass the tourNavigationCoordinator
        composable(
            route = "${Screen.NavigationScreen.route}/{tourId}",
            arguments = listOf(navArgument("tourId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Extract tour ID from navigation arguments
            val tourId = backStackEntry.arguments?.getString("tourId") ?: ""

            NavigationScreen(
                tourId = tourId,
                onBackClick = { navController.popBackStack() },
                locationViewModel = locationViewModel,
                tourNavigator = tourNavigator  // Pass the coordinator from DI
            )
        }

        // Tour Lesen Screen - Using the same pattern of passing the Tour object
        composable(route = Screen.TourOverviewScreen.route) {
            // Get the tourId from savedStateHandle
            val tourId = navController.previousBackStackEntry?.savedStateHandle?.get<String>("tourId") ?: ""

            TourOverviewScreen(
                tourId = tourId,
                toursViewModel = toursViewModel,
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