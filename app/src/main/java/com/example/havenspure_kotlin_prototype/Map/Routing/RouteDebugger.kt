package com.example.havenspure_kotlin_prototype.Map.Routing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.widget.Toast
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * Helper class for debugging routing issues
 * Provides extensive logging and visualization for route generation
 */
object RouteDebugger {
    private const val TAG = "RouteDebugger"
    private const val DEBUG_ENABLED = true
    private const val FILE_LOGGING_ENABLED = true
    private const val LOG_DIRECTORY = "route_debug_logs"
    private const val VISUALIZATION_ENABLED = true

    // Session ID for correlating logs
    private val sessionId = UUID.randomUUID().toString().substring(0, 8)

    // Counters for tracking routing attempts
    private var totalRoutingAttempts = 0
    private var failedRoutingAttempts = 0
    private var successfulRoutes = 0

    // Last route for visualization
    private var lastRoutePoints: List<GeoPoint>? = null

    /**
     * Initialize the debugger
     */
    fun initialize(context: Context) {
        if (DEBUG_ENABLED && FILE_LOGGING_ENABLED) {
            try {
                // Create log directory
                val logDir = File(context.filesDir, LOG_DIRECTORY)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                // Initialize session log file
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val logFile = File(logDir, "route_debug_${timestamp}_${sessionId}.log")

                FileWriter(logFile, true).use { writer ->
                    writer.append("=== Route Debugging Session Started ===\n")
                    writer.append("Session ID: $sessionId\n")
                    writer.append("Timestamp: ${Date()}\n")
                    writer.append("Device info: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n")
                    writer.append("===================================\n\n")
                }

                Log.i(TAG, "Debug logging initialized. Session ID: $sessionId")
                Log.i(TAG, "Log file location: ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize file logging: ${e.message}")
            }
        }
    }

    /**
     * Log a routing attempt
     */
    fun logRoutingAttempt(startPoint: GeoPoint, endPoint: GeoPoint, routerName: String) {
        if (!DEBUG_ENABLED) return

        totalRoutingAttempts++

        val message = "Routing attempt #$totalRoutingAttempts via $routerName: " +
                "From (${startPoint.latitude},${startPoint.longitude}) " +
                "To (${endPoint.latitude},${endPoint.longitude})"

        Log.d(TAG, message)
        appendToLogFile(message)
    }

    /**
     * Log a successful routing result
     */
    fun logRoutingSuccess(
        routePoints: List<GeoPoint>,
        distance: Double,
        routerName: String,
        requestDuration: Long
    ) {
        if (!DEBUG_ENABLED) return

        successfulRoutes++
        lastRoutePoints = routePoints

        val message = StringBuilder()
        message.append("Routing SUCCESS via $routerName\n")
        message.append("  - Points in route: ${routePoints.size}\n")
        message.append("  - Total distance: $distance meters\n")
        message.append("  - Request duration: $requestDuration ms\n")
        message.append("  - Route statistics: ${analyzeRouteSegments(routePoints)}")

        Log.d(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Log a routing failure
     */
    fun logRoutingFailure(
        routerName: String,
        errorMessage: String,
        requestDuration: Long,
        fallbackUsed: Boolean
    ) {
        if (!DEBUG_ENABLED) return

        failedRoutingAttempts++

        val message = StringBuilder()
        message.append("Routing FAILURE via $routerName\n")
        message.append("  - Error: $errorMessage\n")
        message.append("  - Request duration: $requestDuration ms\n")
        message.append("  - Fallback router used: $fallbackUsed\n")
        message.append("  - Failure rate: $failedRoutingAttempts/$totalRoutingAttempts " +
                "(${(failedRoutingAttempts.toFloat() / totalRoutingAttempts * 100).toInt()}%)")

        Log.e(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Log OSRM server response for debugging
     */
    fun logServerResponse(endpoint: String, responseCode: Int, responseBody: String) {
        if (!DEBUG_ENABLED) return

        val message = StringBuilder()
        message.append("OSRM Server response from $endpoint\n")
        message.append("  - Response code: $responseCode\n")
        message.append("  - Response body (first 500 chars): ${responseBody.take(500)}...\n")

        Log.d(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Log polyline decoding results
     */
    fun logPolylineDecoding(encodedLength: Int, decodedPoints: Int, samplePoints: List<GeoPoint>) {
        if (!DEBUG_ENABLED) return

        val message = StringBuilder()
        message.append("Polyline decoded\n")
        message.append("  - Encoded length: $encodedLength chars\n")
        message.append("  - Decoded points: $decodedPoints\n")
        message.append("  - Sample points (first 3): ${samplePoints.take(3).joinToString { "(${it.latitude}, ${it.longitude})" }}\n")

        Log.d(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Log an HTTP request failure
     */
    fun logHttpFailure(endpoint: String, errorMessage: String, responseCode: Int = -1) {
        if (!DEBUG_ENABLED) return

        val message = StringBuilder()
        message.append("HTTP Request failed to $endpoint\n")
        message.append("  - Error message: $errorMessage\n")
        if (responseCode > 0) {
            message.append("  - Response code: $responseCode\n")
        }

        Log.e(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Log fallback being used
     */
    fun logFallbackUsed(fallbackType: String, reason: String) {
        if (!DEBUG_ENABLED) return

        val message = "Fallback used: $fallbackType due to: $reason"

        Log.w(TAG, message)
        appendToLogFile(message)
    }

    /**
     * Log when route validation fails
     */
    fun logRouteValidationFailed(reason: String, routePoints: List<GeoPoint>?) {
        if (!DEBUG_ENABLED) return

        val message = StringBuilder()
        message.append("Route validation FAILED: $reason\n")
        if (routePoints != null) {
            message.append("  - Route had ${routePoints.size} points\n")
            if (routePoints.isNotEmpty()) {
                message.append("  - First point: (${routePoints.first().latitude}, ${routePoints.first().longitude})\n")
                message.append("  - Last point: (${routePoints.last().latitude}, ${routePoints.last().longitude})\n")
            }

            // Add suspicious segment analysis
            if (routePoints.size >= 2) {
                message.append("  - ${analyzeRouteSegments(routePoints)}\n")
            }
        }

        Log.w(TAG, message.toString())
        appendToLogFile(message.toString())
    }

    /**
     * Show a debug toast message to the user about routing
     */
    fun showDebugToast(context: Context, message: String) {
        if (!DEBUG_ENABLED) return

        try {
            Toast.makeText(context, "[DEBUG] $message", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show debug toast: ${e.message}")
        }
    }

    /**
     * Save a visualization of the route to a file for debugging
     */
    fun saveRouteVisualization(context: Context, routePoints: List<GeoPoint>, startPoint: GeoPoint, endPoint: GeoPoint) {
        if (!DEBUG_ENABLED || !VISUALIZATION_ENABLED) return
        if (routePoints.isEmpty()) return

        try {
            // Create a bitmap to draw the route
            val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Calculate bounding box
            var minLat = Double.MAX_VALUE
            var maxLat = Double.MIN_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = Double.MIN_VALUE

            for (point in routePoints) {
                minLat = Math.min(minLat, point.latitude)
                maxLat = Math.max(maxLat, point.latitude)
                minLon = Math.min(minLon, point.longitude)
                maxLon = Math.max(maxLon, point.longitude)
            }

            // Add padding to the bounding box
            val latPadding = (maxLat - minLat) * 0.1
            val lonPadding = (maxLon - minLon) * 0.1

            minLat -= latPadding
            maxLat += latPadding
            minLon -= lonPadding
            maxLon += lonPadding

            // Set up the canvas
            canvas.drawColor(Color.WHITE)

            // Draw the background grid
            val gridPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            // Draw grid lines
            val gridCount = 10
            for (i in 0..gridCount) {
                val x = i * bitmap.width / gridCount.toFloat()
                canvas.drawLine(x, 0f, x, bitmap.height.toFloat(), gridPaint)

                val y = i * bitmap.height / gridCount.toFloat()
                canvas.drawLine(0f, y, bitmap.width.toFloat(), y, gridPaint)
            }

            // Function to convert geo coordinates to pixel coordinates
            fun geoToPixel(lat: Double, lon: Double): Pair<Float, Float> {
                val x = ((lon - minLon) / (maxLon - minLon) * bitmap.width).toFloat()
                val y = ((maxLat - lat) / (maxLat - minLat) * bitmap.height).toFloat()
                return Pair(x, y)
            }

            // Draw the route
            val routePaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }

            val segmentPaints = arrayOf(
                Paint().apply { // Normal segment
                    color = Color.GREEN
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    isAntiAlias = true
                },
                Paint().apply { // Suspicious segment
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    isAntiAlias = true
                }
            )

            // Draw route segments with color coding for suspicious segments
            for (i in 0 until routePoints.size - 1) {
                val point1 = routePoints[i]
                val point2 = routePoints[i + 1]

                val (x1, y1) = geoToPixel(point1.latitude, point1.longitude)
                val (x2, y2) = geoToPixel(point2.latitude, point2.longitude)

                // Calculate segment length
                val segmentLength = calculateDistance(
                    point1.latitude, point1.longitude,
                    point2.latitude, point2.longitude
                )

                // Choose paint based on segment length
                val paint = if (segmentLength > 300) segmentPaints[1] else segmentPaints[0]

                canvas.drawLine(x1, y1, x2, y2, paint)
            }

            // Draw start and end markers
            val markerPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }

            val startMarkerPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }

            val endMarkerPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }

            val labelPaint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
            }

            // Draw points with color coding and size based on order
            for (i in routePoints.indices) {
                val point = routePoints[i]
                val (x, y) = geoToPixel(point.latitude, point.longitude)

                when (i) {
                    0 -> {
                        canvas.drawCircle(x, y, 10f, startMarkerPaint)
                        canvas.drawText("Start", x + 15, y, labelPaint)
                    }
                    routePoints.size - 1 -> {
                        canvas.drawCircle(x, y, 10f, endMarkerPaint)
                        canvas.drawText("End", x + 15, y, labelPaint)
                    }
                    else -> {
                        canvas.drawCircle(x, y, 5f, markerPaint)

                        // Label some points to make it easier to follow the route
                        if (i % 5 == 0) {
                            canvas.drawText("$i", x, y - 10, labelPaint)
                        }
                    }
                }
            }

            // Add route statistics
            val statsPaint = Paint().apply {
                color = Color.BLACK
                textSize = 28f
            }

            val stats = analyzeRouteSegments(routePoints)
            canvas.drawText("Route Stats: $stats", 20f, 40f, statsPaint)

            // Get a properly formatted filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "route_visualization_${timestamp}.png"

            // Save the bitmap to file
            val logDir = File(context.filesDir, LOG_DIRECTORY)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val imageFile = File(logDir, fileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "Route visualization saved to ${imageFile.absolutePath}")
            appendToLogFile("Route visualization saved to $fileName")

            // Add route data for potential replay/analysis
            saveRouteData(context, routePoints, startPoint, endPoint)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route visualization: ${e.message}", e)
        }
    }

    /**
     * Save route data for potential replay
     */
    private fun saveRouteData(context: Context, routePoints: List<GeoPoint>, startPoint: GeoPoint, endPoint: GeoPoint) {
        if (!DEBUG_ENABLED || !FILE_LOGGING_ENABLED) return

        try {
            val logDir = File(context.filesDir, LOG_DIRECTORY)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dataFile = File(logDir, "route_data_${timestamp}.csv")

            FileWriter(dataFile).use { writer ->
                writer.append("type,lat,lon\n")
                writer.append("start,${startPoint.latitude},${startPoint.longitude}\n")
                writer.append("end,${endPoint.latitude},${endPoint.longitude}\n")

                for (i in routePoints.indices) {
                    val point = routePoints[i]
                    writer.append("route,${point.latitude},${point.longitude}\n")
                }
            }

            Log.d(TAG, "Route data saved to ${dataFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route data: ${e.message}", e)
        }
    }

    /**
     * Visualize last route on a map for debugging
     */
    fun visualizeLastRouteOnMap(mapView: MapView) {
        if (!DEBUG_ENABLED || !VISUALIZATION_ENABLED) return
        if (lastRoutePoints == null || lastRoutePoints!!.isEmpty()) return

        try {
            // Clear any existing debug overlays
            val overlaysToRemove = mapView.overlays.filter { it is Polyline && it.outlinePaint.color == Color.RED }
            mapView.overlays.removeAll(overlaysToRemove)

            // Create a debug polyline for the last route
            val routeOverlay = Polyline(mapView)
            routeOverlay.setPoints(lastRoutePoints)
            routeOverlay.outlinePaint.color = Color.RED
            routeOverlay.outlinePaint.strokeWidth = 10f
            routeOverlay.outlinePaint.isAntiAlias = true

            // Add to map
            mapView.overlays.add(routeOverlay)
            mapView.invalidate()

            Log.d(TAG, "Visualized last route on map with ${lastRoutePoints!!.size} points")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to visualize route on map: ${e.message}", e)
        }
    }

    /**
     * Analyze route segments for potential issues
     */
    private fun analyzeRouteSegments(routePoints: List<GeoPoint>): String {
        if (routePoints.size < 2) {
            return "Too few points for analysis"
        }

        var maxSegmentLength = 0.0
        var totalLength = 0.0
        var suspiciousSegments = 0

        for (i in 0 until routePoints.size - 1) {
            val point1 = routePoints[i]
            val point2 = routePoints[i + 1]

            val segmentLength = calculateDistance(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude
            )

            totalLength += segmentLength
            maxSegmentLength = maxOf(maxSegmentLength, segmentLength)

            // Flag suspiciously long segments (possible water/terrain crossing)
            if (segmentLength > 300) {
                suspiciousSegments++
            }
        }

        val avgSegmentLength = totalLength / (routePoints.size - 1)

        return "Max segment: ${maxSegmentLength.toInt()}m, " +
                "Avg segment: ${avgSegmentLength.toInt()}m, " +
                "Suspicious segments: $suspiciousSegments"
    }

    /**
     * Calculate distance between two points in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Append a message to the log file
     */
    private fun appendToLogFile(message: String) {
        if (!DEBUG_ENABLED || !FILE_LOGGING_ENABLED) return

        try {
            val logDir = File(LOG_DIRECTORY)
            if (!logDir.exists()) return

            val logFiles = logDir.listFiles { file ->
                file.isFile && file.name.contains(sessionId)
            }

            if (logFiles != null && logFiles.isNotEmpty()) {
                val logFile = logFiles[0]
                val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

                FileWriter(logFile, true).use { writer ->
                    writer.append("[$timestamp] $message\n\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file: ${e.message}")
        }
    }

    /**
     * Get routing statistics summary
     */
    fun getStatisticsSummary(): String {
        return "Routing Stats: Attempts: $totalRoutingAttempts, " +
                "Success: $successfulRoutes, " +
                "Failures: $failedRoutingAttempts, " +
                "Success Rate: ${(successfulRoutes.toFloat() / max(totalRoutingAttempts, 1)) * 100}%"
    }

    /**
     * Log network connectivity information
     */
    fun logNetworkInfo(context: Context) {
        if (!DEBUG_ENABLED) return

        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo

            val message = StringBuilder()
            message.append("Network Connectivity Info:\n")

            if (activeNetwork != null) {
                message.append("  - Connected: ${activeNetwork.isConnected}\n")
                message.append("  - Type: ${activeNetwork.typeName}\n")
                message.append("  - State: ${activeNetwork.state}\n")

                if (activeNetwork.type == android.net.ConnectivityManager.TYPE_MOBILE) {
                    message.append("  - Mobile data is active\n")
                } else if (activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI) {
                    message.append("  - WiFi is active\n")
                }
            } else {
                message.append("  - No active network\n")
            }

            Log.d(TAG, message.toString())
            appendToLogFile(message.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log network info: ${e.message}", e)
        }
    }

    /**
     * Set debug status (for remote debugging control)
     */
    fun setDebugEnabled(enabled: Boolean) {
        // This is a compile-time constant, but this method lets you log the request
        // and potentially implement runtime toggling in a future version
        Log.i(TAG, "Request to set debug ${if (enabled) "enabled" else "disabled"} received (currently ${if (DEBUG_ENABLED) "enabled" else "disabled"})")
    }
}