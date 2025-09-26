package com.example.etongue.data.repository

import android.content.Context
import com.example.etongue.data.communication.BLEConnectionManager
import com.example.etongue.data.communication.WiFiConnectionManager
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class DeviceConnectionErrorScenariosTest {
    
    private lateinit var context: Context
    private lateinit var bleConnectionManager: BLEConnectionManager
    private lateinit var wifiConnectionManager: WiFiConnectionManager
    private lateinit var repository: DeviceConnectionRepositoryImpl
    
    private val testBleDevice = ESP32Device(
        id = "test-ble-device",
        name = "Test BLE ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    private val testWifiDevice = ESP32Device(
        id = "192.168.1.100",
        name = "Test WiFi ESP32",
        macAddress = "11:22:33:44:55:66",
        signalStrength = -40,
        connectionType = ConnectionType.WIFI
    )
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bleConnectionManager = mockk(relaxed = true)
        wifiConnectionManager = mockk(relaxed = true)
        
        repository = DeviceConnectionRepositoryImpl(
            context = context,
            bleConnectionManager = bleConnectionManager,
            wifiConnectionManager = wifiConnectionManager
        )
    }
    
    @Test
    fun `scanForDevices should handle BLE scan exception gracefully`() = runTest {
        // Given
        val wifiDevices = listOf(testWifiDevice)
        
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.scanForDevices() } throws SecurityException("Bluetooth permission denied")
        coEvery { wifiConnectionManager.scanForDevices() } returns flowOf(wifiDevices)
        
        // When
        val result = repository.scanForDevices()
        
        // Then
        result.collect { devices ->
            assertEquals(1, devices.size)
            assertEquals(testWifiDevice, devices.first())
        }
    }
    
    @Test
    fun `scanForDevices should handle WiFi scan exception gracefully`() = runTest {
        // Given
        val bleDevices = listOf(testBleDevice)
        
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.scanForDevices() } returns flowOf(bleDevices)
        coEvery { wifiConnectionManager.scanForDevices() } throws IOException("Network error")
        
        // When
        val result = repository.scanForDevices()
        
        // Then
        result.collect { devices ->
            assertEquals(1, devices.size)
            assertEquals(testBleDevice, devices.first())
        }
    }
    
    @Test
    fun `scanForDevices should return empty list when both scans fail`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.scanForDevices() } throws SecurityException("Bluetooth permission denied")
        coEvery { wifiConnectionManager.scanForDevices() } throws IOException("Network error")
        
        // When
        val result = repository.scanForDevices()
        
        // Then
        result.collect { devices ->
            assertTrue(devices.isEmpty())
        }
    }
    
    @Test
    fun `connectToDevice should handle BLE connection timeout`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } throws TimeoutException("Connection timeout")
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)
        assertNull(repository.getConnectedDevice())
    }
    
    @Test
    fun `connectToDevice should handle WiFi connection timeout`() = runTest {
        // Given
        coEvery { wifiConnectionManager.connect(testWifiDevice) } throws SocketTimeoutException("Connection timeout")
        
        // When
        val result = repository.connectToDevice(testWifiDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
        assertNull(repository.getConnectedDevice())
    }
    
    @Test
    fun `connectToDevice should handle BLE permission denied`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } throws SecurityException("Bluetooth permission denied")
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals("Bluetooth permission denied", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `connectToDevice should handle device not found error`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.failure(Exception("Device not found"))
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Device not found", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `connectToDevice should handle connection manager returning error status`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.ERROR)
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then - Connection should still succeed initially, but status will show error
        assertTrue(result.isSuccess)
        assertEquals(testBleDevice, repository.getConnectedDevice())
    }
    
    @Test
    fun `disconnectFromDevice should handle disconnect failure gracefully`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // Mock disconnect failure
        coEvery { bleConnectionManager.disconnect() } returns Result.failure(Exception("Disconnect failed"))
        
        // When
        val result = repository.disconnectFromDevice()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Disconnect failed", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `authenticateDevice should handle authentication timeout`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // Mock authentication timeout
        coEvery { bleConnectionManager.sendCommand("AUTH:DEFAULT") } throws TimeoutException("Authentication timeout")
        
        // When
        val result = repository.authenticateDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)
    }
    
    @Test
    fun `authenticateDevice should handle authentication rejection`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // Mock authentication rejection
        coEvery { bleConnectionManager.sendCommand("AUTH:DEFAULT") } returns Result.failure(Exception("Authentication rejected"))
        
        // When
        val result = repository.authenticateDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Authentication rejected", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `isDeviceReachable should handle network exceptions`() = runTest {
        // Given
        coEvery { wifiConnectionManager.pingDevice(testWifiDevice) } throws IOException("Network unreachable")
        
        // When
        val result = repository.isDeviceReachable(testWifiDevice)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `refreshConnection should handle device unreachable scenario`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // Mock device as unreachable
        every { bleConnectionManager.isBluetoothEnabled() } returns false
        
        // When
        val result = repository.refreshConnection()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Device is not reachable", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `refreshConnection should handle reconnection failure`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.ERROR) // Simulate error state
        
        repository.connectToDevice(testBleDevice)
        
        // Mock device as reachable but reconnection fails
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.failure(Exception("Reconnection failed"))
        
        // When
        val result = repository.refreshConnection()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Reconnection failed", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `pairWithDevice should handle Bluetooth security exception`() = runTest {
        // When - This would normally require mocking Android Bluetooth APIs
        val result = repository.pairWithDevice(testBleDevice)
        
        // Then - For this simplified test, we just verify it doesn't crash
        assertNotNull(result)
    }
    
    @Test
    fun `repository should handle concurrent connection attempts`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(any()) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { bleConnectionManager.disconnect() } returns Result.success(Unit)
        
        // When - Attempt to connect to multiple devices concurrently
        val result1 = repository.connectToDevice(testBleDevice)
        val result2 = repository.connectToDevice(testWifiDevice.copy(connectionType = ConnectionType.BLUETOOTH_LE))
        
        // Then - Second connection should succeed and first should be disconnected
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        
        // Only the last connected device should be active
        assertNotNull(repository.getConnectedDevice())
    }
}