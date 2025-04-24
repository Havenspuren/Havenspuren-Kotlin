package com.example.havenspure_kotlin_prototype.navigation

import LocationDetailScreen
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.havenspure_kotlin_prototype.AppDrawer
import com.example.havenspure_kotlin_prototype.Utils.LocationUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.di.Graph
import com.example.havenspure_kotlin_prototype.models.Tour as ModelTour
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

    // Initialize TourNavigator from dependency injection
    val tourNavigator = Graph.getInstance().tourNavigator

    // Drawer state for the navigation drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Get local context (don't redefine context as it's already a parameter)
    val localContext = LocalContext.current
    val locationUtils = LocationUtils(localContext)

    // Define the navigation graph
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        // Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    // Check location permissions
                    if (locationUtils.hasLocationPermission(context)) {
                        locationUtils.getLastKnownLocationAndSetupUpdates(locationViewModel)
                        // Permissions granted, navigate to Main
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
                },
                locationUtils = locationUtils
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
                    onEntedeckenClick = { navController.navigate(Screen.ToursMain.route) }
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
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<ModelTour>("tour")
                ?: ModelTour(id = "", title = "Unknown Tour", progress = 0)

            RichtungenZeigenScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() },
                locationViewModel = locationViewModel
            )
        }

        // NavigationScreen route
        composable(
            route = "${Screen.NavigationScreen.route}/{tourId}",
            arguments = listOf(navArgument("tourId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Extract tour ID from navigation arguments
            val tourId = backStackEntry.arguments?.getString("tourId") ?: ""

            NavigationScreen(
                tourId = tourId,
                onBackClick = { navController.popBackStack() },
                onShowLocationDetail = { location ->
                    // Navigate to LocationDetailScreen with required parameters
                    navController.navigate("${Screen.LocationDetailScreen.route}/$tourId/${location.id}")
                },
                locationViewModel = locationViewModel,
                tourNavigator = tourNavigator
            )
        }

        // Tour Overview Screen
        composable(route = Screen.TourOverviewScreen.route) {
            // Get the tourId from savedStateHandle
            val tourId = navController.previousBackStackEntry?.savedStateHandle?.get<String>("tourId") ?: ""

            TourOverviewScreen(
                tourId = tourId,
                toursViewModel = toursViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Tour Hören Screen
        composable(route = Screen.TourHoren.route) {
            val tour = navController.previousBackStackEntry?.savedStateHandle?.get<ModelTour>("tour")
                ?: ModelTour(id = "", title = "Unknown Tour", progress = 0)

            TourHorenScreen(
                tour = tour,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Map screen placeholder
        composable(Screen.Map.route) {
            // MapScreen implementation
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Map Screen")
            }
        }

        // Trophies screen placeholder
        composable(Screen.Trophies.route) {
            // TrophiesScreen implementation
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Trophies Screen")
            }
        }

        // LocationDetailScreen route - FIXED
        composable(
            route = "${Screen.LocationDetailScreen.route}/{tourId}/{locationId}",
            arguments = listOf(
                navArgument("tourId") { type = NavType.StringType },
                navArgument("locationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // Extract parameters from navigation arguments
            val tourId = backStackEntry.arguments?.getString("tourId") ?: ""
            val locationId = backStackEntry.arguments?.getString("locationId") ?: ""

            // Use remember to keep track of state
            var isLoading by remember { mutableStateOf(true) }
            var tour by remember { mutableStateOf<Tour?>(null) }
            var location by remember { mutableStateOf<Location?>(null) }

            // Fetch tour and location data
            LaunchedEffect(tourId, locationId) {
                isLoading = true

                // Use the existing toursViewModel that's passed as a parameter
                toursViewModel.getTourWithLocations(tourId) { tourWithLocations ->
                    if (tourWithLocations != null) {
                        tour = tourWithLocations.tour
                        location = tourWithLocations.locations.find { it.id == locationId }
                    }
                    isLoading = false
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (tour != null && location != null) {
                LocationDetailScreen(
                    location = location!!,
                    tour = tour!!,
                    tourNavigator = tourNavigator,
                    onBackClick = { navController.popBackStack() },
                    onFinishClick = {
                        // Return to navigation screen after marking location as visited
                        navController.popBackStack()
                    }
                )
            } else {
                // Error state - could not load data
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not load location details")
                }
            }
        }
    }
}