package com.edu.ackline.feature.pairing

import com.edu.ackline.pairing.PairingClaimFailure
import com.edu.ackline.pairing.PairingProvisioningFailure

internal enum class PairingErrorAction { RetrySameQr, RetryOnce, NewQr, ReplacementQr, Operator }
internal data class PairingError(val message: String, val action: PairingErrorAction)

internal fun pairingError(reason: PairingClaimFailure): PairingError = when (reason) {
    PairingClaimFailure.TRANSPORT -> PairingError(
        "No se pudo conectar con Hermes. Activa Tailscale y vuelve a intentarlo.", PairingErrorAction.RetryOnce,
    ) // P2A cannot distinguish failure before send from a lost response after consumption.
    PairingClaimFailure.TAILSCALE_IDENTITY_REQUIRED -> PairingError(
        "No se pudo conectar con Hermes. Activa Tailscale y vuelve a intentarlo.", PairingErrorAction.RetrySameQr,
    )
    PairingClaimFailure.RATE_LIMITED -> PairingError(
        "Demasiados intentos. Espera un minuto y vuelve a intentarlo.", PairingErrorAction.RetrySameQr,
    )
    PairingClaimFailure.EXPIRED -> PairingError("Este QR caducó. Genera un nuevo QR en tu Mac.", PairingErrorAction.NewQr)
    PairingClaimFailure.CONSUMED -> PairingError("Este QR ya fue usado. Genera un nuevo QR en tu Mac.", PairingErrorAction.NewQr)
    PairingClaimFailure.REPLACE_REQUIRED -> PairingError(
        "Hermes ya está vinculado con otra instalación. Genera un nuevo QR de reemplazo en tu Mac.",
        PairingErrorAction.ReplacementQr,
    )
    PairingClaimFailure.SERVER_MISCONFIGURED -> PairingError(
        "Hermes no está listo para emparejar. Revisa su configuración en el Mac.", PairingErrorAction.Operator,
    )
    PairingClaimFailure.INVALID_ENDPOINT, PairingClaimFailure.INVALID_REQUEST,
    PairingClaimFailure.INVALID, PairingClaimFailure.MALFORMED_RESPONSE,
    PairingClaimFailure.RESPONSE_TOO_LARGE, PairingClaimFailure.UNEXPECTED_STATUS,
    PairingClaimFailure.CLIENT_ERROR -> invalidQrError()
}

internal fun invalidQrError() = PairingError("Este QR no es válido. Genera un nuevo QR en tu Mac.", PairingErrorAction.NewQr)

internal fun pairingError(reason: PairingProvisioningFailure): PairingError = when (reason) {
    is PairingProvisioningFailure.ClaimFailed -> pairingError(reason.reason)
    PairingProvisioningFailure.InvalidInput -> invalidQrError()
    PairingProvisioningFailure.InvalidResponse, PairingProvisioningFailure.InvalidKeyEncoding,
    PairingProvisioningFailure.InvalidKeySize, PairingProvisioningFailure.KeyImportFailed,
    PairingProvisioningFailure.AckBaseUrlInvalid, PairingProvisioningFailure.AckBaseUrlPersistenceFailed,
    PairingProvisioningFailure.FidConfirmationFailed -> PairingError(
        "No se pudo completar el emparejamiento. Genera un nuevo QR en tu Mac.", PairingErrorAction.NewQr,
    )
}
