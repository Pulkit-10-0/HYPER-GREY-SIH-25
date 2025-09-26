package com.example.etongue.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Utility functions for network operations and debugging
 */
object NetworkUtils {
    
    /**
     * Gets detailed network information for debugging
     */
    fun getNetworkInfo(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)
        
        val info = StringBuilder()
        info.append("Network Status:\n")
        
        if (network == null) {
            info.append("- No active network\n")
            return info.toString()
        }
        
        networkCapabilities?.let { caps ->
            info.append("- WiFi: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}\n")
            info.append("- Cellular: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}\n")
            info.append("- Ethernet: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}\n")
            info.append("- Internet: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}\n")
            info.append("- Validated: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}\n")
        }
        
        // Get link properties
        linkProperties?.let { props ->
            info.append("\nNetwork Configuration:\n")
            info.append("- Interface: ${props.interfaceName}\n")
            
            props.linkAddresses.forEach { linkAddress ->
                val address = linkAddress.address
                if (address.isSiteLocalAddress && !address.isLoopbackAddress) {
                    info.append("- IP: ${address.hostAddress}/${linkAddress.prefixLength}\n")
                }
            }
            
            props.routes.forEach { route ->
                info.append("- Route: ${route.destination} via ${route.gateway}\n")
            }
            
            props.dnsServers.forEach { dns ->
                info.append("- DNS: ${dns.hostAddress}\n")
            }
        }
        
        // Get local IP addresses from all interfaces
        info.append("\nAll Network Interfaces:\n")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                info.append("- ${networkInterface.name} (${if (networkInterface.isUp) "UP" else "DOWN"})\n")
                
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress) {
                            info.append("  - ${address.hostAddress}\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            info.append("- Error getting interfaces: ${e.message}\n")
        }
        
        return info.toString()
    }
    
    /**
     * Tests if a specific IP and port is reachable
     */
    suspend fun testConnection(ip: String, port: Int, timeoutMs: Int = 2000): Boolean {
        return try {
            val address = InetAddress.getByName(ip)
            address.isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets the current device's IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
    
    /**
     * Scans the ARP table for active devices on the network
     */
    fun getArpTableDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec("cat /proc/net/arp")
            val reader = process.inputStream.bufferedReader()
            
            reader.useLines { lines ->
                lines.drop(1) // Skip header
                    .forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 6) {
                            val ip = parts[0]
                            val mac = parts[3]
                            val flags = parts[2]
                            
                            // Only include entries that are not incomplete
                            if (flags != "0x0" && mac != "00:00:00:00:00:00") {
                                devices.add("$ip ($mac)")
                            }
                        }
                    }
            }
            
            process.waitFor()
        } catch (e: Exception) {
            println("Error reading ARP table: ${e.message}")
        }
        return devices
    }
    
    /**
     * Performs a network ping to check if a host is reachable
     */
    suspend fun pingHost(host: String, timeoutMs: Int = 3000): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            address.isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
    }
    

}