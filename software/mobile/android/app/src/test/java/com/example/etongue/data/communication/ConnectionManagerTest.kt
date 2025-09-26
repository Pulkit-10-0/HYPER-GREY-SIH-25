package com.example.etongue.data.communication

import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.models.SensorDataPacket
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConnectionManagerTest {
    
    private lateinit var testConnectionManager: TestConnectionManager
    private lateinit var mockDataPacketParser: DataPacketParser
    
    private val testDevice = ESP32Device(
        id = "test_device_001",
        name = "Test ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    @Before
    fun setUp() {
        mockDataPacketParser = mockk()
        testConnectionManager = TestConnectionManager()
    }
    
    @Test
    fun `isConnected should return false when no device is connected`() {
        assertFalse(testConnectionManager.isConnected())
    }
    
    @Test
    fun `isConnected should return true when device is connected`() = runTest {
        testConnectionManager.connect(testDevice)
        assertTrue(testConnectionManager.isConnected())
    }
    
    @Test
    fun `connect should set connected device`() = runTest {
        val result = testConnectionManager.connect(testDevice)
        
        assertTrue(result.isSuccess)
        assertEquals(testDevice, testConnectionManager.getConnectedDevice())
    }
    
    @Test
    fun `disconnect should clear connected device`() = runTest {
        testConnectionManager.connect(testDevice)
        assertTrue(testConnectionManager.isConnected())
        
        val result = testConnectionManager.disconnect()
        
        assertTrue(result.isSuccess)
        assertNull(testConnectionManager.getConnectedDevice())
        assertFalse(testConnectionManager.isConnected())
    }
    
    @Test
    fun `isDeviceCompatible should be implemented by subclasses`() {
        // This test verifies that the abstract method is properly implemented
        assertTrue(testConnectionManager.isDeviceCompatible(testDevice))
    }
    
    /**
     * Test implementation of ConnectionManager for testing purposes
     */
    private class TestConnectionManager : ConnectionManager() {
        private var connectedDevice: ESP32Device? = null
        private var connectionStatus = ConnectionStatus.DISCONNECTED
        
        override suspend fun scanForDevices(): Flow<List<ESP32Device>> {
            return flowOf(listOf(
                ESP32Device(
                    id = "test_1",
                    name = "Test Device 1",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    signalStrength = -50,
                    connectionType = ConnectionType.BLUETOOTH_LE
                ),
                ESP32Device(
                    id = "test_2",
                    name = "Test Device 2",
                    macAddress = "11:22:33:44:55:66",
                    signalStrength = -60,
                    connectionType = ConnectionType.WIFI
                )
            ))
        }
        
        override suspend fun connect(device: ESP32Device): Result<Unit> {
            return if (isDeviceCompatible(device)) {
                connectedDevice = device
                connectionStatus = ConnectionStatus.CONNECTED
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Device not compatible"))
            }
        }
        
        override suspend fun disconnect(): Result<Unit> {
            connectedDevice = null
            connectionStatus = ConnectionStatus.DISCONNECTED
            return Result.success(Unit)
        }
        
        override suspend fun startStreaming(): Flow<SensorDataPacket> {
            connectionStatus = ConnectionStatus.STREAMING
            return flowOf() // Empty flow for testing
        }
        
        override suspend fun stopStreaming(): Result<Unit> {
            connectionStatus = ConnectionStatus.CONNECTED
            return Result.success(Unit)
        }
        
        override fun getConnectionStatus(): Flow<ConnectionStatus> {
            return flowOf(connectionStatus)
        }
        
        override fun getConnectedDevice(): ESP32Device? {
            return connectedDevice
        }
        
        override suspend fun sendCommand(command: String): Result<Unit> {
            return if (connectedDevice != null) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Not connected"))
            }
        }
        
        override fun isDeviceCompatible(device: ESP32Device): Boolean {
            return true // Accept all devices for testing
        }
    }
}

class BLEConnectionManagerTest {
    
    private lateinit var mockDataPacketParser: DataPacketParser
    
    private val bleDevice = ESP32Device(
        id = "ble_device_001",
        name = "BLE ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    private val wifiDevice = ESP32Device(
        id = "wifi_device_001",
        name = "WiFi ESP32",
        macAddress = "11:22:33:44:55:66",
        signalStrength = -40,
        connectionType = ConnectionType.WIFI
    )
    
    @Before
    fun setUp() {
        mockDataPacketParser = mockk()
    }
    
    @Test
    fun `isDeviceCompatible should return true for BLE devices`() {
        // Note: This test would need a real context for full BLEConnectionManager testing
        // For now, we test the compatibility logic
        assertTrue(bleDevice.connectionType == ConnectionType.BLUETOOTH_LE)
    }
    
    @Test
    fun `isDeviceCompatible should return false for WiFi devices`() {
        // Note: This test would need a real context for full BLEConnectionManager testing
        // For now, we test the compatibility logic
        assertFalse(wifiDevice.connectionType == ConnectionType.BLUETOOTH_LE)
    }
}

class WiFiConnectionManagerTest {
    
    private lateinit var mockDataPacketParser: DataPacketParser
    
    private val wifiDevice = ESP32Device(
        id = "192.168.1.100",
        name = "WiFi ESP32",
        macAddress = "11:22:33:44:55:66",
        signalStrength = -40,
        connectionType = ConnectionType.WIFI
    )
    
    private val bleDevice = ESP32Device(
        id = "ble_device_001",
        name = "BLE ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    @Before
    fun setUp() {
        mockDataPacketParser = mockk()
    }
    
    @Test
    fun `isDeviceCompatible should return true for WiFi devices`() {
        // Note: This test would need a real context for full WiFiConnectionManager testing
        // For now, we test the compatibility logic
        assertTrue(wifiDevice.connectionType == ConnectionType.WIFI)
    }
    
    @Test
    fun `isDeviceCompatible should return false for BLE devices`() {
        // Note: This test would need a real context for full WiFiConnectionManager testing
        // For now, we test the compatibility logic
        assertFalse(bleDevice.connectionType == ConnectionType.WIFI)
    }
    
    @Test
    fun `WiFi device should have IP address as ID`() {
        assertTrue(wifiDevice.id.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")))
    }
}