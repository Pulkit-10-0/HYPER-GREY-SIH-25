package com.example.etongue.ui.viewmodels

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.DeviceConnectionRepository
import com.example.etongue.data.repository.SensorDataRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SensorDataViewModelTest {

    private lateinit var sensorDataRepository: SensorDataRepository
    private lateinit var deviceConnectionRepository: DeviceConnectionRepository
    private lateinit var viewModel: SensorDataViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        sensorDataRepository = mockk()
        deviceConnectionRepository = mockk()
        
        // Setup default mock behaviors
        every { deviceConnectionRepository.getConnectionStatus() } returns flowOf(ConnectionStatus.CONNECTED)
        every { sensorDataRepository.getStreamingStatus() } returns flowOf(StreamingStatus.IDLE)
        
        viewModel = SensorDataViewModel(sensorDataRepository, deviceConnectionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be correct`() {
        val uiState = viewModel.uiState.value
        val streamingStatus = viewModel.streamingStatus.value
        val connectionStatus = viewModel.connectionStatus.value
        val currentSensorData = viewModel.currentSensorData.value
        val errorMessage = viewModel.errorMessage.value

        assertFalse(uiState.isLoading)
        assertFalse(uiState.hasReceivedData)
        assertFalse(uiState.isDataTimeout)
        assertEquals(StreamingStatus.IDLE, streamingStatus)
        assertEquals(ConnectionStatus.CONNECTED, connectionStatus)
        assertNull(currentSensorData)
        assertNull(errorMessage)
    }

    @Test
    fun `startStreaming should update state correctly`() = runTest {
        val mockSensorData = createMockSensorData()
        coEvery { sensorDataRepository.startStreaming() } returns flowOf(mockSensorData)

        viewModel.startStreaming()

        val currentData = viewModel.currentSensorData.value
        val uiState = viewModel.uiState.value

        assertNotNull(currentData)
        assertEquals(mockSensorData.deviceId, currentData?.deviceId)
        assertTrue(uiState.hasReceivedData)
        assertFalse(uiState.isLoading)
    }

    @Test
    fun `stopStreaming should call repository stopStreaming`() = runTest {
        coEvery { sensorDataRepository.stopStreaming() } returns Result.success(Unit)

        viewModel.stopStreaming()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
    }

    @Test
    fun `clearError should clear error message`() {
        // Set an error message first
        viewModel.clearError()
        
        val errorMessage = viewModel.errorMessage.value
        assertNull(errorMessage)
    }

    private fun createMockSensorData(): SensorDataPacket {
        return SensorDataPacket(
            timestamp = System.currentTimeMillis(),
            deviceId = "TEST_DEVICE_001",
            sensorReadings = SensorReadings(
                ph = 7.0,
                tds = 500.0,
                uvAbsorbance = 0.5,
                temperature = 25.0,
                colorRgb = ColorData(128, 128, 128),
                moisturePercentage = 50.0
            ),
            electrodeReadings = ElectrodeReadings(
                platinum = 100.0,
                silver = 200.0,
                silverChloride = 150.0,
                stainlessSteel = 180.0,
                copper = 120.0,
                carbon = 90.0,
                zinc = 110.0
            )
        )
    }
}