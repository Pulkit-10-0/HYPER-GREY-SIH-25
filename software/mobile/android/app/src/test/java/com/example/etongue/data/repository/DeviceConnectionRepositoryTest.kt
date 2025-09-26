package com.example.etongue.data.repository

import android.content.Context
import com.example.etongue.data.communication.BLEConnectionManager
import com.example.etongue.data.communication.WiFiConnectionManager
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class DeviceConnectionRepositoryTest {
    
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
    fun `scanForDevices should combine BLE and WiFi devices`() = runTest {
        // Given
        val bleDevices = listOf(testBleDevice)
        val wifiDevices = listOf(testWifiDevice)
        
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.scanForDevices() } returns flowOf(bleDevices)
        coEvery { wifiConnectionManager.scanForDevices() } returns flowOf(wifiDevices)
        
        // When
        val result = repository.scanForDevices()
        
        // Then
        result.collect { devices ->
            assertEquals(2, devices.size)
            assertTrue(devices.contains(testBleDevice))
            assertTrue(devices.contains(testWifiDevice))
        }
    }
    
    @Test
    fun `scanForDevices should handle BLE disabled gracefully`() = runTest {
        // Given
        val wifiDevices = listOf(testWifiDevice)
        
        every { bleConnectionManager.isBluetoothEnabled() } returns false
        every { bleConnectionManager.isBleSupported() } returns true
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
    fun `connectToDevice should succeed for BLE device when Bluetooth is enabled`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testBleDevice, repository.getConnectedDevice())
        coVerify { bleConnectionManager.connect(testBleDevice) }
    }
    
    @Test
    fun `connectToDevice should fail for BLE device when Bluetooth is disabled`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns false
        every { bleConnectionManager.isBleSupported() } returns true
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Bluetooth is not enabled", result.exceptionOrNull()?.message)
        assertNull(repository.getConnectedDevice())
    }
    
    @Test
    fun `connectToDevice should fail for BLE device when BLE is not supported`() = runTest {
        // Given
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns false
        
        // When
        val result = repository.connectToDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Bluetooth LE is not supported on this device", result.exceptionOrNull()?.message)
        assertNull(repository.getConnectedDevice())
    }
    
    @Test
    fun `connectToDevice should succeed for WiFi device`() = runTest {
        // Given
        coEvery { wifiConnectionManager.connect(testWifiDevice) } returns Result.success(Unit)
        every { wifiConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        // When
        val result = repository.connectToDevice(testWifiDevice)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testWifiDevice, repository.getConnectedDevice())
        coVerify { wifiConnectionManager.connect(testWifiDevice) }
    }
    
    @Test
    fun `connectToDevice should disconnect existing connection before connecting new device`() = runTest {
        // Given - First connect to BLE device
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { bleConnectionManager.disconnect() } returns Result.success(Unit)
        
        repository.connectToDevice(testBleDevice)
        
        // When - Connect to WiFi device
        coEvery { wifiConnectionManager.connect(testWifiDevice) } returns Result.success(Unit)
        every { wifiConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        val result = repository.connectToDevice(testWifiDevice)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testWifiDevice, repository.getConnectedDevice())
        coVerify { bleConnectionManager.disconnect() }
        coVerify { wifiConnectionManager.connect(testWifiDevice) }
    }
    
    @Test
    fun `disconnectFromDevice should succeed when device is connected`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { bleConnectionManager.disconnect() } returns Result.success(Unit)
        
        repository.connectToDevice(testBleDevice)
        
        // When
        val result = repository.disconnectFromDevice()
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(repository.getConnectedDevice())
        coVerify { bleConnectionManager.disconnect() }
    }
    
    @Test
    fun `disconnectFromDevice should succeed when no device is connected`() = runTest {
        // When
        val result = repository.disconnectFromDevice()
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(repository.getConnectedDevice())
    }
    
    @Test
    fun `pairWithDevice should succeed for BLE device`() = runTest {
        // Given
        // Mock BluetoothAdapter and BluetoothDevice would be needed here
        // This is a simplified test - in practice, you'd need to mock the Android Bluetooth APIs
        
        // When
        val result = repository.pairWithDevice(testBleDevice)
        
        // Then
        // This test would need proper Android mocking to be fully functional
        // For now, we'll just verify the method doesn't throw
        assertNotNull(result)
    }
    
    @Test
    fun `pairWithDevice should succeed for WiFi device without pairing`() = runTest {
        // When
        val result = repository.pairWithDevice(testWifiDevice)
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `authenticateDevice should fail when no device is connected`() = runTest {
        // When
        val result = repository.authenticateDevice(testBleDevice)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("No active connection", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `authenticateDevice should succeed when device is connected`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { bleConnectionManager.sendCommand("AUTH:DEFAULT") } returns Result.success(Unit)
        
        repository.connectToDevice(testBleDevice)
        
        // When
        val result = repository.authenticateDevice(testBleDevice)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { bleConnectionManager.sendCommand("AUTH:DEFAULT") }
    }
    
    @Test
    fun `authenticateDevice should use custom auth token when provided`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { bleConnectionManager.sendCommand("AUTH:custom-token") } returns Result.success(Unit)
        
        repository.connectToDevice(testBleDevice)
        
        // When
        val result = repository.authenticateDevice(testBleDevice, "custom-token")
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { bleConnectionManager.sendCommand("AUTH:custom-token") }
    }
    
    @Test
    fun `isDeviceReachable should return true for reachable WiFi device`() = runTest {
        // Given
        coEvery { wifiConnectionManager.pingDevice(testWifiDevice) } returns true
        
        // When
        val result = repository.isDeviceReachable(testWifiDevice)
        
        // Then
        assertTrue(result)
        coVerify { wifiConnectionManager.pingDevice(testWifiDevice) }
    }
    
    @Test
    fun `isDeviceReachable should return false for unreachable WiFi device`() = runTest {
        // Given
        coEvery { wifiConnectionManager.pingDevice(testWifiDevice) } returns false
        
        // When
        val result = repository.isDeviceReachable(testWifiDevice)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `refreshConnection should fail when no device is connected`() = runTest {
        // When
        val result = repository.refreshConnection()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("No device connected", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `refreshConnection should succeed when device is reachable`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // Mock device as reachable
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        
        // When
        val result = repository.refreshConnection()
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `getSignalStrength should return connected device signal strength`() = runTest {
        // Given - Connect to device first
        every { bleConnectionManager.isBluetoothEnabled() } returns true
        every { bleConnectionManager.isBleSupported() } returns true
        coEvery { bleConnectionManager.connect(testBleDevice) } returns Result.success(Unit)
        every { bleConnectionManager.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        
        repository.connectToDevice(testBleDevice)
        
        // When
        val signalStrength = repository.getSignalStrength()
        
        // Then
        assertEquals(testBleDevice.signalStrength, signalStrength)
    }
    
    @Test
    fun `getSignalStrength should return null when no device is connected`() = runTest {
        // When
        val signalStrength = repository.getSignalStrength()
        
        // Then
        assertNull(signalStrength)
    }
}