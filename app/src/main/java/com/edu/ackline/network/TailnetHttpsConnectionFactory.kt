package com.edu.ackline.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

fun interface HttpsConnectionFactory {
    fun open(url: URL): HttpsURLConnection
}

class TailnetHttpsConnectionFactory(
    context: Context,
) : HttpsConnectionFactory {

    private val applicationContext = context.applicationContext

    override fun open(url: URL): HttpsURLConnection {
        val connectivityManager = try {
            applicationContext.getSystemService(ConnectivityManager::class.java)
        } catch (error: RuntimeException) {
            throw IOException("tailnet VPN network unavailable", error)
        } ?: throw IOException("tailnet VPN network unavailable")

        val vpnNetwork = try {
            connectivityManager.allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (error: RuntimeException) {
            throw IOException("tailnet VPN network unavailable", error)
        } ?: throw IOException("tailnet VPN network unavailable")

        val connection = try {
            vpnNetwork.openConnection(url)
        } catch (error: IOException) {
            throw error
        } catch (error: RuntimeException) {
            throw IOException("tailnet HTTPS connection unavailable", error)
        }

        return connection as? HttpsURLConnection
            ?: throw IOException("tailnet HTTPS connection unavailable")
    }
}
