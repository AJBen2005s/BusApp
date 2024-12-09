package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.viewModels
import androidx.compose.runtime.livedata.observeAsState


class SettingsActivity : ComponentActivity() {
    private val darkModeViewModel: DarkModeViewModel by lazy { DarkModeViewModel.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(darkModeViewModel)
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun SettingsScreen(darkModeViewModel: DarkModeViewModel) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    var isDarkMode by rememberSaveable { mutableStateOf(sharedPreferences.getBoolean("dark_mode", false)) }

    darkModeViewModel.isDarkMode.observeAsState().value?.let { isDarkMode = it }

    MyApplicationTheme(darkTheme = isDarkMode) {
        Scaffold(
            topBar = {
                SettingsTopMenuBar {
                    (context as? ComponentActivity)?.finish()
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(text = "Settings", style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dark Mode")
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            isDarkMode = it
                            sharedPreferences.edit().putBoolean("dark_mode", isDarkMode).apply()
                            darkModeViewModel.setDarkMode(isDarkMode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTopMenuBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = "Settings")
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    )
}