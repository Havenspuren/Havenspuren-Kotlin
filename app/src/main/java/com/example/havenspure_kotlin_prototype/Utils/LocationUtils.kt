package com.example.havenspure_kotlin_prototype.Utils


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.havenspure_kotlin_prototype.Data.LocationData
import com.example.havenspure_kotlin_prototype.ViewModels.LocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

class LocationUtils(val context: Context) {

    private val _fusedLocationClient: FusedLocationProviderClient
            = LocationServices.getFusedLocationProviderClient(context)


    @SuppressLint("MissingPermission")
    fun requestLocationUpdates(viewModel: LocationViewModel){
        val locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    val location = LocationData(latitude = it.latitude, longitude =  it.longitude)
                    viewModel.updateLocation(location)
                    reverseGeocodeLocation(viewModel)
                }
            }
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000).build()

        _fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
    @SuppressLint("MissingPermission")
    fun getLastKnownLocationAndSetupUpdates(viewModel: LocationViewModel) {
        _fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val locationData = LocationData(latitude = location.latitude, longitude = location.longitude)
                viewModel.updateLocation(locationData)
                reverseGeocodeLocation(viewModel)
            }
            // Set up continuous location updates for future updates
            requestLocationUpdates(viewModel)
        }
    }


    fun hasLocationPermission(context: Context):Boolean{

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    }


    fun reverseGeocodeLocation( viewmodel : LocationViewModel){
        val geocoder = Geocoder(context, Locale.getDefault())
        val location : LocationData? = viewmodel.location.value
        val coordinate = location?.let { it1 -> LatLng(it1.latitude, it1.longitude) }
        val addresses:MutableList<Address>? =
            coordinate?.let { geocoder.getFromLocation(it.latitude, coordinate.longitude, 1) }
         if(addresses?.isNotEmpty() == true){
             viewmodel.updateAddress(addresses[0].getAddressLine(0))
        }else{
            "Address not found"
        }
    }

}