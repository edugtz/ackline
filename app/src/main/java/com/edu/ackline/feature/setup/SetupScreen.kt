package com.edu.ackline.feature.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edu.ackline.feature.onboarding.rememberNotificationPermissionAction
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupState
import com.edu.ackline.ui.AcklineTopBar

/** Readiness, server-confirmed re-pair entry, and quiet opt-in diagnostics. */
@Composable
fun SetupScreen(onBack: (() -> Unit)? = null, onRePair: () -> Unit) {
    val setupState by SetupState.state.collectAsState()
    val context = LocalContext.current

    val notificationGranted = setupState.notificationGranted
    val permission = rememberNotificationPermissionAction()

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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Status summary — honest aggregate of the rows below only.
            val registrationReady =
                setupState.registrationState == RegistrationState.Ready
            val allReady = setupState.fullyReady

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
                if (!notificationGranted) {
                    Button(
                        onClick = permission.launch,
                    ) {
                        Text(permission.label)
                    }
                    TextButton(onClick = permission.openSettings, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("Ajustes de notificaciones")
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

            if (setupState.hasConfirmedPairing && setupState.rePairRequired) {
                SetupSection(title = "Conexión con Hermes") {
                    Text("Necesitas volver a conectar Ackline con Hermes.")
                    Button(onClick = onRePair, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("Emparejar nuevamente")
                    }
                }
            }

            // ── Diagnostics — deliberately quieter than the sections above.
            SetupSection(title = "Diagnóstico", quiet = true) {
                // Kept only as a collapsed diagnostic affordance, never a setup instruction.
                var showId by remember { mutableStateOf(false) }
                TextButton(onClick = { showId = !showId }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(if (showId) "Ocultar identificador de diagnóstico" else "Identificador de diagnóstico")
                }
                if (showId) {
                    setupState.installationId?.let { id ->
                        Text(id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        OutlinedButton(onClick = { copyDeviceId(context, id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text("Copiar")
                        }
                    }
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

private fun copyDeviceId(context: Context, deviceId: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ackline Device ID", deviceId))
}
