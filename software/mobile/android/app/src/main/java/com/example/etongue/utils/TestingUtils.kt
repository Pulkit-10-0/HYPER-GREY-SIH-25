package com.example.etongue.utils

/**
 * Utilities for testing the E-Tongue app
 */
object TestingUtils {
    private var mockServer: MockESP32Server? = null
    
    /**
     * Starts a mock ESP32 server for testing WiFi connections
     */
    fun startMockESP32Server(): String {
        if (mockServer != null) {
            return "Mock server is already running"
        }
        
        try {
            mockServer = MockESP32Server()
            mockServer?.start()
            
            val localIp = NetworkUtils.getLocalIpAddress() ?: "Unknown"
            return "Mock ESP32 server started at $localIp:8080\nDiscovery port: 8081\nUse this IP for manual WiFi connection"
        } catch (e: Exception) {
            return "Failed to start mock server: ${e.message}"
        }
    }
    
    /**
     * Stops the mock ESP32 server
     */
    fun stopMockESP32Server(): String {
        return if (mockServer != null) {
            mockServer?.stop()
            mockServer = null
            "Mock ESP32 server stopped"
        } else {
            "Mock server is not running"
        }
    }
    
    /**
     * Gets the status of the mock server
     */
    fun getMockServerStatus(): String {
        return if (mockServer != null) {
            val localIp = NetworkUtils.getLocalIpAddress() ?: "Unknown"
            "Mock server running at $localIp:8080"
        } else {
            "Mock server not running"
        }
    }
    
    /**
     * Loads hardcoded sensor data directly into the app for testing
     */
    fun loadHardcodedData(): String {
        return try {
            val dataList = HardcodedSensorData.getRealSensorDataList()
            "Loaded ${dataList.size} sensor data entries from hardcoded data"
        } catch (e: Exception) {
            "Failed to load hardcoded data: ${e.message}"
        }
    }
    
    /**
     * Gets common test connection details
     */
    fun getTestConnectionInfo(): String {
        val localIp = NetworkUtils.getLocalIpAddress() ?: "192.168.1.100"
        return """
            Test Connection Information:
            
            WiFi Manual Connection:
            - IP Address: $localIp
            - Port: 8080
            - Discovery Port: 8081
            
            Common ESP32 IPs to try:
            - 192.168.1.100
            - 192.168.4.1 (ESP32 AP mode)
            - 10.0.0.100
            - Your device IP: $localIp
            
            Bluetooth LE:
            - Use MAC address format: AA:BB:CC:DD:EE:FF
            - Common ESP32 names: ESP32, E-Tongue, etc.
            
            To test with mock server:
            1. Start mock server (see debug info)
            2. Use your device's IP address for manual WiFi connection
            3. The mock server will respond with REAL sensor data
            
            Hardcoded Data:
            - ${HardcodedSensorData.getRealSensorDataList().size} real sensor readings available
            - Data includes: SS, Cu, Zn, Ag, Pt, Temp, pH, TDS, UV, Soil
            - Timestamps from Sept 10-11, 2025
        """.trimIndent()
    }
}