package com.edu.ackline.pairing

import com.edu.ackline.network.AckBaseUrlProvider
import com.edu.ackline.network.normalizeHttpsBaseUrl
import com.edu.ackline.security.PayloadKeyStore

sealed interface PairingProvisioningResult {
    data object Success : PairingProvisioningResult

    data class Failure(val reason: PairingProvisioningFailure) : PairingProvisioningResult
}

sealed interface PairingProvisioningFailure {
    data object InvalidInput : PairingProvisioningFailure

    data class ClaimFailed(val reason: PairingClaimFailure) : PairingProvisioningFailure

    data object InvalidResponse : PairingProvisioningFailure

    data object InvalidKeyEncoding : PairingProvisioningFailure

    data object InvalidKeySize : PairingProvisioningFailure

    data object KeyImportFailed : PairingProvisioningFailure

    data object AckBaseUrlInvalid : PairingProvisioningFailure

    data object AckBaseUrlPersistenceFailed : PairingProvisioningFailure

    data object FidConfirmationFailed : PairingProvisioningFailure
}

/**
 * The only local boundary allowed to turn a successful H1 claim into a
 * provisioned, server-confirmed pairing state.
 */
class PairingProvisioner(
    private val claim: (String, PairingClaimRequest) -> PairingClaimResult,
    private val importRawKey: (ByteArray, String) -> PayloadKeyStore.ImportResult,
    private val setProvisionedBaseUrl: (String) -> AckBaseUrlProvider.SetResult,
    private val confirmServerPairing: (String) -> Boolean,
) {

    fun pair(
        pairingEndpoint: String,
        sessionId: String,
        token: String,
        currentFid: String,
    ): PairingProvisioningResult {
        if (normalizePairingEndpoint(pairingEndpoint) == null ||
            !isValidClaimValue(sessionId, MAX_SESSION_ID_LENGTH) ||
            !isValidClaimValue(token, MAX_TOKEN_LENGTH) ||
            !isValidClaimValue(currentFid, MAX_FID_LENGTH)
        ) {
            return failure(PairingProvisioningFailure.InvalidInput)
        }

        val claimResult = claim(
            pairingEndpoint,
            PairingClaimRequest(
                sessionId = sessionId,
                token = token,
                fid = currentFid,
            ),
        )
        val response = when (claimResult) {
            is PairingClaimResult.Success -> claimResult.response
            is PairingClaimResult.Failure -> {
                return failure(PairingProvisioningFailure.ClaimFailed(claimResult.reason))
            }
        }

        if (!EncryptedKidValidator.isValid(response.kid) ||
            normalizeHttpsBaseUrl(response.ackBaseUrl) == null
        ) {
            return failure(PairingProvisioningFailure.InvalidResponse)
        }

        val rawKey = decodeE2eeKey(response.e2eeKeyB64)
            ?: return failure(PairingProvisioningFailure.InvalidKeyEncoding)
        val importResult = try {
            importRawKey(rawKey, response.kid)
        } catch (_: Exception) {
            PayloadKeyStore.ImportResult.FAILED
        } finally {
            rawKey.fill(0)
        }

        when (importResult) {
            PayloadKeyStore.ImportResult.IMPORTED,
            PayloadKeyStore.ImportResult.ALREADY_READY,
            -> Unit

            PayloadKeyStore.ImportResult.INVALID_KEY -> {
                return failure(PairingProvisioningFailure.InvalidKeySize)
            }

            PayloadKeyStore.ImportResult.FAILED,
            PayloadKeyStore.ImportResult.NO_STAGING_FILE,
            -> return failure(PairingProvisioningFailure.KeyImportFailed)
        }

        when (setProvisionedBaseUrl(response.ackBaseUrl)) {
            AckBaseUrlProvider.SetResult.STORED -> Unit
            AckBaseUrlProvider.SetResult.INVALID_URL -> {
                return failure(PairingProvisioningFailure.AckBaseUrlInvalid)
            }

            AckBaseUrlProvider.SetResult.PERSISTENCE_FAILED -> {
                return failure(PairingProvisioningFailure.AckBaseUrlPersistenceFailed)
            }
        }

        if (!confirmServerPairing(currentFid)) {
            return failure(PairingProvisioningFailure.FidConfirmationFailed)
        }

        return PairingProvisioningResult.Success
    }

    private fun failure(reason: PairingProvisioningFailure): PairingProvisioningResult =
        PairingProvisioningResult.Failure(reason)

    private object EncryptedKidValidator {
        fun isValid(kid: String): Boolean =
            com.edu.ackline.push.EncryptedPushEnvelope.isValidKid(kid)
    }

    private companion object {
        const val MAX_SESSION_ID_LENGTH = 256
        const val MAX_TOKEN_LENGTH = 512
        const val MAX_FID_LENGTH = 256
    }
}
