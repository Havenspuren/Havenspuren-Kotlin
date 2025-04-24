package com.example.havenspure_kotlin_prototype.navigation

import com.example.havenspure_kotlin_prototype.models.Tour

// Sealed class to define app navigation routes
sealed class Screen(val route: String) {
    // Main screens
    object Feedback : Screen("feedback")
    object Support : Screen("support")
    object About : Screen("about")
    object OfflineRichtungenZeigen : Screen("offline_richtungen_zeigen") // New offline directions screen
    object Splash : Screen("splash")
    object LocationPermission : Screen("location_permission")
    object Main : Screen("main")
    object ToursMain : Screen("tours_main")
    object TourDetail : Screen("tour_detail")
    object RichtungenZeigen : Screen("richtungen_zeigen")
    object NavigationScreen : Screen("navigation")
    object TourOverviewScreen : Screen("tour_overview")
    object TourHoren : Screen("tour_horen")
    object Map : Screen("map")
    object Trophies : Screen("trophies")
    object LocationDetailScreen : Screen("location_detail")

}