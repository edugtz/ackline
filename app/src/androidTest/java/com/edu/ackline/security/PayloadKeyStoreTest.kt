package com.edu.ackline.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.ackline.push.EncryptedPushEnvelope
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PayloadKeyStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keyStore = PayloadKeyStore(context)
    private val stagingFile = File(context.filesDir, PayloadKeyStore.STAGING_FILE_NAME)

    @Before
    fun setUp() {
        stagingFile.delete()
        keyStore.deleteKeyForTest(TEST_KID)
    }

    @After
    fun tearDown() {
        stagingFile.delete()
        keyStore.deleteKeyForTest(TEST_KID)
    }

    @Test
    fun importsExact32ByteKeyAndKeepsOnlyNonExportableReference() {
        stagingFile.writeBytes(TEST_KEY)

        assertEquals(PayloadKeyStore.ImportResult.IMPORTED, keyStore.importStagedKeyIfPresent(TEST_KID))
        assertFalse(stagingFile.exists())
        assertTrue(keyStore.isReady(TEST_KID))
        assertNull(keyStore.getDecryptKey(TEST_KID)?.encoded)
        assertTrue(PayloadKeyStore(context).isReady(TEST_KID))

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            requireNotNull(keyStore.getDecryptKey(TEST_KID)),
            GCMParameterSpec(128, ByteArray(12) { it.toByte() }),
        )
        cipher.updateAAD(EncryptedPushEnvelope.aad("1", "test-vector"))
        assertEquals(
            VECTOR_PLAINTEXT,
            cipher.doFinal(Base64.getUrlDecoder().decode(VECTOR_CIPHERTEXT)).toString(Charsets.UTF_8),
        )
    }

    @Test
    fun invalidOrDuplicateImportsDeleteStagingWithoutReplacingExistingKey() {
        stagingFile.writeBytes(ByteArray(31))
        assertEquals(PayloadKeyStore.ImportResult.INVALID_KEY, keyStore.importStagedKeyIfPresent(TEST_KID))
        assertFalse(stagingFile.exists())

        stagingFile.writeBytes(TEST_KEY)
        assertEquals(PayloadKeyStore.ImportResult.IMPORTED, keyStore.importStagedKeyIfPresent(TEST_KID))
        stagingFile.writeBytes(ByteArray(32) { 7 })
        assertEquals(PayloadKeyStore.ImportResult.ALREADY_READY, keyStore.importStagedKeyIfPresent(TEST_KID))
        assertFalse(stagingFile.exists())
    }

    @Test
    fun missingAliasFailsClosedWithoutDecrypting() {
        val envelope = EncryptedPushEnvelope(
            version = "1",
            kid = "missing-instrumented-key",
            nonce = ByteArray(12),
            ciphertext = ByteArray(16),
        )

        val result = PayloadCrypto(keyStore).decrypt(envelope)
        assertEquals(
            PayloadCrypto.Failure.KEY_NOT_CONFIGURED,
            (result as PayloadCrypto.DecryptResult.Rejected).failure,
        )
    }

    private companion object {
        const val TEST_KID = "instrumented-test"
        val TEST_KEY = ByteArray(32) { it.toByte() }
        const val VECTOR_PLAINTEXT = "{\"protocol\":\"1\",\"notification_id\":\"vector-001\",\"level\":\"important\",\"title\":\"Vector\",\"message\":\"Non-sensitive test\",\"created_at\":\"2026-08-30T00:00:00Z\",\"ack_token\":\"vector-token\"}"
        const val VECTOR_CIPHERTEXT = "PCCmaaqRrXjiLbWxk9haQaG46ECZHTYfWROM6nM2adYjKoyKyqJm9waJT925pQQagjwW6Db0mfhW-lp2apeUgIQe6linuFINeXaQTLnqbZxX-OtOVA42G46c-alaypvDpJZ0lcEvu3PqdUjXaAFEDM5FTomAt0D7OZozkuOSTDSZYDD0gvr7ayXSePUN6nbyBs7TBW8zv2jtPIMojr2Ci_21pDefmq8KyBHwjrDr49W_s-dujbZdrCcmIPM89XMuGsY"
    }
}
