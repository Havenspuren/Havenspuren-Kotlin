package com.example.havenspure_kotlin_prototype.graph

import android.content.Context
import androidx.annotation.Keep
import com.example.havenspure_kotlin_prototype.Graph.RoutingGraph
import com.example.havenspure_kotlin_prototype.navigation.OSRMRouter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.osmdroid.config.Configuration
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized dependency injection and service management
 */
@Keep
object Graph {
    // Thread-safe context reference
    private val contextRef = AtomicReference<Context?>(null)

    // Mutex for thread-safe operations
    private val mutex = Mutex()

    /**
     * Safely get the application context
     */
    private fun getContext(): Context {
        return contextRef.get()
            ?: throw IllegalStateException("Graph not initialized. Call provide() first.")
    }

    /**
     * Lazy-initialized OSRM Router
     */
    val osrmRouter: OSRMRouter by lazy {
        OSRMRouter(getContext())
    }

    /**
     * Initialize graph services
     */
    suspend fun provide(context: Context) = mutex.withLock {
        // Store application context
        contextRef.set(context.applicationContext)

        // Retrieve the stored context
        val appContext = getContext()

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(
            appContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        )

        // Configure OSMDroid settings
        Configuration.getInstance().apply {
            osmdroidTileCache = appContext.cacheDir
            userAgentValue = appContext.packageName
            osmdroidBasePath = appContext.filesDir

            // Performance settings
            tileDownloadThreads = 2
            tileFileSystemThreads = 2
            tileDownloadMaxQueueSize = 8
            tileFileSystemMaxQueueSize = 8
            expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30 // Cache for a month
        }

        // Initialize routing services
        RoutingGraph.initialize(appContext)
    }

    /**
     * Reset or reinitialize services
     */
    suspend fun reset(context: Context) {
        // Clear existing context and reinitialize
        contextRef.set(null)
        provide(context)
    }

    /**
     * Check if Graph is initialized
     */
    fun isInitialized(): Boolean {
        return contextRef.get() != null
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        contextRef.set(null)
    }
}