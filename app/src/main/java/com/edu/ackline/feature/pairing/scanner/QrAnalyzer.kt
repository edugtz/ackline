package com.edu.ackline.feature.pairing.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** QRCodeReader cannot decode other barcode formats. No frame or payload leaves memory. */
internal class QrDecoder {
    private val reader = QRCodeReader()

    fun decode(bytes: ByteArray, width: Int, height: Int): String? = try {
        // QR finder patterns identify orientation; rotating/copying the camera image is unnecessary.
        reader.decode(BinaryBitmap(HybridBinarizer(
            PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false),
        ))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

internal fun copyLuminance(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
    val start = buffer.position()
    return ByteArray(width * height) { index ->
        buffer.get(start + (index / width) * rowStride + (index % width) * pixelStride)
    }
}

/** One callback in flight, even if the UI thread is busy. Every incoming proxy closes. */
internal class QrAnalyzer(
    private val mainExecutor: Executor,
    private val accept: (String) -> Boolean,
    private val decode: (ImageProxy) -> String? = cameraDecoder(),
) : ImageAnalysis.Analyzer {
    private val pending = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    fun stop() { stopped.set(true) }

    override fun analyze(image: ImageProxy) {
        var text: String? = null
        try {
            if (!stopped.get() && pending.compareAndSet(false, true)) {
                text = decode(image)
                if (text == null) pending.set(false)
            }
        } catch (_: Exception) {
            pending.set(false) // Bad/closing frame: skip without logging camera data.
        } finally {
            image.close()
        }
        val decoded = text ?: return
        try {
            mainExecutor.execute {
                try {
                    if (!stopped.get() && accept(decoded)) stopped.set(true)
                } finally {
                    pending.set(false)
                }
            }
        } catch (_: Exception) {
            pending.set(false)
        }
    }
}

private fun cameraDecoder(): (ImageProxy) -> String? {
    val decoder = QrDecoder()
    return { image ->
        val plane = image.planes[0]
        val bytes = copyLuminance(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
        try { decoder.decode(bytes, image.width, image.height) } finally { bytes.fill(0) }
    }
}
