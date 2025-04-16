package com.example.havenspure_kotlin_prototype.di

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.havenspure_kotlin_prototype.Utils.AudioUtils
import com.example.havenspure_kotlin_prototype.ViewModels.LocationTourViewModel
import com.example.havenspure_kotlin_prototype.ViewModels.ToursViewModel
import com.example.havenspure_kotlin_prototype.data.local.AppDatabase
import com.example.havenspure_kotlin_prototype.navigation.TourNavigationCoordinator
import com.havenspure.data.repository.DataInitRepository
import com.havenspure.data.repository.TourRepository
import com.havenspure.data.repository.UserProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class Graph private constructor(private val context: Context) {

    // Application scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Database instance
    private val database by lazy {
        AppDatabase.getDatabase(context)
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
    val tourRepository by lazy {
        TourRepository(tourDao, locationDao)
    }

    val userProgressRepository by lazy {
        UserProgressRepository(userProgressDao, visitedLocationDao, locationDao, trophyDao)
    }

    val dataInitRepository by lazy {
        DataInitRepository(tourRepository, trophyDao)
    }

    // ViewModels
    val toursViewModel by lazy {
        ToursViewModel(tourRepository, userProgressRepository)
    }

    val locationTourViewModel by lazy {
        LocationTourViewModel(tourRepository, userProgressRepository, audioUtils)
    }

    // Navigation Coordinator
    val tourNavigationCoordinator by lazy {
        TourNavigationCoordinator(context)
    }

    // Initialize default data
    init {
        applicationScope.launch {
            initializeDefaultData()
        }
    }

    // In Graph
    fun isDataInitialized(): Boolean {
        // Use a simpler non-blocking check or a SharedPreference flag
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("data_initialized", false)
    }

    // Mark data as initialized after successful initialization
    private suspend fun markDataInitialized() {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("data_initialized", true)
            .apply()
    }

    private suspend fun initializeDefaultData() {
        try {
            dataInitRepository.initializeDefaultData()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retry once if initialization fails
            try {
                dataInitRepository.initializeDefaultData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Graph? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = Graph(context)
                    }
                }
            }
        }

        fun getInstance(): Graph {
            return instance ?: throw IllegalStateException("Graph must be initialized first")
        }
    }
}