package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install the splash screen (This handles the splash screen animation)
        installSplashScreen()

        // Set the splash screen content
        setContent {
            SplashScreenContent()
        }

        // Navigate to the main activity after a delay of 4 seconds
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()  // Finish SplashScreenActivity so it doesn't remain in the back stack
        }, 4000)  // Delay for 4 seconds
    }
}

@Composable
fun SplashScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image (Logo)
        Image(
            painter = painterResource(id = R.drawable.logo),  // Replace with your logo image
            contentDescription = "App Logo",
            modifier = Modifier.size(150.dp)  // Adjust the size of the logo
        )

        // App Name
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MyWay Transit",  // Replace with your app's name
            fontSize = 24.sp,
            color = Color.Black
        )
    }
}