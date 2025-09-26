package com.example.etongue.ui.viewmodels

import com.example.etongue.data.models.*
import com.example.etongue.domain.usecases.LoadHistoricalDataUseCase
import com.example.etongue.domain.usecases.DataSummary
import com.example.etongue.domain.errors.AppError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    
    private lateinit var loadHistoricalDataUseCase: LoadHistoricalDataUseCase
    private lateinit var viewModel: AnalysisViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        loadHistoricalDataUseCase = mockk()
        viewModel = AnalysisViewModel(loadHistoricalDataUseCase)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial state is correct`() {
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingData)
        assertFalse(state.hasData)
        assertFalse(state.hasFiles)
        assertNull(state.error)
        assertNull(state.selectedData)
        assertNull(state.selectedFileName)
        assertTrue(state.availableFiles.isEmpty())
    }
    
    @Test
    fun `loadAvailableFiles updates state correctly on success`() = runTest {
        // Given
        val files = listOf(createDataFile("test1.json"), createDataFile("test2.json"))
        coEvery { loadHistoricalDataUseCase.loadAvailableFiles() } returns Result.success(files)
        
        // When
        viewModel.loadAvailableFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasFiles)
        assertEquals(2, state.availableFiles.size)
        assertNull(state.error)
    }
    
    @Test
    fun `loadAvailableFiles updates state correctly on failure`() = runTest {
        // Given
        val error = AppError.StorageError("Failed to load files")
        coEvery { loadHistoricalDataUseCase.loadAvailableFiles() } returns Result.failure(error)
        
        // When
        viewModel.loadAvailableFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasFiles)
        assertEquals("Storage operation failed: Failed to load files - Unknown error", state.error)
    }
    
    @Test
    fun `loadDataFile updates state correctly on success`() = runTest {
        // Given
        val fileName = "test.json"
        val batch = createValidSensorDataBatch()
        val summary = DataSummary(
            totalDataPoints = 1,
            duration = 1000L,
            averageSamplingRate = 1.0,
            startTime = 1000L,
            endTime = 2000L,
            deviceId = "device_001",
            sessionId = "session_001"
        )
        coEvery { loadHistoricalDataUseCase.loadDataFile(fileName) } returns Result.success(batch)
        coEvery { loadHistoricalDataUseCase.getDataSummary(batch) } returns summary
        
        // When
        viewModel.loadDataFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingData)
        assertTrue(state.hasData)
        assertEquals(fileName, state.selectedFileName)
        assertEquals(batch, state.selectedData)
        assertEquals(summary, state.dataSummary)
        assertNull(state.error)
    }
    
    @Test
    fun `loadDataFile updates state correctly on failure`() = runTest {
        // Given
        val fileName = "test.json"
        val error = AppError.StorageError("File not found")
        coEvery { loadHistoricalDataUseCase.loadDataFile(fileName) } returns Result.failure(error)
        
        // When
        viewModel.loadDataFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingData)
        assertFalse(state.hasData)
        assertEquals("Storage operation failed: File not found - Unknown error", state.error)
    }
    
    @Test
    fun `clearSelectedData resets data state`() {
        // Given - set some data first
        viewModel.loadDataFile("test.json")
        
        // When
        viewModel.clearSelectedData()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.hasData)
        assertNull(state.selectedData)
        assertNull(state.dataSummary)
        assertNull(state.selectedFileName)
        assertNull(state.error)
    }
    
    @Test
    fun `clearError removes error message`() = runTest {
        // Given - set an error first
        val error = AppError.StorageError("Test error")
        coEvery { loadHistoricalDataUseCase.loadAvailableFiles() } returns Result.failure(error)
        viewModel.loadAvailableFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.clearError()
        
        // Then
        val state = viewModel.uiState.value
        assertNull(state.error)
    }
    
    @Test
    fun `refreshFiles calls loadAvailableFiles`() = runTest {
        // Given
        val files = listOf(createDataFile("test.json"))
        coEvery { loadHistoricalDataUseCase.loadAvailableFiles() } returns Result.success(files)
        
        // When
        viewModel.refreshFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.hasFiles)
        assertEquals(1, state.availableFiles.size)
    }
    
    @Test
    fun `loading states are set correctly during operations`() = runTest {
        // Given
        coEvery { loadHistoricalDataUseCase.loadAvailableFiles() } returns Result.success(emptyList())
        
        // When
        viewModel.loadAvailableFiles()
        
        // Then - advance coroutines and check final state
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }
    
    private fun createDataFile(fileName: String): DataFile {
        return DataFile(
            fileName = fileName,
            filePath = "/data/$fileName",
            createdAt = System.currentTimeMillis(),
            fileSize = 1024L,
            dataPointCount = 10,
            sessionId = "session_001",
            deviceId = "device_001"
        )
    }
    
    private fun createValidSensorDataBatch(): SensorDataBatch {
        val device = ESP32Device(
            id = "device_001",
            name = "Test Device",
            macAddress = "AA:BB:CC:DD:EE:FF",
            signalStrength = -50,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
        
        val sensorReadings = SensorReadings(
            ph = 7.0,
            tds = 500.0,
            uvAbsorbance = 0.5,
            temperature = 25.0,
            colorRgb = ColorData(255, 128, 64),
            moisturePercentage = 60.0
        )
        
        val electrodeReadings = ElectrodeReadings(
            platinum = 1.0,
            silver = 2.0,
            silverChloride = 3.0,
            stainlessSteel = 4.0,
            copper = 5.0,
            carbon = 6.0,
            zinc = 7.0
        )
        
        val dataPoint = SensorDataPacket(
            timestamp = System.currentTimeMillis(),
            deviceId = "device_001",
            sensorReadings = sensorReadings,
            electrodeReadings = electrodeReadings
        )
        
        val metadata = SessionMetadata(
            sampleType = "Test Sample",
            testConditions = "Room temperature",
            operatorNotes = "Test notes"
        )
        
        return SensorDataBatch(
            sessionId = "session_001",
            startTime = 1000L,
            endTime = 2000L,
            deviceInfo = device,
            dataPoints = listOf(dataPoint),
            metadata = metadata
        )
    }
}