package com.example.havenspure_kotlin_prototype.ViewModels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.havenspure_kotlin_prototype.data.LocationData

class LocationViewModel: ViewModel() {
    private val _location = mutableStateOf<LocationData?>(null)
    val location: State<LocationData?> = _location

    private val _address = mutableStateOf<String?>("")
    val address : State<String?> = _address

    fun updateLocation(newLocation: LocationData){
        _location.value = newLocation
    }
   fun updateAddress(address:String){
        _address.value=address
   }

}
