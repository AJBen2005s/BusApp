package com.example.myapplication

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load configuration for OSMdroid
        Configuration.getInstance().load(applicationContext, android.preference.PreferenceManager.getDefaultSharedPreferences(this))

        // Request location permissions
        requestLocationPermissions()

        setContent {
            MapScreen(fusedLocationClient)
        }
    }

    private fun requestLocationPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        }
    }
}

@Composable
fun MapScreen(fusedLocationClient: FusedLocationProviderClient) {
    // State to hold the current location
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }

    // Request the current location
    LaunchedEffect(Unit) {
        // Check and get location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                // Update the current location
                currentLocation = GeoPoint(it.latitude, it.longitude)
            }
        }
    }

    // If current location is available, show the map
    currentLocation?.let {
        AndroidView(
            factory = { context ->
                // Initialize MapView
                val mapView = MapView(context)
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                mapView.setBuiltInZoomControls(true)
                mapView.setMultiTouchControls(true)

                // Set the map's initial position to the current location
                val mapController = mapView.controller
                mapController.setZoom(18.0)
                mapController.setCenter(it)

                // Add a marker at the current location
                val marker = Marker(mapView)
                marker.position = it
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "You are here"
                mapView.overlays.add(marker)

                mapView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

