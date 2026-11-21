package com.aerocell.stand.upload

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalIpv4 {
    fun findLanAddress(): String? {
        val found = mutableListOf<Pair<Int, String>>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        for (nic in interfaces) {
            val up = try {
                nic.isUp && !nic.isLoopback && !nic.isVirtual
            } catch (_: Throwable) {
                false
            }
            if (!up) continue
            val name = nic.name.lowercase()
            val score = when {
                name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap") -> 0
                name.startsWith("eth") || name.startsWith("en") -> 1
                name.contains("rmnet") || name.contains("ccmni") || name.contains("wwan") -> 4
                else -> 2
            }
            for (addr in nic.inetAddresses.toList()) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    val ip = addr.hostAddress ?: continue
                    found += score to ip
                }
            }
        }
        return found.minByOrNull { it.first }?.second
    }
}
