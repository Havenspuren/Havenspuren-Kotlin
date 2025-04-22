package com.example.havenspure_kotlin_prototype.OSRM.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.havenspure_kotlin_prototype.OSRM.viewmodel.MapViewModel
import com.example.havenspure_kotlin_prototype.navigation.TourNavigator
import org.osmdroid.views.MapView

@Composable
fun MapLifecycleEffects(
    lifecycleOwner: LifecycleOwner,
    mapView: MapView,
    mapViewModel: MapViewModel,
    tourNavigator: TourNavigator,
    isNavigating: Boolean,
    hasPlayedAudio: Boolean,
    onResetAudioFlag: () -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    // Log that map is resumed
                    android.util.Log.d("MapLifecycleEffects", "Lifecycle event: ON_RESUME - Resuming map functionality")
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Ensure cleanup of resources
                    if (isNavigating) {
                        mapViewModel.stopNavigation()
                    }
                    // Stop audio if it's playing
                    if (hasPlayedAudio) {
                        tourNavigator.stopAudio()
                        onResetAudioFlag()
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
            mapViewModel.cleanupMapResources(mapView)
        }
    }
}