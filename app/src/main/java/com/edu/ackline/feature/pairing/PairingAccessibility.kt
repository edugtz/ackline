package com.edu.ackline.feature.pairing

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

internal const val QR_CAMERA_CONTENT_DESCRIPTION =
    "Vista de cámara para escanear el QR de Ackline"
internal const val QR_SCANNER_GUIDANCE = "Apunta al QR de Hermes en tu Mac."
internal const val QR_SCANNER_UNRELATED_GUIDANCE = "Este QR no es de Ackline"

internal fun SemanticsPropertyReceiver.markQrCamera() {
    contentDescription = QR_CAMERA_CONTENT_DESCRIPTION
}

internal fun Modifier.qrCameraSemantics(): Modifier = semantics {
    markQrCamera()
}

internal fun SemanticsPropertyReceiver.markPairingStatus() {
    liveRegion = LiveRegionMode.Polite
}

internal fun Modifier.pairingStatusSemantics(): Modifier = semantics {
    markPairingStatus()
}
