package com.edu.ackline.network

import android.content.Context
import android.content.SharedPreferences
import com.edu.ackline.BuildConfig
import java.net.URI

/**
 * Owns the runtime ACK/recovery base URL.
 *
 * A successfully completed pairing may provision a URL in app-private
 * storage. Devices without one continue using the build-time fallback.
 */
class AckBaseUrlProvider internal constructor(
    private val fallbackBaseUrl: String,
    private val storage: AckBaseUrlStorage,
) {

    constructor(
        context: Context,
        fallbackBaseUrl: String = BuildConfig.ACK_BASE_URL,
    ) : this(
        fallbackBaseUrl = fallbackBaseUrl,
        storage = SharedPreferencesAckBaseUrlStorage(context.applicationContext),
    )

    internal constructor(fallbackBaseUrl: String) : this(
        fallbackBaseUrl = fallbackBaseUrl,
        storage = EmptyAckBaseUrlStorage,
    )

    /** Returns the provisioned URL when present, otherwise the build fallback. */
    fun getBaseUrl(): String =
        runCatching { storage.read() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackBaseUrl

    /** Runtime provisioning only; the build fallback is never pairing evidence. */
    fun isProvisioned(): Boolean = runCatching {
        storage.read()?.let(::normalizeHttpsBaseUrl) != null
    }.getOrDefault(false)

    fun setProvisionedBaseUrl(baseUrl: String): SetResult {
        val normalized = normalizeHttpsBaseUrl(baseUrl)
            ?: return SetResult.INVALID_URL

        return if (runCatching { storage.write(normalized) }.getOrDefault(false)) {
            SetResult.STORED
        } else {
            SetResult.PERSISTENCE_FAILED
        }
    }

    fun clearProvisionedBaseUrl(): Boolean =
        runCatching { storage.clear() }.getOrDefault(false)

    enum class SetResult {
        STORED,
        INVALID_URL,
        PERSISTENCE_FAILED,
    }
}

internal interface AckBaseUrlStorage {
    fun read(): String?

    fun write(baseUrl: String): Boolean

    fun clear(): Boolean
}

private class SharedPreferencesAckBaseUrlStorage(
    context: Context,
) : AckBaseUrlStorage {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString(KEY_PROVISIONED_BASE_URL, null)

    @Suppress("UseKtx") // The commit result is required to report persistence failure.
    override fun write(baseUrl: String): Boolean =
        preferences.edit()
            .putString(KEY_PROVISIONED_BASE_URL, baseUrl)
            .commit()

    @Suppress("UseKtx") // The commit result is required to report persistence failure.
    override fun clear(): Boolean = preferences.edit()
        .remove(KEY_PROVISIONED_BASE_URL)
        .commit()

    private companion object {
        const val PREFERENCES_NAME = "ackline_pairing_config"
        const val KEY_PROVISIONED_BASE_URL = "provisioned_ack_base_url"
    }
}

private object EmptyAckBaseUrlStorage : AckBaseUrlStorage {
    override fun read(): String? = null

    override fun write(baseUrl: String): Boolean = false

    override fun clear(): Boolean = true
}

/** Returns a normalized HTTPS base URL, or null when the value is unsafe. */
internal fun normalizeHttpsBaseUrl(value: String): String? {
    val normalized = value.trim().trimEnd('/')
    if (normalized.isBlank()) return null

    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        uri.userInfo != null
    ) {
        return null
    }

    return runCatching {
        uri.toURL()
        normalized
    }.getOrNull()
}
