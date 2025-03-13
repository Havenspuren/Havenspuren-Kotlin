package com.example.havenspure_kotlin_prototype.navigation

import com.example.havenspure_kotlin_prototype.models.Tour

// Sealed class to define app navigation routes
sealed class Screen(val route: String) {
    // Main screens
    object Splash : Screen("splash")
    object LocationPermission : Screen("location_permission")
    object Main : Screen("main")
    object TourDetail : Screen("tour_detail") // No parameters in the route
    object Map : Screen("map")
    object Trophies : Screen("trophies")
    object Feedback : Screen("feedback")
    object Support : Screen("support")
    object About : Screen("about")
    object ToursMain : Screen("tours_main")
    object TourLesen : Screen("tour_lesen")
    object TourHoren : Screen("tour_horen")
    object RichtungenZeigen : Screen("richtungen_zeigen")
}