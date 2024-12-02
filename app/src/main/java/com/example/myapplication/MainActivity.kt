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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.views.overlay.Polyline
import java.io.IOException


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
    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var busRoutes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    val drawerState = rememberBottomSheetScaffoldState()

    // Search query state
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val context = LocalContext.current

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

    // Function to fetch GeoJSON data from the assets folder
    suspend fun fetchBusStopsAndUpdateMarkers() {
        try {
            // Read the GeoJSON file from assets
            val geoJson = loadGeoJsonFromAssets(context, "busstops.geojson")

            // Parse the GeoJSON and update bus stops
            busStops = parseBusStopsFromGeoJson(geoJson)
        } catch (e: Exception) {
            println("Error fetching bus stops: ${e.message}")
        }
    }

    // Function to fetch GeoJSON data from the assets folder
    suspend fun fetchRoutesAndUpdateMarkers() {
        try {
            // Read the GeoJSON file from assets
            val geoJson = loadGeoJsonFromAssets(context, "routes.geojson")

            // Parse the GeoJSON and update bus stops
            busRoutes = parseBusRoutesFromGeoJson(geoJson)
        } catch (e: Exception) {
            println("Error fetching bus routes: ${e.message}")
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
            fetchBusStopsAndUpdateMarkers()
            fetchRoutesAndUpdateMarkers()
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

                // Drawer content with height restricted to 5/8 of the screen, but scrollable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight) // Limit height to a maximum of 5/8 screen
                        .background(color = MaterialTheme.colors.surface) // Plain background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Dashed line indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DashedLineIndicator()
                        }

                        // Make the content scrollable
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                        ) {
                            // Search bar
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Drawer content
                            item {
                                Text("Nearby Buses:", style = MaterialTheme.typography.h6)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Add each bus as a separate item
                            items(buses) { bus ->
                                Text("Bus ${bus.busId}: Route ${bus.routeId}")
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Optional: Add a dummy footer or extra space if needed
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("End of buses list", style = MaterialTheme.typography.body2)
                            }
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

                        mapView.viewTreeObserver.addOnPreDrawListener {
                            // Clear previous overlays
                            mapView.overlays.clear()

                            // Add user marker
                            val userIcon = resizeDrawable(context, R.drawable.user, 100, 100) // Adjust size here
                            val userMarker = Marker(mapView)
                            userMarker.position = currentLoc
                            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            userMarker.title = "You are here"
                            userMarker.icon = userIcon
                            mapView.overlays.add(userMarker)

                            // Add bus stop markers
                            val busStopIcon by lazy { resizeDrawable(context, R.drawable.busstop, 100, 100) }
                            busStops.forEach { busStop ->
                                val marker = Marker(mapView)
                                marker.position = GeoPoint(busStop.latitude, busStop.longitude)
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                marker.title = busStop.name
                                marker.icon = busStopIcon
                                mapView.overlays.add(marker)
                            }

                            // Add bus routes
                            busRoutes.forEach { busRoute ->
                                busRoute.coordinates.forEach { segment ->
                                    // Convert each segment to GeoPoints
                                    val geoPoints = segment.map { (latitude, longitude) ->
                                        GeoPoint(latitude, longitude)
                                    }

                                    // Create a polyline
                                    val polyline = org.osmdroid.views.overlay.Polyline()
                                    polyline.setPoints(geoPoints)
                                    polyline.color = android.graphics.Color.BLUE // Set the line color
                                    polyline.width = 5.0f // Set line width
                                    polyline.title = busRoute.routeTitle

                                    // Add polyline to the map
                                    mapView.overlays.add(polyline)
                                }
                            }

                            // Add bus markers
                            val busIcon by lazy { resizeDrawable(context, R.drawable.bus, 100, 100) }
                            buses.forEach { bus ->
                                val busLocation = GeoPoint(bus.latitude, bus.longitude)

                                val busMarker = Marker(mapView)
                                busMarker.position = busLocation
                                busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                busMarker.title = "Bus ${bus.busId}: Route ${bus.routeId}"
                                busMarker.icon = busIcon

                                mapView.overlays.add(busMarker)
                            }

                            // Redraw the map
                            mapView.invalidate()
                            true // Allow map to draw
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

// Function to load the GeoJSON file from the assets folder
private suspend fun loadGeoJsonFromAssets(context: Context, fileName: String): String {
    return withContext(Dispatchers.IO) {
        try {
            // Open the GeoJSON file from assets
            val inputStream = context.assets.open(fileName)

            // Read the file content into a string
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (e: IOException) {
            throw Exception("Error loading GeoJSON file from assets: ${e.message}")
        }
    }
}

// Function to parse GeoJSON and extract bus stops
fun parseBusStopsFromGeoJson(geoJson: String): List<BusStop> {
    val busStops = mutableListOf<BusStop>()

    try {
        val jsonObject = JSONObject(geoJson)
        val features = jsonObject.getJSONArray("features")

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            // Extract necessary details
            val id = properties.getString("BUSSTOPID")
            val name = properties.getString("LOCATION")
            val accessibility = properties.getString("ACCESSIBLE")
            val latitude = coordinates.getDouble(1) // Latitude is at index 1
            val longitude = coordinates.getDouble(0) // Longitude is at index 0

            // Create BusStop object
            busStops.add(BusStop(id, name, accessibility, latitude, longitude))
        }
    } catch (e: Exception) {
        println("Error parsing GeoJSON: ${e.message}")
    }

    return busStops
}

// Function to parse GeoJSON and extract bus routes
fun parseBusRoutesFromGeoJson(geoJson: String): List<BusRoute> {
    val busRoutes = mutableListOf<BusRoute>()

    try {
        val jsonObject = JSONObject(geoJson)
        val features = jsonObject.getJSONArray("features")

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")

            // Check if the route has a ROUTE_NUM
            if (properties.has("ROUTE_NUM")) {
                val routeNum = properties.getString("ROUTE_NUM")
                val routeTitle = properties.getString("TITLE")

                // Extract coordinates (MultiLineString) for the route
                val coordinates = geometry.getJSONArray("coordinates")
                val routeCoordinates = mutableListOf<List<Pair<Double, Double>>>()

                // Iterate over each path segment
                for (j in 0 until coordinates.length()) {
                    val segment = coordinates.getJSONArray(j)
                    val segmentCoordinates = mutableListOf<Pair<Double, Double>>()

                    // Each segment contains multiple points
                    for (k in 0 until segment.length()) {
                        val point = segment.getJSONArray(k)
                        val longitude = point.getDouble(0)
                        val latitude = point.getDouble(1)
                        segmentCoordinates.add(Pair(latitude, longitude))
                    }

                    routeCoordinates.add(segmentCoordinates)
                }

                // Create a BusRoute object and add it to the list
                busRoutes.add(BusRoute(routeNum, routeTitle, routeCoordinates))
            }
        }
    } catch (e: Exception) {
        println("Error parsing GeoJSON: ${e.message}")
    }

    return busRoutes
}
