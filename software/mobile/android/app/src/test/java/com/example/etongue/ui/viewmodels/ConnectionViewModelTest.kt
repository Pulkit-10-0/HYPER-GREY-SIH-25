package com.example.etongue.ui.viewmodels

import app.cash.turbine.test
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.repository.DeviceConnectionRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private lateinit var mockRepository: DeviceConnectionRepository
    private lateinit var viewModel: ConnectionViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testDevice = ESP32Device(
        id = "test-device-1",
        name = "ESP32 Test Device",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -45,
        connectionType = ConnectionType.BLUETOOTH_LE
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)
        
        // Setup default mock behaviors
        every { mockRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.DISCONNECTED)
        every { mockRepository.getConnectedDevice() } returns null
        
        viewModel = ConnectionViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be correct`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(emptyList<ESP32Device>(), initialState.availableDevices)
            assertNull(initialState.selectedDevice)
            assertNull(initialState.connectedDevice)
            assertFalse(initialState.isScanning)
            assertFalse(initialState.isConnecting)
            assertNull(initialState.selectedConnectionType)
            assertNull(initialState.errorMessage)
        }
    }

    @Test
    fun `startScanning should update scanning state and collect devices`() = runTest {
        val deviceList = listOf(testDevice)
        coEvery { mockRepository.scanForDevices() } returns flowOf(deviceList)

        viewModel.startScanning()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(deviceList, state.availableDevices)
            assertFalse(state.isScanning)
            assertNull(state.errorMessage)
        }

        coVerify { mockRepository.scanForDevices() }
    }

    @Test
    fun `startScanning should handle errors gracefully`() = runTest {
        val errorMessage = "Scan failed"
        coEvery { mockRepository.scanForDevices() } throws Exception(errorMessage)

        viewModel.startScanning()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isScanning)
            assertTrue(state.errorMessage?.contains("Scanning error") == true)
        }
    }

    @Test
    fun `stopScanning should update scanning state`() = runTest {
        viewModel.stopScanning()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isScanning)
        }
    }

    @Test
    fun `connectToDevice should update connecting state and handle success`() = runTest {
        coEvery { mockRepository.connectToDevice(testDevice) } returns Result.success(ConnectionStatus.CONNECTED)

        viewModel.connectToDevice(testDevice)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testDevice, state.selectedDevice)
            assertEquals(testDevice, state.connectedDevice)
            assertFalse(state.isConnecting)
            assertNull(state.errorMessage)
        }

        coVerify { mockRepository.connectToDevice(testDevice) }
    }

    @Test
    fun `connectToDevice should handle connection failure`() = runTest {
        val errorMessage = "Connection failed"
        coEvery { mockRepository.connectToDevice(testDevice) } returns Result.failure(Exception(errorMessage))

        viewModel.connectToDevice(testDevice)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isConnecting)
            assertNull(state.selectedDevice)
            assertTrue(state.errorMessage?.contains("Failed to connect") == true)
        }
    }

    @Test
    fun `disconnectFromDevice should handle successful disconnection`() = runTest {
        coEvery { mockRepository.disconnectFromDevice() } returns Result.success(Unit)

        viewModel.disconnectFromDevice()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.connectedDevice)
            assertNull(state.selectedDevice)
            assertNull(state.errorMessage)
        }

        coVerify { mockRepository.disconnectFromDevice() }
    }

    @Test
    fun `disconnectFromDevice should handle disconnection failure`() = runTest {
        val errorMessage = "Disconnect failed"
        coEvery { mockRepository.disconnectFromDevice() } returns Result.failure(Exception(errorMessage))

        viewModel.disconnectFromDevice()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.errorMessage?.contains("Failed to disconnect") == true)
        }
    }

    @Test
    fun `selectDevice should update selected device`() = runTest {
        viewModel.selectDevice(testDevice)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testDevice, state.selectedDevice)
        }
    }

    @Test
    fun `clearError should remove error message`() = runTest {
        // First set an error
        coEvery { mockRepository.connectToDevice(testDevice) } returns Result.failure(Exception("Test error"))
        viewModel.connectToDevice(testDevice)
        advanceUntilIdle()

        // Then clear it
        viewModel.clearError()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `filterDevicesByType should update selected connection type`() = runTest {
        viewModel.filterDevicesByType(ConnectionType.BLUETOOTH_LE)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(ConnectionType.BLUETOOTH_LE, state.selectedConnectionType)
        }
    }

    @Test
    fun `getFilteredDevices should return all devices when no filter is set`() = runTest {
        val devices = listOf(
            testDevice,
            testDevice.copy(id = "device-2", connectionType = ConnectionType.WIFI)
        )
        
        coEvery { mockRepository.scanForDevices() } returns flowOf(devices)
        viewModel.startScanning()
        advanceUntilIdle()

        val filteredDevices = viewModel.getFilteredDevices()
        assertEquals(devices, filteredDevices)
    }

    @Test
    fun `getFilteredDevices should return filtered devices when filter is set`() = runTest {
        val bleDevice = testDevice
        val wifiDevice = testDevice.copy(id = "device-2", connectionType = ConnectionType.WIFI)
        val devices = listOf(bleDevice, wifiDevice)
        
        coEvery { mockRepository.scanForDevices() } returns flowOf(devices)
        viewModel.startScanning()
        advanceUntilIdle()
        
        viewModel.filterDevicesByType(ConnectionType.BLUETOOTH_LE)

        val filteredDevices = viewModel.getFilteredDevices()
        assertEquals(listOf(bleDevice), filteredDevices)
    }

    @Test
    fun `refreshConnection should handle successful refresh`() = runTest {
        coEvery { mockRepository.refreshConnection() } returns Result.success(Unit)

        viewModel.refreshConnection()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.errorMessage)
        }

        coVerify { mockRepository.refreshConnection() }
    }

    @Test
    fun `refreshConnection should handle refresh failure`() = runTest {
        val errorMessage = "Refresh failed"
        coEvery { mockRepository.refreshConnection() } returns Result.failure(Exception(errorMessage))

        viewModel.refreshConnection()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.errorMessage?.contains("Failed to refresh connection") == true)
        }
    }

    @Test
    fun `connection status changes should update UI state`() = runTest {
        val connectedDevice = testDevice
        every { mockRepository.getConnectedDevice() } returns connectedDevice
        
        // Use MutableStateFlow to control emissions
        val statusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        every { mockRepository.getConnectionStatus() } returns statusFlow

        // Create new viewModel to trigger connection status observation
        viewModel = ConnectionViewModel(mockRepository)
        advanceUntilIdle()

        // Test initial status
        viewModel.connectionStatus.test {
            assertEquals(ConnectionStatus.DISCONNECTED, awaitItem())
            
            // Emit new status
            statusFlow.value = ConnectionStatus.CONNECTING
            assertEquals(ConnectionStatus.CONNECTING, awaitItem())
            
            // Emit connected status
            statusFlow.value = ConnectionStatus.CONNECTED
            assertEquals(ConnectionStatus.CONNECTED, awaitItem())
        }
    }

    @Test
    fun `connection status CONNECTED should update connected device`() = runTest {
        val connectedDevice = testDevice
        every { mockRepository.getConnectedDevice() } returns connectedDevice
        every { mockRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)

        // Create new viewModel to trigger connection status observation
        viewModel = ConnectionViewModel(mockRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(connectedDevice, state.connectedDevice)
            assertFalse(state.isConnecting)
        }
    }

    @Test
    fun `connection status ERROR should update error state`() = runTest {
        every { mockRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.ERROR)

        // Create new viewModel to trigger connection status observation
        viewModel = ConnectionViewModel(mockRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isConnecting)
            assertTrue(state.errorMessage?.contains("Connection error occurred") == true)
        }
    }

    @Test
    fun `connection status DISCONNECTED should clear connected device`() = runTest {
        every { mockRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.DISCONNECTED)

        // Create new viewModel to trigger connection status observation
        viewModel = ConnectionViewModel(mockRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.connectedDevice)
            assertFalse(state.isConnecting)
        }
    }
}