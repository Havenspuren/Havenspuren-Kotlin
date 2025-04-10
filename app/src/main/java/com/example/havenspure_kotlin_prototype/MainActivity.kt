
package com.example.havenspure_kotlin_prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.example.havenspure_kotlin_prototype.di.Graph
import com.example.havenspure_kotlin_prototype.navigation.AppNavHost
import com.example.havenspure_kotlin_prototype.ui.theme.WilhelmshavenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : ComponentActivity() {
    // Add the map initializer as a property


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content immediately with a loading state if needed
        setContent {
            val navController = rememberNavController()
            WilhelmshavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = navController,
                        context = this@MainActivity,
                        toursViewModel = Graph.getInstance().toursViewModel
                    )
                }
            }
        }
    }
}