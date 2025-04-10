package com.example.havenspure_kotlin_prototype

import android.app.Application
import com.example.havenspure_kotlin_prototype.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class Havenspuren : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        // Initialize Graph in Application but don't block on data initialization
        Graph.initialize(applicationContext)

        // Start data initialization in background without waiting for it
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            Graph.getInstance().dataInitRepository.initializeDefaultData()
        }
    }
}