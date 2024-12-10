package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import androidx.compose.runtime.livedata.observeAsState

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase
    private val darkModeViewModel: DarkModeViewModel by lazy { DarkModeViewModel.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Room database
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app_database")
            .fallbackToDestructiveMigration()
            .build()

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
            val isDarkMode by darkModeViewModel.isDarkMode.observeAsState(
                initial = getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getBoolean("dark_mode", false)
            )
            MyApplicationTheme(darkTheme = isDarkMode) {
                MapScreen(fusedLocationClient, db)
            }
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


@SuppressLint("UnusedMaterialScaffoldPaddingParameter", "MissingPermission")
@Composable
fun MapScreen(fusedLocationClient: FusedLocationProviderClient, db: AppDatabase) {
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var buses by remember { mutableStateOf<List<Bus>>(emptyList()) }
    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var busRoutes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    var filteredBuses by remember { mutableStateOf<List<Bus>>(emptyList()) }
    var filteredBusStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var filteredRoutes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    val drawerState = rememberBottomSheetScaffoldState()

    // Search query state
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var searchQueryFilteredRoutes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }

    val context = LocalContext.current

    // Fetch buses, bus stops, and routes
    suspend fun fetchBuses(): List<Bus> {
        return try {
            val busResponse = RetrofitInstance.api.getBuses()
            busResponse.entity.map { entity ->
                Bus(
                    busId = entity.vehicle.vehicle.id,
                    routeId = entity.vehicle.trip.routeId,
                    latitude = entity.vehicle.position.latitude,
                    longitude = entity.vehicle.position.longitude
                )
            }
        } catch (e: Exception) {
            println("Error fetching buses: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchBusStops(): List<BusStop> {
        return try {
            val geoJson = loadGeoJsonFromAssets(context, "busstops.geojson")
            parseBusStopsFromGeoJson(geoJson)
        } catch (e: Exception) {
            println("Error fetching bus stops: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchRoutes(): List<BusRoute> {
        return try {
            val geoJson = loadGeoJsonFromAssets(context, "routes.geojson")
            parseBusRoutesFromGeoJson(geoJson)
        } catch (e: Exception) {
            println("Error fetching bus routes: ${e.message}")
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
            }
        }

        busStops = fetchBusStops()
        busRoutes = fetchRoutes()
        buses = fetchBuses()

        // Initially, only bus stops are visible
        filteredBusStops = busStops
        searchQueryFilteredRoutes = busRoutes
    }

    // Filter bus routes based on search query (only in the drawer)
    LaunchedEffect(searchQuery) {
        searchQueryFilteredRoutes = busRoutes.filter {
            it.routeNum.contains(searchQuery.text, ignoreCase = true) ||
                    it.routeTitle.contains(searchQuery.text, ignoreCase = true)
        }
    }

    // Handle route selection for filtering on map
    fun onRouteSelected(route: BusRoute) {
        // Filter buses based on the base route number (e.g., 7 for 7a, 7b, etc.)
        val baseRouteNum = route.routeNum.takeWhile { it.isDigit() }
        filteredRoutes = listOf(route)
        filteredBuses = buses.filter { it.routeId.startsWith(baseRouteNum) }
    }

    // Periodically update bus markers every 7 seconds
    LaunchedEffect(filteredRoutes) {
        while (true) {
            delay(7000)
            buses = fetchBuses()
            filteredBuses = buses.filter { bus ->
                filteredRoutes.any { route ->
                    bus.routeId.startsWith(route.routeNum.takeWhile { it.isDigit() })
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopMenuBar(
                onProfileClick = {
                    context.startActivity(Intent(context, ProfileActivity::class.java))
                },
                onSettingsClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }            )
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = drawerState,
            sheetContent = {
                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val maxHeight = (screenHeight * 5) / 8 // Calculate 5/8th of the screen height

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .background(color = MaterialTheme.colors.surface)
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

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query -> searchQuery = query },
                            label = { Text("Search Routes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Drawer content with filtered routes
                        DrawerContent(
                            filteredRoutes = searchQueryFilteredRoutes,
                            searchQuery = searchQuery.text,
                            onRouteSelected = { route -> onRouteSelected(route) },
                            db = db  // Pass db to DrawerContent
                        )
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
                            filteredRoutes.forEach { busRoute ->
                                busRoute.coordinates.forEach { segment ->
                                    val geoPoints = segment.map { (latitude, longitude) ->
                                        GeoPoint(latitude, longitude)
                                    }
                                    val polyline = org.osmdroid.views.overlay.Polyline()
                                    polyline.setPoints(geoPoints)
                                    polyline.color = android.graphics.Color.BLUE
                                    polyline.width = 5.0f
                                    polyline.title = busRoute.routeTitle
                                    mapView.overlays.add(polyline)
                                }
                            }

                            // Add bus markers for the filtered route
                            val busIcon by lazy { resizeDrawable(context, R.drawable.bus, 100, 100) }
                            filteredBuses.forEach { bus ->
                                val busMarker = Marker(mapView)
                                busMarker.position = GeoPoint(bus.latitude, bus.longitude)
                                busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                busMarker.title = "Bus ${bus.busId} on route ${bus.routeId}"
                                busMarker.icon = busIcon
                                mapView.overlays.add(busMarker)
                            }

                            true // Return true to allow the drawing to continue
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



@Composable
fun DrawerContent(
    filteredRoutes: List<BusRoute>,
    searchQuery: String,
    onRouteSelected: (BusRoute) -> Unit,
    db: AppDatabase
) {
    var savedRoutes by remember { mutableStateOf<List<SavedRoute>>(emptyList()) }

    // Fetch saved routes in a background thread using a suspend function
    LaunchedEffect(Unit) {
        // Run the database fetch operation in IO dispatcher to avoid blocking the main thread
        savedRoutes = withContext(Dispatchers.IO) {
            db.savedRouteDao().getAllSavedRoutes()
        }
    }

    // Function to refresh saved routes
    fun refreshSavedRoutes() {
        CoroutineScope(Dispatchers.IO).launch {
            savedRoutes = db.savedRouteDao().getAllSavedRoutes()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Display saved routes
        item {
            Text(
                text = "Saved Routes",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(savedRoutes) { savedRoute ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp)
                    .clickable {
                        // Find the corresponding route using savedRoute
                        val selectedRoute = filteredRoutes.find { it.routeNum == savedRoute.routeNum }!!
                        onRouteSelected(selectedRoute)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${savedRoute.routeNum}: ${savedRoute.routeTitle}",
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(onClick = {
                        // Delete the route from the database
                        CoroutineScope(Dispatchers.IO).launch {
                            db.savedRouteDao().delete(savedRoute)
                            refreshSavedRoutes()  // Refresh the saved routes list
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Route")
                    }
                }
            }
        }

        item {
            Text(
                text = "All Routes",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Display the filtered routes
        items(filteredRoutes) { route ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp)
                    .clickable {
                        onRouteSelected(route)
                    },
                shape = MaterialTheme.shapes.medium,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${route.routeNum}: ${route.routeTitle}",
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(onClick = {
                        // Save the route to the database within a coroutine
                        CoroutineScope(Dispatchers.IO).launch {
                            val savedRoute = SavedRoute(
                                routeNum = route.routeNum,
                                routeTitle = route.routeTitle
                            )
                            db.savedRouteDao().insert(savedRoute)
                            refreshSavedRoutes()  // Refresh the saved routes list
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "Save Route")
                    }
                }
            }
        }
    }
}