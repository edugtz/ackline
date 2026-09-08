package com.edu.ackline.feature.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.edu.ackline.SetupUiState
import com.edu.ackline.ui.AcklineTopBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edu.ackline.feature.pairing.scanner.QrScanner

/** The same scan/claim surface is used by first pairing and Ajustes re-pair. */
@Composable
internal fun PairingContent(state: PairingPresentation, presenter: PairingPresenter) {
    when (state) {
        PairingPresentation.Idle, PairingPresentation.WaitingForRegistration -> {
            CircularProgressIndicator()
            Text("Preparando la conexión…", modifier = Modifier.pairingStatusSemantics())
            Text("Si tarda, comprueba tu conexión y vuelve a abrir Ackline.")
        }
        PairingPresentation.TailscaleRequired -> {
            Text(
                "Activa Tailscale en este teléfono para emparejar. Ackline usa Tailscale para hablar con tu Hermes de forma privada.",
                modifier = Modifier.pairingStatusSemantics(),
            )
            PairingButton("Comprobar conexión", presenter::refresh)
        }
        PairingPresentation.ReadyToScan -> {
            Text(
                "El QR de tu Mac conectará Ackline con Hermes.",
                modifier = Modifier.pairingStatusSemantics(),
                style = MaterialTheme.typography.bodyLarge,
            )
            PairingButton("Escanear QR", presenter::beginScan)
        }
        PairingPresentation.Scanning -> QrScanner(presenter::acceptScannedQr, presenter::cancelScan)
        PairingPresentation.Pairing -> {
            CircularProgressIndicator()
            Text("Emparejando…", modifier = Modifier.pairingStatusSemantics())
        }
        is PairingPresentation.Error -> {
            Text(
                state.error.message,
                modifier = Modifier.pairingStatusSemantics(),
                color = MaterialTheme.colorScheme.error,
            )
            PairingButton(when (state.error.action) {
                PairingErrorAction.NewQr -> "Escanear un nuevo QR"
                PairingErrorAction.ReplacementQr -> "Escanear QR de reemplazo"
                else -> "Volver a conectar"
            }, presenter::retryScan)
        }
        PairingPresentation.Success -> Text("Hermes conectado", modifier = Modifier.pairingStatusSemantics())
    }
}

@Composable
private fun PairingButton(label: String, action: () -> Unit) {
    Button(onClick = action, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) { Text(label) }
}

@Composable
internal fun RePairScreen(setup: SetupUiState, pairing: PairingViewModel, onBack: () -> Unit) {
    val state by pairing.presenter.state.collectAsState()
    LaunchedEffect(setup) { pairing.presenter.refresh() }
    val busy = state == PairingPresentation.Pairing
    val back = { if (state == PairingPresentation.Scanning) pairing.presenter.cancelScan() else if (!busy) onBack() }
    BackHandler(onBack = back)
    Scaffold(topBar = { AcklineTopBar(title = "Conectar con Hermes", onBack = if (busy) null else back) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PairingContent(state, pairing.presenter)
            if (state == PairingPresentation.Success) PairingButton("Volver a Ajustes", onBack)
        }
    }
}
