package com.edu.ackline.feature.setup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupState

/**
 * Phase 0 setup/debug surface. Intentionally utilitarian: registration state,
 * device ID (FID), and the last fake test message. No production inbox UI.
 */
@Composable
fun SetupScreen() {
    val setupState by SetupState.state.collectAsState()
    val context = LocalContext.current

    var notificationGranted by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Ackline",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Push setup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        SetupRow(
            label = "Notification permission",
            value = if (notificationGranted) "Granted" else "Not granted",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) {
                Text("Request permission")
            }
        }

        SetupRow(
            label = "FCM registration",
            value = when (setupState.registrationState) {
                RegistrationState.Ready -> "Ready"
                RegistrationState.Waiting -> "Waiting"
                RegistrationState.Error -> "Error"
            },
        )

        val installationId = setupState.installationId
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupRow(
                label = "Device ID",
                value = installationId ?: "Waiting",
            )
            if (installationId != null) {
                OutlinedButton(
                    onClick = { copyDeviceId(context, installationId) },
                ) {
                    Text("Copy")
                }
            }
        }

        SetupRow(
            label = "Last test message",
            value = setupState.lastMessageSummary ?: "Waiting for a message",
        )
    }
}

@Composable
private fun SetupRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun copyDeviceId(context: Context, deviceId: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ackline Device ID", deviceId))
}