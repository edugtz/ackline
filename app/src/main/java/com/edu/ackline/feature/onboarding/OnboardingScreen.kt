package com.edu.ackline.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edu.ackline.SetupUiState
import com.edu.ackline.feature.pairing.PairingPresentation
import com.edu.ackline.feature.pairing.PairingViewModel
import com.edu.ackline.ui.AcklineTopBar

internal enum class OnboardingCompletion { Ready, Incomplete, Pending }
internal fun onboardingCompletion(setup: SetupUiState, success: Boolean): OnboardingCompletion = when {
    !success || !setup.hasConfirmedPairing -> OnboardingCompletion.Pending
    setup.fullyReady -> OnboardingCompletion.Ready
    else -> OnboardingCompletion.Incomplete
}

@Composable
internal fun OnboardingScreen(setup: SetupUiState, pairing: PairingViewModel, onInbox: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val presentation by pairing.presenter.state.collectAsState()
    val permission = rememberNotificationPermissionAction { step = 2 }
    LaunchedEffect(step, setup) {
        if (step == 2) pairing.presenter.refresh()
    }
    val completion = onboardingCompletion(setup, presentation == PairingPresentation.Success)
    val title = when {
        completion == OnboardingCompletion.Ready -> "Listo"
        completion == OnboardingCompletion.Incomplete -> "Hermes conectado"
        step == 0 -> "Bienvenido"
        step == 1 -> "Notificaciones"
        else -> "Conectar con Hermes"
    }
    Scaffold(topBar = { AcklineTopBar(title = "Ackline") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            when {
                completion == OnboardingCompletion.Ready -> {
                    Text("Notificaciones activadas. Conectado con Hermes y cifrado listo.", style = MaterialTheme.typography.bodyLarge)
                    PrimaryAction("Ir a Mis alertas", onInbox)
                }
                completion == OnboardingCompletion.Incomplete -> {
                    Text("El emparejamiento se completó. La configuración aún está pendiente.", style = MaterialTheme.typography.bodyLarge)
                    if (!setup.notificationGranted) {
                        Text("Activa las notificaciones para ver las alertas de Hermes.")
                        PrimaryAction(permission.label, permission.launch)
                    }
                    PrimaryAction("Ir a Mis alertas", onInbox)
                }
                step == 0 -> {
                    Text("Tu bandeja personal para las alertas de Hermes.", style = MaterialTheme.typography.bodyLarge)
                    Text("Activa las notificaciones, conecta con Hermes y recibe tus alertas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("Empezar") { step = 1 }
                }
                step == 1 -> {
                    Text("Permite que Ackline te avise cuando llegue una alerta de Hermes.", style = MaterialTheme.typography.bodyLarge)
                    if (setup.notificationGranted) {
                        PrimaryAction("Continuar") { step = 2 }
                    } else {
                        PrimaryAction(permission.label, permission.launch)
                        TextButton(onClick = { step = 2 }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text("Continuar sin notificaciones")
                        }
                    }
                }
                else -> when (val state = presentation) {
                    PairingPresentation.Idle, PairingPresentation.WaitingForRegistration -> {
                        CircularProgressIndicator()
                        Text("Preparando la conexión…")
                        Text("Si tarda, comprueba tu conexión y vuelve a abrir Ackline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PairingPresentation.TailscaleRequired -> {
                        Text("Activa Tailscale en este teléfono para emparejar. Ackline usa Tailscale para hablar con tu Hermes de forma privada.")
                        PrimaryAction("Comprobar conexión", pairing.presenter::refresh)
                    }
                    PairingPresentation.ReadyToScan -> {
                        Text("El QR de tu Mac conectará Ackline con Hermes.", style = MaterialTheme.typography.bodyLarge)
                        Text("El escáner estará disponible en la siguiente actualización.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {}, enabled = false, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Escanear QR") }
                    }
                    PairingPresentation.Scanning -> Text("Buscando el QR de Ackline…")
                    PairingPresentation.Pairing -> { CircularProgressIndicator(); Text("Emparejando…") }
                    is PairingPresentation.Error -> {
                        Text(state.error.message, color = MaterialTheme.colorScheme.error)
                        PrimaryAction("Volver a conectar", pairing.presenter::retryScan)
                    }
                    PairingPresentation.Success -> Text("Actualizando el estado…")
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) { Text(label) }
}
