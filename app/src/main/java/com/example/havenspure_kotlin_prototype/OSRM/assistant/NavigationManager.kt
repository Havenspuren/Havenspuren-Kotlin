package com.example.havenspure_kotlin_prototype.OSRM.assistant

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.havenspure_kotlin_prototype.OSRM.data.models.LocationDataOSRM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages navigation-related functionality including:
 * - Bearing/orientation tracking
 * - Turn-by-turn navigation
 * - Arrival detection
 */
class NavigationManager(private val context: Context) : SensorEventListener {
    private val TAG = "NavigationManager"

    // Sensor-related fields
    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rotationVector: Sensor? = null
    private var orientationJob: Job? = null
    private var sensorListening = false

    // Orientation data
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private val orientationAngles = FloatArray(3)
    private val rotationMatrix = FloatArray(9)

    // Navigation state
    private val _currentBearing = MutableStateFlow(0f)
    val currentBearing: StateFlow<Float> = _currentBearing

    private val _isArrived = MutableStateFlow(false)
    val isArrived: StateFlow<Boolean> = _isArrived

    private val _currentInstruction = MutableStateFlow("Starten Sie die Navigation")
    val currentInstruction: StateFlow<String> = _currentInstruction

    private val _remainingDistance = MutableStateFlow("Entfernung wird berechnet...")
    val remainingDistance: StateFlow<String> = _remainingDistance

    /**
     * Start tracking device orientation
     */
    fun startOrientationTracking() {
        if (sensorListening) return

        try {
            // Get sensors
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            // Register listeners based on available sensors
            if (rotationVector != null) {
                // Modern devices - use rotation vector
                sensorManager.registerListener(
                    this,
                    rotationVector,
                    SensorManager.SENSOR_DELAY_GAME
                )
            } else if (accelerometer != null && magnetometer != null) {
                // Legacy fallback - use accelerometer + magnetometer
                sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
                sensorManager.registerListener(
                    this,
                    magnetometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            sensorListening = true
            Log.d(TAG, "Orientation tracking started")

            // Periodically update bearing
            orientationJob = CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    // Just to ensure we don't flood with updates
                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting orientation tracking: ${e.message}")
        }
    }

    /**
     * Stop tracking device orientation
     */
    fun stopOrientationTracking() {
        try {
            sensorManager.unregisterListener(this)
            orientationJob?.cancel()
            orientationJob = null
            sensorListening = false
            Log.d(TAG, "Orientation tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping orientation tracking: ${e.message}")
        }
    }

    /**
     * Handle sensor data
     */
    override fun onSensorChanged(event: SensorEvent) {
        try {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    // Process rotation vector directly
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    updateOrientation()
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Copy accelerometer data
                    System.arraycopy(event.values, 0, gravityValues, 0, 3)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    // Copy magnetic field data
                    System.arraycopy(event.values, 0, magneticValues, 0, 3)

                    // If we have both accelerometer and magnetic data, calculate orientation
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)) {
                        updateOrientation()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSensorChanged: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not using accuracy changes
    }

    /**
     * Update orientation angles and bearing
     */
    private fun updateOrientation() {
        try {
            // Calculate orientation angles from rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Get azimuth (bearing) in degrees
            val azimuth = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360) % 360

            // Only update if bearing has changed significantly (reduce jitter)
            if (abs(_currentBearing.value - azimuth) > 1.0) {
                _currentBearing.value = azimuth
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating orientation: ${e.message}")
        }
    }

    /**
     * Update navigation state based on current location
     *
     * @param userLocation Current user location
     * @param destinationLocation Destination location
     * @param routeManager Route manager for route information
     */
    fun updateNavigation(
        userLocation: LocationDataOSRM,
        destinationLocation: LocationDataOSRM,
        routeManager: RouteManager
    ) {
        try {
            // Check for arrival
            val distanceToDest = LocationDataOSRM.distanceBetween(userLocation, destinationLocation)
            val hasArrived = distanceToDest < 20 // Consider arrived within 20 meters

            if (hasArrived != _isArrived.value) {
                _isArrived.value = hasArrived

                if (hasArrived) {
                    _currentInstruction.value = "Sie haben Ihr Ziel erreicht"
                    _remainingDistance.value = "Entfernung: 0 Meter"
                    return
                }
            }

            // Skip further updates if arrived
            if (_isArrived.value) return

            // Update instruction
            _currentInstruction.value = routeManager.getNavigationInstruction(
                userLocation,
                destinationLocation
            )

            // Update distance
            _remainingDistance.value = routeManager.getRemainingDistance(
                userLocation,
                destinationLocation
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating navigation: ${e.message}")
        }
    }

    /**
     * Clean up resources when done
     */
    fun cleanup() {
        stopOrientationTracking()
    }
}