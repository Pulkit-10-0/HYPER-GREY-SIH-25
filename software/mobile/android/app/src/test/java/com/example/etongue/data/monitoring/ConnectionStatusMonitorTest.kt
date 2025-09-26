package com.example.etongue.data.monitoring

import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.repository.DeviceConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStatusMonitorTest {
    
    private lateinit var deviceConnectionRepository: DeviceConnectionRepository
    private lateinit var connectionStatusMonitor: ConnectionStatusMonitor
    
    private val testDevice = ESP32Device(
        id = "test-device",
        name = "Test ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    @Before
    fun setup() {
        deviceConnectionRepository = mockk(relaxed = true)
        connectionStatusMonitor = ConnectionStatusMonitor(deviceConnectionRepository)
    }
    
    @Test
    fun `startMonitoring should initialize monitoring state`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns null
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.DISCONNECTED)
        
        // When
        connectionStatusMonitor.startMonitoring()
        
        // Then
        val stats = connectionStatusMonitor.getConnectionStats()
        assertTrue(stats.isMonitoring)
    }
    
    @Test
    fun `stopMonitoring should reset monitoring state`() = runTest {
        // Given
        connectionStatusMonitor.startMonitoring()
        
        // When
        connectionStatusMonitor.stopMonitoring()
        
        // Then
        val stats = connectionStatusMonitor.getConnectionStats()
        assertFalse(stats.isMonitoring)
        assertEquals(ConnectionHealth.UNKNOWN, stats.health)
        assertEquals(0, stats.reconnectionAttempts)
    }
    
    @Test
    fun `health check should mark connection as healthy when device is connected and reachable`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { deviceConnectionRepository.isDeviceReachable(testDevice) } returns true
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000) // Allow health check to run
        
        // Then
        connectionStatusMonitor.connectionHealth.collect { health ->
            assertEquals(ConnectionHealth.HEALTHY, health)
        }
    }
    
    @Test
    fun `health check should mark connection as unhealthy when device is not reachable`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { deviceConnectionRepository.isDeviceReachable(testDevice) } returns false
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000) // Allow health check to run
        
        // Then
        connectionStatusMonitor.connectionHealth.collect { health ->
            assertEquals(ConnectionHealth.UNHEALTHY, health)
        }
    }
    
    @Test
    fun `health check should mark connection as disconnected when no device is connected`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns null
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.DISCONNECTED)
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000) // Allow health check to run
        
        // Then
        connectionStatusMonitor.connectionHealth.collect { health ->
            assertEquals(ConnectionHealth.DISCONNECTED, health)
        }
    }
    
    @Test
    fun `health check should handle error state`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.ERROR)
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000) // Allow health check to run
        
        // Then
        connectionStatusMonitor.connectionHealth.collect { health ->
            assertEquals(ConnectionHealth.ERROR, health)
        }
    }
    
    @Test
    fun `reconnection should be attempted when connection becomes unhealthy`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { deviceConnectionRepository.isDeviceReachable(testDevice) } returns false
        coEvery { deviceConnectionRepository.refreshConnection() } returns Result.success(Unit)
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(15000) // Allow health check and reconnection to run
        
        // Then
        coVerify { deviceConnectionRepository.refreshConnection() }
        
        connectionStatusMonitor.reconnectionAttempts.collect { attempts ->
            assertTrue(attempts > 0)
        }
    }
    
    @Test
    fun `reconnection should try full reconnection if refresh fails`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        coEvery { deviceConnectionRepository.isDeviceReachable(testDevice) } returns false
        coEvery { deviceConnectionRepository.refreshConnection() } returns Result.failure(Exception("Refresh failed"))
        coEvery { deviceConnectionRepository.connectToDevice(testDevice) } returns Result.success(ConnectionStatus.CONNECTED)
        
        // When
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(15000) // Allow health check and reconnection to run
        
        // Then
        coVerify { deviceConnectionRepository.refreshConnection() }
        coVerify { deviceConnectionRepository.connectToDevice(testDevice) }
    }
    
    @Test
    fun `reconnection should stop after max attempts`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.ERROR)
        coEvery { deviceConnectionRepository.refreshConnection() } returns Result.failure(Exception("Connection failed"))
        coEvery { deviceConnectionRepository.connectToDevice(testDevice) } returns Result.failure(Exception("Connection failed"))
        
        // When
        connectionStatusMonitor.startMonitoring()
        
        // Simulate multiple failed reconnection attempts
        repeat(4) {
            advanceTimeBy(15000) // Allow health check and reconnection to run
        }
        
        // Then
        connectionStatusMonitor.connectionHealth.collect { health ->
            assertEquals(ConnectionHealth.FAILED, health)
        }
        
        connectionStatusMonitor.reconnectionAttempts.collect { attempts ->
            assertEquals(3, attempts) // Max attempts reached
        }
    }
    
    @Test
    fun `triggerReconnection should reset attempts and start reconnection`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns testDevice
        coEvery { deviceConnectionRepository.refreshConnection() } returns Result.success(Unit)
        
        // Set some previous attempts
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000)
        
        // When
        val result = connectionStatusMonitor.triggerReconnection()
        
        // Then
        assertTrue(result.isSuccess)
        
        connectionStatusMonitor.reconnectionAttempts.collect { attempts ->
            assertEquals(0, attempts) // Should be reset
        }
    }
    
    @Test
    fun `triggerReconnection should fail when no device is connected`() = runTest {
        // Given
        every { deviceConnectionRepository.getConnectedDevice() } returns null
        
        // When
        val result = connectionStatusMonitor.triggerReconnection()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("No device to reconnect to", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `resetMonitoring should reset all monitoring state`() = runTest {
        // Given
        connectionStatusMonitor.startMonitoring()
        advanceTimeBy(1000)
        
        // When
        connectionStatusMonitor.resetMonitoring()
        
        // Then
        val stats = connectionStatusMonitor.getConnectionStats()
        assertEquals(ConnectionHealth.UNKNOWN, stats.health)
        assertEquals(0, stats.reconnectionAttempts)
        assertEquals(0L, stats.lastHealthCheck)
        assertTrue(stats.lastSuccessfulConnection > 0) // Should be set to current time
    }
    
    @Test
    fun `getConnectionStats should return current monitoring state`() = runTest {
        // Given
        connectionStatusMonitor.startMonitoring()
        
        // When
        val stats = connectionStatusMonitor.getConnectionStats()
        
        // Then
        assertNotNull(stats)
        assertTrue(stats.isMonitoring)
        assertEquals(ConnectionHealth.UNKNOWN, stats.health) // Initial state
        assertEquals(0, stats.reconnectionAttempts)
    }
    
    @Test
    fun `connection health enum should have correct display names`() {
        assertEquals("Unknown", ConnectionHealth.UNKNOWN.getDisplayName())
        assertEquals("Healthy", ConnectionHealth.HEALTHY.getDisplayName())
        assertEquals("Unhealthy", ConnectionHealth.UNHEALTHY.getDisplayName())
        assertEquals("Connecting", ConnectionHealth.CONNECTING.getDisplayName())
        assertEquals("Reconnecting", ConnectionHealth.RECONNECTING.getDisplayName())
        assertEquals("Disconnected", ConnectionHealth.DISCONNECTED.getDisplayName())
        assertEquals("Error", ConnectionHealth.ERROR.getDisplayName())
        assertEquals("Failed", ConnectionHealth.FAILED.getDisplayName())
    }
    
    @Test
    fun `connection health isActive should return correct values`() {
        assertTrue(ConnectionHealth.HEALTHY.isActive())
        assertTrue(ConnectionHealth.CONNECTING.isActive())
        assertTrue(ConnectionHealth.RECONNECTING.isActive())
        
        assertFalse(ConnectionHealth.UNKNOWN.isActive())
        assertFalse(ConnectionHealth.UNHEALTHY.isActive())
        assertFalse(ConnectionHealth.DISCONNECTED.isActive())
        assertFalse(ConnectionHealth.ERROR.isActive())
        assertFalse(ConnectionHealth.FAILED.isActive())
    }
}