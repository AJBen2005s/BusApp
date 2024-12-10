package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.livedata.observeAsState
import android.util.Log

class ProfileActivity : ComponentActivity() {
    private val darkModeViewModel: DarkModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkMode by darkModeViewModel.isDarkMode.observeAsState(
                initial = getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getBoolean("dark_mode", false)
            )
            MyApplicationTheme(darkTheme = isDarkMode) {
                ProfileScreen()
            }
        }
    }
}


@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
    var userName by remember { mutableStateOf(sharedPreferences.getString("user_name", "User") ?: "User") }
    var isEditing by remember { mutableStateOf(false) }
    var selectedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Load saved profile picture from file
    LaunchedEffect(Unit) {
        val savedFileName = sharedPreferences.getString("profile_picture_file", null)
        savedFileName?.let { fileName ->
            val file = context.getFileStreamPath(fileName)
            if (file.exists()) {
                selectedBitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = decodeBitmapFromUri(context, it)
            if (bitmap != null) {
                selectedBitmap = bitmap
                saveBitmapToFile(context, bitmap, "profile_picture.png")
                // Save the file name in SharedPreferences
                sharedPreferences.edit().putString("profile_picture_file", "profile_picture.png").apply()
            }
        }
    }

    Scaffold(
        topBar = {
            ProfileTopMenuBar {
                (context as? ComponentActivity)?.finish()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    TextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Enter your name") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        isEditing = false
                        // Save the user name to SharedPreferences
                        sharedPreferences.edit().putString("user_name", userName).apply()
                    }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save Name")
                    }
                } else {
                    Text(text = "Hi, $userName", style = MaterialTheme.typography.h4)
                    IconButton(onClick = { isEditing = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Name")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Select Profile Picture", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            selectedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { selectImageLauncher.launch("image/*") }) {
                Text(text = "Select Image")
            }
        }
    }
}

// Helper function to decode bitmap from Uri
fun decodeBitmapFromUri(context: Context, uri: Uri): android.graphics.Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        Log.e("ProfileScreen", "Failed to decode bitmap: ${e.message}")
        null
    }
}

// Helper function to save bitmap to a file
fun saveBitmapToFile(context: Context, bitmap: android.graphics.Bitmap, fileName: String) {
    try {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        }
    } catch (e: Exception) {
        Log.e("ProfileScreen", "Failed to save bitmap: ${e.message}")
    }
}


@Composable
fun ProfileTopMenuBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = "User Profile")
        },
        actions = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Back"
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    )
}