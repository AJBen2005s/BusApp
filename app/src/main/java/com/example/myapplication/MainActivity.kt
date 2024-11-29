package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.delay

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

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(fusedLocationClient: FusedLocationProviderClient) {
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var buses by remember { mutableStateOf<List<Bus>>(emptyList()) }

    val scope = rememberCoroutineScope()

    // Function to fetch buses and update markers
    suspend fun fetchBusesAndUpdateMarkers() {
        try {
            val busResponse = RetrofitInstance.api.getBuses()
            println("API Response: Fetched buses: ${busResponse.entity}")  // Log the entity array
            buses = busResponse.entity.map { entity ->
                Bus(
                    busId = entity.vehicle.vehicle.id,
                    routeId = entity.vehicle.trip.routeId,
                    latitude = entity.vehicle.position.latitude,
                    longitude = entity.vehicle.position.longitude
                )
            }

            // Log the buses' positions
            buses.forEach { bus ->
                println("Bus ${bus.busId}: (${bus.latitude}, ${bus.longitude})")
            }
        } catch (e: Exception) {
            println("Error fetching buses: ${e.message}")
        }
    }

    // Request the current location
    LaunchedEffect(Unit) {
        // Check and get location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                // Update the current location
                currentLocation = GeoPoint(it.latitude, it.longitude)
            }
        }

        // Fetch buses initially
        fetchBusesAndUpdateMarkers()

        // Periodically fetch and update buses every 5 seconds
        while (true) {
            delay(7000) // Delay for 5 seconds
            fetchBusesAndUpdateMarkers()
        }
    }

    // If current location is available, show the map
    currentLocation?.let { currentLoc ->
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
                mapController.setCenter(currentLoc)

                fun resizeDrawable(context: Context, drawableRes: Int, width: Int, height: Int): Drawable {
                    // Load the drawable as a Bitmap
                    val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)

                    // Resize the bitmap
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)

                    // Convert Bitmap to Drawable
                    return BitmapDrawable(context.resources, scaledBitmap)
                }

                // Add a marker at the current location
                val userIcon = resizeDrawable(context, R.drawable.user, 100, 100) // Adjust the size here

                val userMarker = Marker(mapView)
                userMarker.position = currentLoc
                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                userMarker.title = "You are here"

                userMarker.icon = userIcon
                mapView.overlays.add(userMarker)

                // Use onGlobalLayoutListener to ensure map has been laid out
                mapView.viewTreeObserver.addOnPreDrawListener {
                    // Clear previous markers
                    mapView.overlays.clear()

                    // Add user marker again (in case of re-layout)
                    mapView.overlays.add(userMarker)

                    // Add bus markers
                    buses.forEach { bus ->
                        val busLocation = GeoPoint(bus.latitude, bus.longitude)
                        val busMarker = Marker(mapView)
                        busMarker.position = busLocation
                        busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        busMarker.title = "Bus ${bus.busId}, Route ${bus.routeId}"

                        // Add the marker to the map
                        mapView.overlays.add(busMarker)
                    }

                    // Force a redraw of the map to ensure the markers appear
                    mapView.invalidate()
                    true // Allow the map to be drawn
                }

                mapView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}





