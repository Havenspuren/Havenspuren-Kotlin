package com.example.havenspure_kotlin_prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.havenspure_kotlin_prototype.navigation.AppNavHost
import com.example.havenspure_kotlin_prototype.ui.theme.WilhelmshavenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}