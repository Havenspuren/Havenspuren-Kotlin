package com.example.havenspure_kotlin_prototype.navigation

// Sealed class to define app navigation routes
sealed class Screen(val route: String) {
    // Main screens
    object Splash : Screen("splash")
    object LocationPermission : Screen("location_permission")
    object Main : Screen("main")

    // Additional screens for future implementation
    object TourDetail : Screen("tour_detail/{tourId}") {
        fun createRoute(tourId: String) = "tour_detail/$tourId"
    }

    object Map : Screen("map")
    object Trophies : Screen("trophies")
    object Feedback : Screen("feedback")
    object Support : Screen("support")
    object About : Screen("about")
}