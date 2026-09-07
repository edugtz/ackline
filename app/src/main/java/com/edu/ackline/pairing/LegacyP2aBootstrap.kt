package com.edu.ackline.pairing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Inputs are captured at startup, before registration can replace the stored FID. */
internal class LegacyP2aBootstrap(
    private val baseline: FidRePairState,
    private val upgrade: Boolean,
) {
    private var encryptionReady: Boolean? = null
    private var ackProvisioned: Boolean? = null
    private var registrationResolved = false
    private var currentFid: String? = null

    fun provisioningResolved(encryptionReady: Boolean, ackProvisioned: Boolean) {
        this.encryptionReady = encryptionReady
        this.ackProvisioned = ackProvisioned
    }

    fun registrationResolved(fid: String?) {
        registrationResolved = true
        currentFid = fid
    }

    /** null means loading; false is a final, ineligible decision. */
    fun decision(): Boolean? {
        if (baseline.legacyP2aBootstrapEvaluated || baseline.serverPairingConfirmed) return false
        if (!upgrade) return false
        if (encryptionReady == null || ackProvisioned == null || !registrationResolved) return null
        return encryptionReady == true && ackProvisioned == true &&
            !baseline.lastObservedFid.isNullOrBlank() && !currentFid.isNullOrBlank() &&
            currentFid == baseline.lastObservedFid && !baseline.rePairRequired
    }
}

internal fun isPackageUpgrade(firstInstallTime: Long, lastUpdateTime: Long): Boolean =
    firstInstallTime > 0 && firstInstallTime < lastUpdateTime

internal fun isPackageUpgrade(context: Context): Boolean = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    isPackageUpgrade(info.firstInstallTime, info.lastUpdateTime)
}.getOrDefault(false)
