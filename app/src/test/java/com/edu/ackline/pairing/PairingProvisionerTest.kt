package com.edu.ackline.pairing

import com.edu.ackline.network.AckBaseUrlProvider
import com.edu.ackline.network.InMemoryAckBaseUrlStorage
import com.edu.ackline.security.PayloadKeyStore
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingProvisionerTest {

    @Test
    fun successfulClaimImportsKeyPersistsUrlAndConfirmsFidInOrder() {
        val order = mutableListOf<String>()
        var importedRawKey: ByteArray? = null
        var confirmedFid: String? = null
        val storage = InMemoryAckBaseUrlStorage()
        val provider = AckBaseUrlProvider("https://fallback.example", storage)
        val key = Base64.getEncoder().encodeToString(TEST_KEY)

        val provisioner = PairingProvisioner(
            claim = { _, request ->
                order += "claim"
                assertEquals("session", request.sessionId)
                PairingClaimResult.Success(
                    PairingClaimResponse("ackline-main", key, "https://paired.example"),
                )
            },
            importRawKey = { rawKey, kid ->
                order += "import"
                importedRawKey = rawKey
                assertEquals("ackline-main", kid)
                assertEquals(32, rawKey.size)
                PayloadKeyStore.ImportResult.IMPORTED
            },
            setProvisionedBaseUrl = { baseUrl ->
                order += "url"
                provider.setProvisionedBaseUrl(baseUrl)
            },
            confirmServerPairing = { fid ->
                order += "confirm"
                confirmedFid = fid
                true
            },
        )

        val result = provisioner.pair(
            pairingEndpoint = "https://hermes.example/pairing/claim",
            sessionId = "session",
            token = "token",
            currentFid = "fid-001",
        )

        assertEquals(PairingProvisioningResult.Success, result)
        assertEquals(listOf("claim", "import", "url", "confirm"), order)
        assertEquals("https://paired.example", provider.getBaseUrl())
        assertEquals("fid-001", confirmedFid)
        assertTrue(importedRawKey!!.all { it == 0.toByte() })
    }

    @Test
    fun claimFailureDoesNotMutateLocalProvisioningState() {
        var importCalls = 0
        var urlCalls = 0
        var confirmCalls = 0
        val provider = AckBaseUrlProvider(
            "https://fallback.example",
            InMemoryAckBaseUrlStorage(),
        )
        val secret = "pairing-token-secret"
        val provisioner = PairingProvisioner(
            claim = { _, _ ->
                PairingClaimResult.Failure(PairingClaimFailure.EXPIRED)
            },
            importRawKey = { _, _ ->
                importCalls += 1
                PayloadKeyStore.ImportResult.FAILED
            },
            setProvisionedBaseUrl = {
                urlCalls += 1
                provider.setProvisionedBaseUrl(it)
            },
            confirmServerPairing = {
                confirmCalls += 1
                true
            },
        )

        val result = provisioner.pair(
            "https://hermes.example/pairing/claim",
            "session",
            secret,
            "fid",
        )

        assertEquals(
            PairingProvisioningResult.Failure(
                PairingProvisioningFailure.ClaimFailed(PairingClaimFailure.EXPIRED),
            ),
            result,
        )
        assertEquals(0, importCalls)
        assertEquals(0, urlCalls)
        assertEquals(0, confirmCalls)
        assertEquals("https://fallback.example", provider.getBaseUrl())
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun keyImportFailureLeavesUrlAndFidBaselineUntouched() {
        var urlCalls = 0
        var confirmCalls = 0
        val storage = InMemoryAckBaseUrlStorage()
        val provider = AckBaseUrlProvider("https://fallback.example", storage)
        val provisioner = provisioner(
            provider = provider,
            importResult = PayloadKeyStore.ImportResult.FAILED,
            onUrl = { urlCalls += 1 },
            onConfirm = { confirmCalls += 1 },
        )

        assertEquals(
            PairingProvisioningResult.Failure(PairingProvisioningFailure.KeyImportFailed),
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
        assertEquals(0, urlCalls)
        assertEquals(0, confirmCalls)
        assertEquals("https://fallback.example", provider.getBaseUrl())
    }

    @Test
    fun wrongKeySizeIsReportedByProvisioningLayer() {
        val provisioner = provisioner(
            responseKey = Base64.getEncoder().encodeToString(ByteArray(31)),
            importResult = PayloadKeyStore.ImportResult.INVALID_KEY,
        )

        assertEquals(
            PairingProvisioningResult.Failure(PairingProvisioningFailure.InvalidKeySize),
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
    }

    @Test
    fun invalidAckUrlDoesNotReachFidConfirmation() {
        var importCalls = 0
        var confirmCalls = 0
        val provisioner = PairingProvisioner(
            claim = { _, _ ->
                PairingClaimResult.Success(
                    PairingClaimResponse(
                        kid = "ackline-main",
                        e2eeKeyB64 = Base64.getEncoder().encodeToString(TEST_KEY),
                        ackBaseUrl = "http://not-https.example",
                    ),
                )
            },
            importRawKey = { _, _ ->
                importCalls += 1
                PayloadKeyStore.ImportResult.IMPORTED
            },
            setProvisionedBaseUrl = { AckBaseUrlProvider.SetResult.STORED },
            confirmServerPairing = {
                confirmCalls += 1
                true
            },
        )

        assertEquals(
            PairingProvisioningResult.Failure(PairingProvisioningFailure.InvalidResponse),
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
        assertEquals(0, importCalls)
        assertEquals(0, confirmCalls)
    }

    @Test
    fun ackUrlPersistenceFailureDoesNotConfirmFid() {
        var confirmCalls = 0
        val provisioner = PairingProvisioner(
            claim = { _, _ ->
                PairingClaimResult.Success(
                    PairingClaimResponse(
                        kid = "ackline-main",
                        e2eeKeyB64 = Base64.getEncoder().encodeToString(TEST_KEY),
                        ackBaseUrl = "https://paired.example",
                    ),
                )
            },
            importRawKey = { _, _ -> PayloadKeyStore.ImportResult.IMPORTED },
            setProvisionedBaseUrl = { AckBaseUrlProvider.SetResult.PERSISTENCE_FAILED },
            confirmServerPairing = {
                confirmCalls += 1
                true
            },
        )

        assertEquals(
            PairingProvisioningResult.Failure(
                PairingProvisioningFailure.AckBaseUrlPersistenceFailed,
            ),
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
        assertEquals(0, confirmCalls)
    }

    @Test
    fun alreadyReadyKeyIsAcceptedWithoutChangingTheRemainingOrder() {
        val order = mutableListOf<String>()
        val provisioner = PairingProvisioner(
            claim = { _, _ ->
                order += "claim"
                PairingClaimResult.Success(
                    PairingClaimResponse(
                        "ackline-main",
                        Base64.getEncoder().encodeToString(TEST_KEY),
                        "https://paired.example",
                    ),
                )
            },
            importRawKey = { _, _ ->
                order += "import"
                PayloadKeyStore.ImportResult.ALREADY_READY
            },
            setProvisionedBaseUrl = {
                order += "url"
                AckBaseUrlProvider.SetResult.STORED
            },
            confirmServerPairing = {
                order += "confirm"
                true
            },
        )

        assertEquals(
            PairingProvisioningResult.Success,
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
        assertEquals(listOf("claim", "import", "url", "confirm"), order)
    }

    @Test
    fun fidConfirmationFailureReportsIncompleteProvisioningAfterLocalWrites() {
        var confirmed = 0
        val provisioner = provisioner(
            onConfirm = { confirmed += 1 },
            confirmResult = false,
        )

        assertEquals(
            PairingProvisioningResult.Failure(PairingProvisioningFailure.FidConfirmationFailed),
            provisioner.pair("https://hermes.example/pairing/claim", "s", "t", "fid"),
        )
        assertEquals(1, confirmed)
    }

    private fun provisioner(
        provider: AckBaseUrlProvider = AckBaseUrlProvider(
            "https://fallback.example",
            InMemoryAckBaseUrlStorage(),
        ),
        responseKey: String = Base64.getEncoder().encodeToString(TEST_KEY),
        importResult: PayloadKeyStore.ImportResult = PayloadKeyStore.ImportResult.IMPORTED,
        onUrl: () -> Unit = {},
        onConfirm: () -> Unit = {},
        confirmResult: Boolean = true,
    ): PairingProvisioner = PairingProvisioner(
        claim = { _, _ ->
            PairingClaimResult.Success(
                PairingClaimResponse("ackline-main", responseKey, "https://paired.example"),
            )
        },
        importRawKey = { _, _ -> importResult },
        setProvisionedBaseUrl = { url ->
            onUrl()
            provider.setProvisionedBaseUrl(url)
        },
        confirmServerPairing = {
            onConfirm()
            confirmResult
        },
    )

    private companion object {
        val TEST_KEY = ByteArray(32) { it.toByte() }
    }
}
