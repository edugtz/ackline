package com.edu.ackline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.edu.ackline.ui.AcklineApp
import com.edu.ackline.ui.theme.AcklineTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the FID registration flow is active so the setup surface
        // receives the Device ID through AcklineMessagingService.onRegistered().
        FirebaseMessaging.getInstance()
            .register()
            .addOnFailureListener { SetupState.onRegistrationError(it) }

        setContent {
            AcklineTheme {
                AcklineApp()
            }
        }
    }
}
