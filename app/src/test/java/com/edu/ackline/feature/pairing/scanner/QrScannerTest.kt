package com.edu.ackline.feature.pairing.scanner

import androidx.camera.core.ImageProxy
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupUiState
import com.edu.ackline.feature.pairing.*
import com.edu.ackline.pairing.PairingProvisioningResult
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.edu.ackline.feature.pairing.QR_CAMERA_CONTENT_DESCRIPTION
import com.edu.ackline.feature.pairing.markPairingStatus
import com.edu.ackline.feature.pairing.markQrCamera
import org.junit.Assert.*
import org.junit.Test

class QrScannerTest {
    private val qr = """{"v":1,"endpoint":"https://example.com/pairing/claim","session_id":"test-session","token":"test-token"}"""
    private val direct = Executor { it.run() }

    @Test fun scannerAccessibilityUsesRedactedCameraDescriptionAndPoliteStatus() {
        val camera = SemanticsConfiguration().apply { markQrCamera() }
        val status = SemanticsConfiguration().apply { markPairingStatus() }

        assertEquals(
            listOf(QR_CAMERA_CONTENT_DESCRIPTION),
            camera.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertFalse(QR_CAMERA_CONTENT_DESCRIPTION.contains("token", ignoreCase = true))
        assertFalse(QR_CAMERA_CONTENT_DESCRIPTION.contains("session", ignoreCase = true))
        assertFalse(QR_CAMERA_CONTENT_DESCRIPTION.contains("FID", ignoreCase = true))
        assertEquals(LiveRegionMode.Polite, status.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test fun generatedQrDecodesAtEveryRightAngleAndFeedsExistingParser() {
        val matrix = MultiFormatWriter().encode(qr, BarcodeFormat.QR_CODE, 320, 320)
        var pixels = ByteArray(320 * 320) { if (matrix[it % 320, it / 320]) 0 else -1 }
        repeat(4) {
            val decoded = QrDecoder().decode(pixels, 320, 320)
            assertEquals(qr, decoded)
            assertTrue(PairingQrParser.parse(decoded!!) is PairingQrResult.Valid)
            val old = pixels
            pixels = ByteArray(old.size) { i -> old[(319 - i % 320) * 320 + i / 320] }
        }
    }

    @Test fun otherBarcodeFormatsAndBlankFramesDoNotDecode() {
        val matrix = MultiFormatWriter().encode("1234567890", BarcodeFormat.CODE_128, 320, 320)
        val pixels = ByteArray(320 * 320) { if (matrix[it % 320, it / 320]) 0 else -1 }
        assertNull(QrDecoder().decode(pixels, 320, 320))
        assertNull(QrDecoder().decode(ByteArray(320 * 320) { -1 }, 320, 320))
    }

    @Test fun luminanceHandlesRowPaddingPixelStrideAndBufferPosition() {
        val buffer = ByteBuffer.wrap(byteArrayOf(99, 1, 0, 2, 0, 0, 0, 3, 0, 4))
        buffer.position(1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), copyLuminance(buffer, 2, 2, 6, 2))
        assertEquals(1, buffer.position())
    }

    @Test fun unrelatedStaysScanningMalformedStopsAndValidClaimsOnlyOnce() {
        for (text in listOf("https://unrelated.example", """{"session_id":"broken","v":2}""", qr)) {
            var claims = 0
            val presenter = PairingPresenter(direct, {
                SetupUiState(registrationState = RegistrationState.Ready, installationId = "test-fid", legacyBootstrapResolved = true)
            }, { true }) { _, _ -> claims++; PairingProvisioningResult.Success }
            presenter.refresh(); presenter.beginScan()
            var closes = 0
            val analyzer = QrAnalyzer(direct, presenter::acceptScannedQr) { text }
            repeat(3) { analyzer.analyze(proxy { closes++ }) }
            assertEquals(3, closes)
            when (text) {
                qr -> { assertEquals(1, claims); assertEquals(PairingPresentation.Success, presenter.state.value) }
                "https://unrelated.example" -> { assertEquals(0, claims); assertEquals(PairingPresentation.Scanning, presenter.state.value) }
                else -> { assertEquals(0, claims); assertTrue(presenter.state.value is PairingPresentation.Error) }
            }
            assertFalse(presenter.state.value.toString().contains("test-token"))
            assertFalse(presenter.state.value.toString().contains("test-session"))
        }
    }

    @Test fun everyProxyClosesOnSuccessErrorAndNoResult() {
        for (mode in 0..2) {
            var closes = 0
            var accepted = 0
            val analyzer = QrAnalyzer(direct, { accepted++; true }) {
                when (mode) { 0 -> qr; 1 -> null; else -> throw IllegalArgumentException("synthetic-frame") }
            }
            analyzer.analyze(proxy { closes++ })
            assertEquals(1, closes)
            assertEquals(if (mode == 0) 1 else 0, accepted)
        }
    }

    @Test fun busyMainThreadDoesNotQueueMultiplePayloadsAndStopDropsLateResult() {
        var callback: Runnable? = null
        var decodes = 0
        var accepted = 0
        var closes = 0
        val analyzer = QrAnalyzer(Executor { check(callback == null); callback = it }, { accepted++; true }) { decodes++; qr }
        repeat(5) { analyzer.analyze(proxy { closes++ }) }
        assertEquals(1, decodes)
        assertEquals(5, closes)
        analyzer.stop()
        callback!!.run()
        analyzer.analyze(proxy { closes++ })
        assertEquals(0, accepted)
        assertEquals(6, closes)
        assertEquals(1, decodes)
    }

    @Test fun leavingCameraReleasesExactlyOnceAndLateProviderCannotBind() {
        var binds = 0
        var releases = 0
        val lease = CameraLease()
        lease.attach { binds++; { releases++ } }.getOrThrow()
        lease.close(); lease.close()
        lease.attach { binds++; { releases++ } }.getOrThrow()
        assertEquals(1, binds); assertEquals(1, releases)
        val late = CameraLease()
        late.close()
        late.attach { binds++; { releases++ } }.getOrThrow()
        assertEquals(1, binds)
    }

    @Test fun cameraUnavailableReturnsFailureWithoutAcquiringOwnership() {
        val lease = CameraLease()
        assertTrue(lease.attach { throw IllegalStateException("synthetic unavailable camera") }.isFailure)
        lease.close()
    }

    private fun proxy(close: () -> Unit): ImageProxy = Proxy.newProxyInstance(
        ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java),
    ) { _, method, _ ->
        if (method.name == "close") { close(); null } else throw UnsupportedOperationException(method.name)
    } as ImageProxy
}
