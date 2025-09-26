package com.example.etongue.data.repository

import android.content.Context
import com.example.etongue.data.communication.BLEConnectionManager
import com.example.etongue.data.communication.DataPacketParser
import com.example.etongue.data.communication.WiFiConnectionManager
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.monitoring.ConnectionHealth
import com.example.etongue.data.monitoring.ConnectionStatusMonitor
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for device connection functionality
 */
class DeviceConnectionIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var dataPacketParser: DataPacketParser
    private lateinit var bleConnectionManager: BLEConnectionManager
    private lateinit var wifiConnectionManager: WiFiConnectionManager
    private lateinit var repository: DeviceConnectionRepositoryImpl
    private lateinit var connectionMonitor: ConnectionStatusMonitor
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        dataPacketParser = mockk(relaxed = true)
        bleConnectionManager = mockk(relaxed = true)
        wifiConnectionManager = mockk(relaxed = true)
        
        repository = DeviceConnectionRepositoryImpl(
            context = context,
            bleConnectionManager = bleConnectionManager,
            wifiConnectionManager = wifiConnectionManager
        )
        
        connectionMonitor = ConnectionStatusMonitor(repository)
    }
    
    @Test
    fun `repository should be created successfully`() {
        assertNotNull(repository)
        assertNull(repository.getConnectedDevice())
        assertNull(repository.getSignalStrength())
    }
    
    @Test
    fun `connection monitor should be created successfully`() {
        assertNotNull(connectionMonitor)
        
        val stats = connectionMonitor.getConnectionStats()
        assertEquals(ConnectionHealth.UNKNOWN, stats.health)
        assertEquals(0, stats.reconnectionAttempts)
        assertFalse(stats.isMonitoring)
    }
    
    @Test
    fun `device validation should work correctly`() {
        val validBleDevice = ESP32Device(
            id = "test-ble",
            name = "Test BLE ESP32",
            macAddress = "AA:BB:CC:DD:EE:FF",
            signalStrength = -50,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
        
        val validWifiDevice = ESP32Device(
            id = "192.168.1.100",
            name = "Test WiFi ESP32",
            macAddress = "11:22:33:44:55:66",
            signalStrength = -40,
            connectionType = ConnectionType.WIFI
        )
        
        assertTrue(validBleDevice.isValid())
        assertTrue(validWifiDevice.isValid())
        
        // Test device compatibility
        assertTrue(repository.isDeviceSupported(validWifiDevice)) // WiFi is always supported
    }
    
    @Test
    fun `connection health enum should have correct properties`() {
        assertTrue(ConnectionHealth.HEALTHY.isActive())
        assertTrue(ConnectionHealth.CONNECTING.isActive())
        assertTrue(ConnectionHealth.RECONNECTING.isActive())
        
        assertFalse(ConnectionHealth.UNKNOWN.isActive())
        assertFalse(ConnectionHealth.UNHEALTHY.isActive())
        assertFalse(ConnectionHealth.DISCONNECTED.isActive())
        assertFalse(ConnectionHealth.ERROR.isActive())
        assertFalse(ConnectionHealth.FAILED.isActive())
        
        assertEquals("Healthy", ConnectionHealth.HEALTHY.getDisplayName())
        assertEquals("Reconnecting", ConnectionHealth.RECONNECTING.getDisplayName())
    }
    
    @Test
    fun `monitoring can be started and stopped`() {
        connectionMonitor.startMonitoring()
        assertTrue(connectionMonitor.getConnectionStats().isMonitoring)
        
        connectionMonitor.stopMonitoring()
        assertFalse(connectionMonitor.getConnectionStats().isMonitoring)
    }
    
    @Test
    fun `monitoring can be reset`() {
        connectionMonitor.startMonitoring()
        connectionMonitor.resetMonitoring()
        
        val stats = connectionMonitor.getConnectionStats()
        assertEquals(ConnectionHealth.UNKNOWN, stats.health)
        assertEquals(0, stats.reconnectionAttempts)
        assertEquals(0L, stats.lastHealthCheck)
    }
}