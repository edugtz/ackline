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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.edu.ackline.AcklineApplication
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupState
import com.edu.ackline.ui.AcklineTopBar

/**
 * User-facing "Ajustes" screen (Phase 9 Change C).
 *
 * Presents the same app/setup state as the former technical bootstrap
 * surface — notification permission, push registration, encryption state,
 * Device ID (FID) and last test message — grouped into a coherent settings
 * layout using the Ackline teal visual system. Presentation only: no state
 * semantics or behavior were added or changed.
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcklineTopBar(title = "Ajustes", onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ── Status summary — honest aggregate of the rows below only.
            val registrationReady =
                setupState.registrationState == RegistrationState.Ready
            val allReady = notificationGranted &&
                registrationReady &&
                setupState.installationId != null &&
                setupState.encryptionReady &&
                !setupState.rePairRequired

            SetupSection(title = "Estado") {
                Text(
                    text = "Estado de Ackline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        allReady -> "Todo listo"
                        setupState.registrationState == RegistrationState.Error ->
                            "Pendiente — error en el registro push"
                        else -> "Pendiente"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (allReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!allReady) {
                    Text(
                        text = "Revisa las secciones marcadas como pendientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Notifications
            SetupSection(title = "Notificaciones") {
                StatusRow(
                    label = "Permiso de notificaciones",
                    value = if (notificationGranted) "Permitido" else "No permitido",
                    ok = notificationGranted,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    ) {
                        Text("Solicitar permiso")
                    }
                }
                StatusRow(
                    label = "Registro push",
                    value = when (setupState.registrationState) {
                        RegistrationState.Ready -> "Listo"
                        RegistrationState.Waiting -> "Esperando"
                        RegistrationState.Error -> "Error"
                    },
                    ok = registrationReady,
                )
            }

            // ── Security
            SetupSection(title = "Seguridad") {
                StatusRow(
                    label = "Cifrado",
                    value = if (setupState.encryptionReady) "Listo" else "No configurado",
                    ok = setupState.encryptionReady,
                )
            }

            // ── Device
            SetupSection(title = "Dispositivo") {
                val installationId = setupState.installationId
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ID del dispositivo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = installationId ?: "Esperando",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (installationId != null) {
                            OutlinedButton(
                                onClick = { copyDeviceId(context, installationId) },
                            ) {
                                Text("Copiar")
                            }
                        }
                    }
                }
                if (setupState.rePairRequired) {
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

            // ── Diagnostics — deliberately quieter than the sections above.
            SetupSection(title = "Diagnóstico", quiet = true) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Última prueba",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = setupState.lastMessageSummary ?: "Esperando un mensaje",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Section pattern for Ajustes: compact uppercase section label above one
 * tonal card. `quiet` sections recede a layer (used for diagnostics).
 */
@Composable
private fun SetupSection(
    title: String,
    quiet: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (quiet) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/**
 * Read-only status row: label + text value. Status is expressed with text,
 * never color alone. No switches — this screen only reads state (plus the
 * existing permission-request action).
 */
@Composable
private fun StatusRow(
    label: String,
    value: String,
    ok: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
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
