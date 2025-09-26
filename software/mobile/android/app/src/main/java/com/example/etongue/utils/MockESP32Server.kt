package com.example.etongue.utils

import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Mock ESP32 server for testing WiFi connections
 * This simulates an ESP32 device that responds to connection attempts
 */
class MockESP32Server(
    private val port: Int = 8080,
    private val discoveryPort: Int = 8081
) {
    private var isRunning = false
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun start() {
        if (isRunning) return
        
        isRunning = true
        println("MockESP32: Starting mock ESP32 server on ports $port and $discoveryPort")
        
        // Start main server
        scope.launch {
            startMainServer()
        }
        
        // Start discovery server
        scope.launch {
            startDiscoveryServer()
        }
    }
    
    fun stop() {
        isRunning = false
        serverSocket?.close()
        discoverySocket?.close()
        scope.cancel()
        println("MockESP32: Stopped mock ESP32 server")
    }
    
    private suspend fun startMainServer() {
        try {
            serverSocket = ServerSocket(port)
            println("MockESP32: Main server listening on port $port")
            
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        scope.launch {
                            handleClient(clientSocket)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        println("MockESP32: Error accepting connection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("MockESP32: Main server error: ${e.message}")
        }
    }
    
    private suspend fun startDiscoveryServer() {
        try {
            discoverySocket = ServerSocket(discoveryPort)
            println("MockESP32: Discovery server listening on port $discoveryPort")
            
            while (isRunning) {
                try {
                    val clientSocket = discoverySocket?.accept()
                    if (clientSocket != null) {
                        scope.launch {
                            handleDiscovery(clientSocket)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        println("MockESP32: Error accepting discovery connection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("MockESP32: Discovery server error: ${e.message}")
        }
    }
    
    private suspend fun handleDiscovery(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            val request = reader.readLine()
            println("MockESP32: Discovery request: $request")
            
            if (request == "DISCOVER") {
                // Respond with ESP32 identification
                writer.println("ESP32|Mock E-Tongue Device|AA:BB:CC:DD:EE:FF")
                println("MockESP32: Sent discovery response")
            }
            
            clientSocket.close()
        } catch (e: Exception) {
            println("MockESP32: Discovery error: ${e.message}")
        }
    }
    
    private suspend fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            println("MockESP32: Client connected from ${clientSocket.remoteSocketAddress}")
            
            var isStreaming = false
            
            while (clientSocket.isConnected && !clientSocket.isClosed) {
                val request = reader.readLine() ?: break
                println("MockESP32: Received: $request")
                
                when (request.trim().uppercase()) {
                    "CONNECT" -> {
                        writer.println("CONNECTED")
                        println("MockESP32: Connection established")
                    }
                    
                    "PING" -> {
                        writer.println("PONG")
                        println("MockESP32: Responded to ping")
                    }
                    
                    "START_STREAM" -> {
                        writer.println("OK")
                        isStreaming = true
                        println("MockESP32: Started streaming")
                        
                        // Start sending mock sensor data
                        scope.launch {
                            while (isStreaming && clientSocket.isConnected && !clientSocket.isClosed) {
                                val sensorData = generateMockSensorData()
                                writer.println(sensorData)
                                println("MockESP32: Sent: $sensorData")
                                delay(1000) // Send data every second
                            }
                        }
                    }
                    
                    "STOP_STREAM" -> {
                        writer.println("OK")
                        isStreaming = false
                        println("MockESP32: Stopped streaming")
                    }
                    
                    "DISCONNECT" -> {
                        writer.println("OK")
                        println("MockESP32: Disconnecting")
                        break
                    }
                    
                    else -> {
                        writer.println("UNKNOWN_COMMAND")
                        println("MockESP32: Unknown command: $request")
                    }
                }
            }
            
            clientSocket.close()
            println("MockESP32: Client disconnected")
            
        } catch (e: Exception) {
            println("MockESP32: Client error: ${e.message}")
        }
    }
    
    private fun generateMockSensorData(): String {
        // Use real hardcoded sensor data
        return HardcodedSensorData.getNextSensorData()
    }
}