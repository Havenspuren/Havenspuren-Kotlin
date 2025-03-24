package com.example.havenspure_kotlin_prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.havenspure_kotlin_prototype.Map.offline.MapInitializer
import com.example.havenspure_kotlin_prototype.navigation.AppNavHost
import com.example.havenspure_kotlin_prototype.ui.theme.WilhelmshavenTheme

class MainActivity : ComponentActivity() {
    // Add the map initializer as a property
    private lateinit var mapInitializer: MapInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the map services
        mapInitializer = MapInitializer(this)
        mapInitializer.initialize() // Initialize map
        mapInitializer.initializeRouting() // Initialize routing

        setContent {
            // Initialize navigation controller
            val navController = rememberNavController()

            WilhelmshavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Set up the navigation host with the controller
                    AppNavHost(
                        navController = navController,
                        context = this
                    )
                }
            }
        }
    }

    // Provide a way for components to access the map initializer
    fun getMapInitializer(): MapInitializer {
        return mapInitializer
    }
}