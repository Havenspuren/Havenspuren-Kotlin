package com.example.havenspure_kotlin_prototype.OSRM.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton class to create and manage Retrofit instances for OSRM API calls
 * Supports multiple endpoints with failover capability
 */
object RetrofitClient {
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB cache
    private const val TIMEOUT_SECONDS = 15L

    // Map to store API services for different endpoints
    private val apiServices = mutableMapOf<String, OSRMApiService>()

    /**
     * Create or retrieve an OSRM API service for the specified endpoint
     *
     * @param context Application context
     * @param baseUrl API endpoint URL
     * @return OSRM API service instance
     */
    fun getApiService(context: Context, baseUrl: String = OSRMApiService.BASE_URL): OSRMApiService {
        // Return existing service if available
        apiServices[baseUrl]?.let { return it }

        // Create cache directory
        val cacheDir = File(context.cacheDir, "http-cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Create cache
        val cache = Cache(cacheDir, CACHE_SIZE)

        // Create logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebugBuild(context)) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // Configure OkHttpClient
        val client = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Create and store API service
        val apiService = retrofit.create(OSRMApiService::class.java)
        apiServices[baseUrl] = apiService

        return apiService
    }

    /**
     * Get all available API services for failover
     *
     * @param context Application context
     * @return List of API services in order of preference
     */
    fun getAllApiServices(context: Context): List<OSRMApiService> {
        return OSRMApiService.OSRM_ENDPOINTS.map { baseUrl ->
            getApiService(context, baseUrl)
        }
    }

    /**
     * Check if the app is running in debug mode
     */
    private fun isDebugBuild(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val flags = packageInfo.applicationInfo?.flags ?: 0
            (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
}