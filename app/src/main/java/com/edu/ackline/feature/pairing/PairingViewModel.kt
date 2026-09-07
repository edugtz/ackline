package com.edu.ackline.feature.pairing

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import com.edu.ackline.AcklineApplication
import com.edu.ackline.BuildConfig
import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupState
import com.edu.ackline.SetupUiState
import com.edu.ackline.pairing.PairingProvisioningResult
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface PairingPresentation {
    data object Idle : PairingPresentation
    data object WaitingForRegistration : PairingPresentation
    data object TailscaleRequired : PairingPresentation
    data object ReadyToScan : PairingPresentation
    data object Scanning : PairingPresentation
    data object Pairing : PairingPresentation
    data object Success : PairingPresentation
    data class Error(val error: PairingError) : PairingPresentation
}

/** No bearer is in UI state, saved state, or storage. Retries require rescanning. */
internal class PairingPresenter(
    private val executor: Executor,
    private val setup: () -> SetupUiState,
    private val vpnPresent: () -> Boolean,
    private val pair: (PairingQr, String) -> PairingProvisioningResult,
) {
    private val mutableState = MutableStateFlow<PairingPresentation>(PairingPresentation.Idle)
    val state = mutableState.asStateFlow()
    private var ambiguousRetryUsed = false

    @Synchronized
    fun refresh() {
        if (mutableState.value == PairingPresentation.Pairing || mutableState.value == PairingPresentation.Success ||
            mutableState.value is PairingPresentation.Error
        ) return
        mutableState.value = prerequisite() ?: PairingPresentation.ReadyToScan
    }

    private fun prerequisite(): PairingPresentation? {
        val value = setup()
        return when {
            !value.legacyBootstrapResolved || value.registrationState != RegistrationState.Ready ||
                value.installationId.isNullOrBlank() -> PairingPresentation.WaitingForRegistration
            !vpnPresent() -> PairingPresentation.TailscaleRequired
            else -> null
        }
    }

    @Synchronized
    fun retryScan() {
        if (mutableState.value !is PairingPresentation.Error) return
        mutableState.value = prerequisite() ?: PairingPresentation.ReadyToScan
    }

    // A1 integration hook only. No external intent, clipboard, or production manual-input UI.
    @Synchronized
    fun acceptDebugQr(input: String, debugEnabled: Boolean) {
        if (!debugEnabled || mutableState.value != PairingPresentation.ReadyToScan) return
        prerequisite()?.let { mutableState.value = it; return }
        mutableState.value = PairingPresentation.Scanning
        val parsed = PairingQrParser.parse(input)
        when (parsed) {
            PairingQrResult.Unrelated -> { mutableState.value = PairingPresentation.ReadyToScan; return }
            PairingQrResult.Invalid -> { mutableState.value = PairingPresentation.Error(invalidQrError()); return }
            is PairingQrResult.Valid -> {
                val fid = setup().installationId ?: run { refresh(); return }
                mutableState.value = PairingPresentation.Pairing
                executor.execute {
                    val result = try {
                        pair(parsed.payload, fid)
                    } catch (_: Exception) {
                        // Unknown exception may be after a successful remote claim.
                        PairingProvisioningResult.Failure(com.edu.ackline.pairing.PairingProvisioningFailure.FidConfirmationFailed)
                    }
                    complete(result)
                }
            }
        }
    }

    @Synchronized
    private fun complete(result: PairingProvisioningResult) {
        mutableState.value = when (result) {
            PairingProvisioningResult.Success -> PairingPresentation.Success
            is PairingProvisioningResult.Failure -> {
                var error = pairingError(result.reason)
                if (error.action == PairingErrorAction.RetryOnce) {
                    if (ambiguousRetryUsed) error = invalidQrError() else ambiguousRetryUsed = true
                }
                PairingPresentation.Error(error)
            }
        }
    }
}

internal class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcklineApplication
    private val executor = Executors.newSingleThreadExecutor()
    val presenter = PairingPresenter(executor, { SetupState.state.value }, { vpnPresent(app) }) { qr, fid ->
        val result = app.pairingProvisioner.pair(qr.endpoint, qr.sessionId, qr.token, fid)
        SetupState.onEncryptionStatusChanged(runCatching {
            app.payloadKeyStore.isReady(BuildConfig.PAYLOAD_ENCRYPTION_KID)
        }.getOrDefault(false))
        SetupState.onAckProvisioningChanged(app.ackBaseUrlProvider.isProvisioned())
        result
    }

    internal fun acceptDebugQr(input: String) = presenter.acceptDebugQr(input, BuildConfig.DEBUG)
    override fun onCleared() {
        executor.shutdown() // A claim already started completes its durable finalization.
    }
}

@Suppress("DEPRECATION") // Matches the existing explicit-VPN transport's network selection.
private fun vpnPresent(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    manager.allNetworks.any {
        manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}.getOrDefault(false)
