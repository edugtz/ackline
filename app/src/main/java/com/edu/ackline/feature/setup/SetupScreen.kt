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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.edu.ackline.AcklineApplication
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupState

/**
 * Phase 0 setup/debug surface. Intentionally utilitarian: registration state,
 * device ID (FID), and the last fake test message. No production inbox UI.
 */
@Composable
fun SetupScreen(onBack: (() -> Unit)? = null) {
    val setupState by SetupState.state.collectAsState()
    val context = LocalContext.current

    var notificationGranted by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    var rePairUpdateErrorMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text("Volver")
                }
            }
            Text(
                text = "Ackline",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
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

            SetupRow(
                label = "Cifrado",
                value = if (setupState.encryptionReady) "Listo" else "No configurado",
            )

            val installationId = setupState.installationId
            Column {
                Text(
                    text = "Device ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = installationId ?: "Waiting",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (installationId != null) {
                        OutlinedButton(
                            onClick = { copyDeviceId(context, installationId) },
                        ) {
                            Text("Copy")
                        }
                    }
                }
                if (setupState.rePairRequired) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "El Device ID cambió. Actualiza ackline-fid en Hermes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            val updated = (context.applicationContext as AcklineApplication)
                                .markRePairUpdated()
                            rePairUpdateErrorMessage = if (updated) {
                                null
                            } else {
                                "No se pudo guardar el cambio. Intenta de nuevo."
                            }
                        },
                    ) {
                        Text("Marcar como actualizado")
                    }
                    rePairUpdateErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SetupRow(
                label = "Last test message",
                value = setupState.lastMessageSummary ?: "Waiting for a message",
            )
        }
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
            color = MaterialTheme.colorScheme.onSurface,
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
