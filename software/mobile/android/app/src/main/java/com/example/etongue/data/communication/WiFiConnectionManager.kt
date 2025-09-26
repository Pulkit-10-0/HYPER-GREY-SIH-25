package com.example.etongue.data.communication

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.models.SensorDataPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Manages WiFi socket connections to ESP32 devices
 */
class WiFiConnectionManager(
    private val context: Context,
    private val dataPacketParser: DataPacketParser
) : ConnectionManager() {
    
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val CONNECTION_TIMEOUT = 5000 // 5 seconds
        private const val READ_TIMEOUT = 10000 // 10 seconds
        private const val DISCOVERY_PORT = 8081 // Port for device discovery
    }
    
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var connectedDevice: ESP32Device? = null
    private var isStreaming = false
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    override suspend fun scanForDevices(): Flow<List<ESP32Device>> = callbackFlow {
        val devices = mutableListOf<ESP32Device>()
        
        try {
            // Check WiFi connection first
            if (!isWiFiConnected()) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            // Get local IP to determine network
            val localIp = getLocalIpAddress()
            if (localIp == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            val networkPrefix = localIp.substringBeforeLast(".")
            
            // Send initial empty list
            trySend(emptyList())
            
            // Simple scan - just check a few common ESP32 IPs
            val commonIPs = listOf(100, 101, 102, 200, 201, 202)
            
            while (true) {
                for (lastOctet in commonIPs) {
                    val ip = "$networkPrefix.$lastOctet"
                    
                    // Skip our own IP
                    if (ip == localIp) continue
                    
                    // Try to connect to ESP32 port
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, DEFAULT_PORT), 2000)
                        socket.close()
                        
                        val device = ESP32Device(
                            id = ip,
                            name = "ESP32 Device ($ip)",
                            macAddress = "Unknown",
                            signalStrength = -35,
                            connectionType = ConnectionType.WIFI
                        )
                        
                        if (!devices.any { it.id == ip }) {
                            devices.add(device)
                            trySend(devices.toList())
                        }
                    } catch (e: Exception) {
                        // Device not found - normal
                    }
                }
                
                // Wait 5 seconds before next scan
                kotlinx.coroutines.delay(5000)
            }
            
        } catch (e: Exception) {
            close(e)
        }
        
        awaitClose { }
    }
    
    override suspend fun connect(device: ESP32Device): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isDeviceCompatible(device)) {
                    return@withContext Result.failure(IllegalArgumentException("Device is not compatible with WiFi connection"))
                }
                
                if (!isWiFiConnected()) {
                    return@withContext Result.failure(Exception("WiFi is not connected"))
                }
                
                _connectionStatus.value = ConnectionStatus.CONNECTING
                println("WiFi Connecting to ${device.name} at ${device.id}:$DEFAULT_PORT")
                
                socket = Socket()
                socket?.connect(InetSocketAddress(device.id, DEFAULT_PORT), CONNECTION_TIMEOUT)
                socket?.soTimeout = READ_TIMEOUT
                
                writer = socket?.getOutputStream()?.let { PrintWriter(it, true) }
                reader = socket?.getInputStream()?.let { BufferedReader(InputStreamReader(it)) }
                
                // Send connection handshake
                writer?.println("CONNECT")
                val response = reader?.readLine()
                
                println("WiFi Handshake response: $response")
                
                if (response == "CONNECTED") {
                    connectedDevice = device
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    println("WiFi Connected successfully to ${device.name}")
                    Result.success(Unit)
                } else {
                    disconnect()
                    Result.failure(Exception("Connection handshake failed: $response"))
                }
            } catch (e: SocketTimeoutException) {
                _connectionStatus.value = ConnectionStatus.ERROR
                Result.failure(Exception("Connection timeout"))
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                Result.failure(e)
            }
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                isStreaming = false
                writer?.println("DISCONNECT")
                writer?.close()
                reader?.close()
                socket?.close()
                
                writer = null
                reader = null
                socket = null
                connectedDevice = null
                
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun startStreaming(): Flow<SensorDataPacket> {
        return if (_connectionStatus.value.canStartStreaming()) {
            _connectionStatus.value = ConnectionStatus.STREAMING
            isStreaming = true
            
            sendCommand("START_STREAM")
            
            callbackFlow {
                withContext(Dispatchers.IO) {
                    try {
                        while (isStreaming && socket?.isConnected == true) {
                            val data = reader?.readLine()
                            if (data != null && data.isNotBlank()) {
                                val parseResult = dataPacketParser.parseDataPacket(data, connectedDevice?.id ?: "")
                                parseResult.getOrNull()?.let { packet ->
                                    trySend(packet)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        close(e)
                    }
                }
                
                awaitClose {
                    isStreaming = false
                }
            }
        } else {
            emptyFlow()
        }
    }
    
    override suspend fun stopStreaming(): Result<Unit> {
        return try {
            isStreaming = false
            sendCommand("STOP_STREAM")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getConnectionStatus(): Flow<ConnectionStatus> = connectionStatus
    
    override fun getConnectedDevice(): ESP32Device? = connectedDevice
    
    override suspend fun sendCommand(command: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                writer?.println(command)
                writer?.flush()
                
                // Wait for acknowledgment
                val response = reader?.readLine()
                if (response == "OK") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Command failed: $command, Response: $response"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override fun isDeviceCompatible(device: ESP32Device): Boolean {
        return device.connectionType == ConnectionType.WIFI
    }
    
    /**
     * Checks if WiFi is connected
     */
    private fun isWiFiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Gets the local IP address
     */
    private fun getLocalIpAddress(): String? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(network)
            
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val address = linkAddress.address
                if (address.isSiteLocalAddress && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
    
    /**
     * Pings a device to check if it's reachable
     */
    suspend fun pingDevice(device: ESP32Device): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.id, DEFAULT_PORT), 2000)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}