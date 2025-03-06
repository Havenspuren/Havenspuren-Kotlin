package com.example.havenspure_kotlin_prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.havenspure_kotlin_prototype.ui.screens.MainScreen
import com.example.havenspure_kotlin_prototype.ui.screens.SplashScreen
import com.example.havenspure_kotlin_prototype.ui.theme.WilhelmshavenTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WilhelmshavenTheme {
                // State to track whether to show splash screen or main screen
                var showSplashScreen by remember { mutableStateOf(true) }

                if (showSplashScreen) {
                    SplashScreen(
                        onNavigateToMain = {
                            showSplashScreen = false
                        }
                    )
                } else {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            AppDrawer(
                                onCloseDrawer = {
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
                            }
                        )
                    }
                }
            }
        }
    }
}