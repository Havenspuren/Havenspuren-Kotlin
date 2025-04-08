package com.example.havenspure_kotlin_prototype.di

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.example.havenspure_kotlin_prototype.Utils.AudioUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationTourViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.havenspure.data.local.HavenspurenDatabase
import com.havenspure.data.repository.DataInitRepository
import com.havenspure.data.repository.TourRepository
import com.havenspure.data.repository.UserProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependency injection graph for the application
 */
class Graph private constructor(private val context: Context) {

    // Application scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Database instance
    private val database by lazy {
        HavenspurenDatabase.getDatabase(context)
    }

    // DAOs
    private val tourDao by lazy { database.tourDao() }
    private val locationDao by lazy { database.locationDao() }
    private val userProgressDao by lazy { database.userProgressDao() }
    private val visitedLocationDao by lazy { database.visitedLocationDao() }
    private val trophyDao by lazy { database.trophyDao() }

    // Util classes
    private val audioUtils by lazy { AudioUtils(context) }

    // Repositories
    private val tourRepository by lazy {
        TourRepository(tourDao, locationDao)
    }

    private val userProgressRepository by lazy {
        UserProgressRepository(userProgressDao, visitedLocationDao, locationDao, trophyDao)
    }

    private val dataInitRepository by lazy {
        DataInitRepository(tourRepository, trophyDao)
    }

    // ViewModels
    val toursViewModel by lazy {
        ToursViewModel(tourRepository, userProgressRepository)
    }

    val locationTourViewModel by lazy {
        LocationTourViewModel(tourRepository, userProgressRepository, audioUtils)
    }

    // Initialize default data on first launch
    init {
        applicationScope.launch {
            initializeDefaultData()
        }
    }

    private suspend fun initializeDefaultData() {
        try {
            // Always initialize default data first to ensure default tour exists
            dataInitRepository.initializeDefaultData()

            // After initializing default data, you can load other tours if needed
            // This ensures at least one tour will always be available
            tourRepository.getToursWithProgress().collect { tours ->
                // Additional operations with tours if needed
                // For example, you could log or process the loaded tours
            }
        } catch (e: Exception) {
            // Log the error
            e.printStackTrace()
            // If there's an error, try to initialize default data again
            try {
                dataInitRepository.initializeDefaultData()
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle the case where default data initialization fails completely
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Graph? = null

        fun initialize(context: Context) {
            if (instance == null) {
                instance = Graph(context)
            }
        }

        fun getInstance(): Graph {
            return instance ?: throw IllegalStateException("Graph must be initialized first")
        }
    }
}

// Extension function to access context from Application class
fun Context.applicationGraph(): Graph = when (this) {
    is HavenspureApplication -> this.graph
    else -> this.applicationContext.applicationGraph()
}

// Application class that initializes the graph
class HavenspureApplication : Application() {
    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()
        Graph.initialize(this)
        graph = Graph.getInstance()
    }
}