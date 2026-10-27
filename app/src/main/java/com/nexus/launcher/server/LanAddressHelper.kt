package com.nexus.launcher.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

data class LanInfo(
    val ipv4: String?,
    val interfaceName: String?,
    val isWifi: Boolean,
    val joinAddress: String?,
    val note: String
)

/**
 * Best-effort LAN IPv4 for phone-hosted Minecraft server.
 * Public internet needs a relay — not invented here.
 */
@Singleton
class LanAddressHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun currentLanInfo(port: Int = 25565): LanInfo {
        val ip = wifiIpv4() ?: firstNonLoopbackIpv4()
        val isWifi = isOnWifi()
        return when {
            ip != null -> LanInfo(
                ipv4 = ip,
                interfaceName = null,
                isWifi = isWifi,
                joinAddress = "$ip:$port",
                note = if (isWifi)
                    "Same Wi‑Fi pe players is address se join kar sakte hain."
                else
                    "Wi‑Fi nahi dikha. Mobile data pe public join ke liye relay chahiye."
            )
            else -> LanInfo(
                ipv4 = null,
                interfaceName = null,
                isWifi = isWifi,
                joinAddress = null,
                note = "LAN IP nahi mila. Wi‑Fi on karo ya network permission check karo."
            )
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    private fun wifiIpv4(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork ?: return null
                val props: LinkProperties = cm.getLinkProperties(net) ?: return null
                props.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
            } else {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ip = wm.connectionInfo?.ipAddress ?: return null
                if (ip == 0) return null
                String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun firstNonLoopbackIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
