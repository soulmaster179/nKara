package com.ntech.nkara.feature.lan

import java.net.Inet4Address
import java.net.NetworkInterface

object LanNetworkInfo {
    fun hostAddress(port: Int = 8877): String {
        val address = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
        return address?.let { "$it:$port" } ?: "Chưa kết nối Wi-Fi"
    }
}
