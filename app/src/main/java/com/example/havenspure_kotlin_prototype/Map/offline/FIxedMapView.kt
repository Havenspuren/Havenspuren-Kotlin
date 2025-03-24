package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.views.MapView

/**
 * FixedMapView is an extension of MapView that adds additional debugging
 * and fixes some common issues with tile loading and zooming
 */
class FixedMapView : MapView {

    private val TAG = "FixedMapView"

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        // Improve performance
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Set redraw frequency - use the correct methods
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false

        // Enable extra logging
        addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                event?.let {
                    Log.d(TAG, "Zoom changed to: ${it.zoomLevel}")
                    onZoomChanged(it.zoomLevel)
                }
                return false
            }
        })
    }

    /**
     * Override the tile loading behavior when zoom changes
     */
    private fun onZoomChanged(newZoom: Double) {
        // Force refresh when zoom level changes
        invalidate()

        // Log current zoom bounds
        Log.d(TAG, "Current zoom range: $minZoomLevel to $maxZoomLevel")

        // Check if current zoom is within valid range
        if (newZoom < minZoomLevel || newZoom > maxZoomLevel) {
            Log.w(TAG, "Zoom level $newZoom is outside valid range!")
        }

        // Check if we're currently using a custom tile source
        val tileSource = this.tileProvider.tileSource
        if (tileSource !is OnlineTileSourceBase) {
            Log.d(TAG, "Using custom tile source: ${tileSource.name()}")
        }
    }

    /**
     * Override to provide better error handling during drawing
     */
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        try {
            super.dispatchDraw(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Error in dispatchDraw: ${e.message}")
        }
    }

    /**
     * Set safer zoom bounds that match your available tiles
     */
    fun setSafeZoomLevels(min: Double, max: Double) {
        this.minZoomLevel = min
        this.maxZoomLevel = max

        // OSMDroid doesn't have a direct setZoomLimits method
        // The controller will respect the min/max values set on the MapView

        Log.d(TAG, "Set safe zoom levels: $min to $max")
    }
}