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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings



class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load configuration for OSMdroid
        Configuration.getInstance().load(
            applicationContext,
            android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )

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

@SuppressLint("MissingPermission", "UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MapScreen(fusedLocationClient: FusedLocationProviderClient) {
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var buses by remember { mutableStateOf<List<Bus>>(emptyList()) }
    val drawerState = rememberBottomSheetScaffoldState()

    // Search query state
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Function to fetch buses
    suspend fun fetchBusesAndUpdateMarkers() {
        try {
            val busResponse = RetrofitInstance.api.getBuses()
            buses = busResponse.entity.map { entity ->
                Bus(
                    busId = entity.vehicle.vehicle.id,
                    routeId = entity.vehicle.trip.routeId,
                    latitude = entity.vehicle.position.latitude,
                    longitude = entity.vehicle.position.longitude
                )
            }
        } catch (e: Exception) {
            println("Error fetching buses: ${e.message}")
        }
    }

    // Request the current location
    LaunchedEffect(Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
            }
        }

        // Periodically fetch and update buses
        while (true) {
            fetchBusesAndUpdateMarkers()
            delay(7000) // Update every 7 seconds
        }
    }

    // Main Scaffold
    Scaffold(
        topBar = {
            TopMenuBar(
                onProfileClick = {
                    println("Profile button clicked!") // Replace with actual navigation logic
                },
                onSettingsClick = {
                    println("Settings button clicked!") // Replace with actual navigation logic
                }
            )
        }
    ) {
        // Bottom Sheet Layout
        BottomSheetScaffold(
            scaffoldState = drawerState,
            sheetContent = {
                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val maxHeight = (screenHeight * 5) / 8 // Calculate 5/8th of the screen height

                // Drawer content with height restricted to 5/8 of the screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight) // Limit height to a maximum of 5/8 screen
                        .background(color = MaterialTheme.colors.surface) // Plain background
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column {
                        // Dashed line indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DashedLineIndicator()
                        }

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Drawer content
                        Text("Nearby Buses:", style = MaterialTheme.typography.h6)
                        Spacer(modifier = Modifier.height(8.dp))
                        buses.forEach { bus ->
                            Text("Bus ${bus.busId}: Route ${bus.routeId}")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            sheetPeekHeight = 90.dp // Visible height for the search bar
        ) {
            // Main content (MapView)
            currentLocation?.let { currentLoc ->
                AndroidView(
                    factory = { context ->
                        // Initialize MapView
                        val mapView = MapView(context)
                        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                        mapView.setMultiTouchControls(true)

                        // Set the map's initial position to the current location
                        val mapController = mapView.controller
                        mapController.setZoom(18.0)
                        mapController.setCenter(currentLoc)

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
                                busMarker.title = "Bus ${bus.busId}: Route ${bus.routeId}"

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
    }
}

@Composable
fun DashedLineIndicator() {
    Canvas(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
    ) {
        val dashLength = 5.dp.toPx()
        val dashSpacing = 3.dp.toPx()
        var currentX = 0f
        val totalWidth = size.width

        while (currentX < totalWidth) {
            drawLine(
                color = Color.Gray,
                start = Offset(currentX, size.height / 2),
                end = Offset(currentX + dashLength, size.height / 2),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            currentX += dashLength + dashSpacing
        }
    }
}

@Composable
fun TopMenuBar(onProfileClick: () -> Unit, onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = "MyWay Transit")
        },
        navigationIcon = {
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile"
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    )
}

// Helper function to resize drawable
fun resizeDrawable(context: Context, drawableRes: Int, width: Int, height: Int): Drawable {
    val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
    return BitmapDrawable(context.resources, scaledBitmap)
}