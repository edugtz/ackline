package com.edu.ackline.feature.pairing.scanner

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.camera.core.CameraState
import java.util.concurrent.Executors

@Composable
internal fun QrScanner(accept: (String) -> Boolean, onCancel: () -> Unit) {
    val permission = rememberCameraPermission()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var failed by remember { mutableStateOf(false) }
    var unrelated by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Put cancel before preview so it remains reachable at large font sizes.
        TextButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancelar escaneo") }
        when {
            !permission.granted -> {
                Text("Ackline necesita la cámara para leer el QR de Hermes. No guarda fotos.")
                if (permission.settings) Text("Permite el acceso a la cámara en los ajustes de Android.")
                Button(onClick = permission.launch, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Text(if (permission.settings) "Abrir ajustes de Android" else "Permitir cámara")
                }
                if (!permission.settings) {
                    TextButton(onClick = permission.openSettings, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("Ajustes de cámara")
                    }
                }
            }
            failed -> {
                Text("No se pudo abrir la cámara. Comprueba que esté disponible e intenta de nuevo.")
                Button(onClick = { failed = false }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Reintentar cámara") }
            }
            else -> {
                Text(if (unrelated) "Este QR no es de Ackline" else "Apunta al QR de Hermes en tu Mac.",
                    style = MaterialTheme.typography.bodyMedium)
                if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
                    CameraPreview(
                        accept = { text -> accept(text).also { terminal -> unrelated = !terminal } },
                        onFailure = { failed = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(accept: (String) -> Boolean, onFailure: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val acceptLatest by rememberUpdatedState(accept)
    val failureLatest by rememberUpdatedState(onFailure)
    val view = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(320.dp))
    DisposableEffect(view, owner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val analyzer = QrAnalyzer(main, { acceptLatest(it) })
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, analyzer)
        val lease = CameraLease()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // A late provider callback after navigation must never acquire camera ownership.
            lease.attach {
                val provider = future.get()
                try {
                    preview.setSurfaceProvider(view.surfaceProvider)
                    val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    val observer = Observer<CameraState> { state ->
                        if (state.error != null) failureLatest()
                    }
                    camera.cameraInfo.cameraState.observe(owner, observer)
                    val release: () -> Unit = {
                        camera.cameraInfo.cameraState.removeObserver(observer)
                        provider.unbind(preview, analysis) // Only this scanner's use cases.
                    }
                    release
                } catch (failure: Exception) {
                    provider.unbind(preview, analysis)
                    throw failure // Sanitized by the UI boundary; never logged.
                }
            }.onFailure { failureLatest() }
        }, main)
        onDispose {
            analyzer.stop()
            analysis.clearAnalyzer()
            lease.close()
            executor.shutdown() // Already running decode closes its proxy; queued callbacks see stop.
        }
    }
}

/** Main-thread ownership: disposal before async initialization forbids a later bind. */
internal class CameraLease {
    private var closed = false
    private var release: (() -> Unit)? = null
    fun attach(bind: () -> (() -> Unit)): Result<Unit> = runCatching {
        if (!closed && release == null) release = bind()
    }
    fun close() {
        if (closed) return
        closed = true
        release?.invoke()
        release = null
    }
}
