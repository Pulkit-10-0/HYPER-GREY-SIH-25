package com.example.etongue.ui.viewmodels

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import com.example.etongue.domain.usecases.SaveSensorDataUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DataManagementViewModelTest {
    
    private lateinit var mockRepository: SensorDataRepository
    private lateinit var mockSaveUseCase: SaveSensorDataUseCase
    private lateinit var viewModel: DataManagementViewModel
    
    private val testDispatcher = StandardTestDispatcher()
    
    private val mockDeviceInfo = ESP32Device(
        id = "test_device_001",
        name = "Test ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockRepository = mockk()
        mockSaveUseCase = mockk()
        
        // Default mock behaviors
        coEvery { mockRepository.loadSavedFiles() } returns emptyList()
        
        viewModel = DataManagementViewModel(mockRepository, mockSaveUseCase)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial state should be correct`() = runTest {
        // Assert
        val uiState = viewModel.uiState.first()
        val files = viewModel.files.first()
        val storageInfo = viewModel.storageInfo.first()
        
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        assertNull(uiState.successMessage)
        assertNull(uiState.loadedData)
        
        assertTrue(files.isEmpty())
        
        assertEquals(0, storageInfo.totalFiles)
        assertEquals(0L, storageInfo.totalSize)
        assertEquals("0 B", storageInfo.formattedSize)
    }
    
    @Test
    fun `saveCurrentSession should save data successfully`() = runTest {
        // Arrange
        coEvery { mockSaveUseCase.saveCurrentSession(any(), any()) } returns Result.success("test_file.json")
        coEvery { mockRepository.loadSavedFiles() } returns listOf(createMockDataFile())
        
        // Act
        viewModel.saveCurrentSession(
            deviceInfo = mockDeviceInfo,
            sampleType = "Test Sample",
            testConditions = "Room temperature",
            operatorNotes = "Test notes"
        )
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        assertEquals("Data saved successfully: test_file.json", uiState.successMessage)
        
        coVerify { mockSaveUseCase.saveCurrentSession(mockDeviceInfo, any()) }
        coVerify { mockRepository.loadSavedFiles() }
    }
    
    @Test
    fun `saveCurrentSession should handle save failure`() = runTest {
        // Arrange
        coEvery { mockSaveUseCase.saveCurrentSession(any(), any()) } returns Result.failure(Exception("Save failed"))
        
        // Act
        viewModel.saveCurrentSession(mockDeviceInfo)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals("Save failed", uiState.error)
        assertNull(uiState.successMessage)
        
        coVerify { mockSaveUseCase.saveCurrentSession(mockDeviceInfo, any()) }
        coVerify(exactly = 0) { mockRepository.loadSavedFiles() }
    }
    
    @Test
    fun `loadFiles should update files list`() = runTest {
        // Arrange
        val mockFiles = listOf(
            createMockDataFile("file1.json", 1000L),
            createMockDataFile("file2.json", 2000L)
        )
        coEvery { mockRepository.loadSavedFiles() } returns mockFiles
        
        // Act
        viewModel.loadFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val files = viewModel.files.value
        val uiState = viewModel.uiState.value
        
        assertEquals(2, files.size)
        assertEquals("file2.json", files[0].fileName) // Should be sorted by creation time desc
        assertEquals("file1.json", files[1].fileName)
        
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        
        coVerify { mockRepository.loadSavedFiles() }
    }
    
    @Test
    fun `loadFiles should handle repository error`() = runTest {
        // Arrange
        coEvery { mockRepository.loadSavedFiles() } throws RuntimeException("Load error")
        
        // Act
        viewModel.loadFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals("Load error", uiState.error)
        
        val files = viewModel.files.value
        assertTrue(files.isEmpty())
    }
    
    @Test
    fun `deleteFile should remove file successfully`() = runTest {
        // Arrange
        val fileName = "test_file.json"
        coEvery { mockRepository.deleteFile(fileName) } returns Result.success(Unit)
        coEvery { mockRepository.loadSavedFiles() } returns emptyList()
        
        // Act
        viewModel.deleteFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        assertEquals("File deleted successfully", uiState.successMessage)
        
        coVerify { mockRepository.deleteFile(fileName) }
        coVerify { mockRepository.loadSavedFiles() }
    }
    
    @Test
    fun `deleteFile should handle deletion failure`() = runTest {
        // Arrange
        val fileName = "test_file.json"
        coEvery { mockRepository.deleteFile(fileName) } returns Result.failure(Exception("Delete failed"))
        
        // Act
        viewModel.deleteFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals("Delete failed", uiState.error)
        assertNull(uiState.successMessage)
        
        coVerify { mockRepository.deleteFile(fileName) }
        coVerify(exactly = 0) { mockRepository.loadSavedFiles() }
    }
    
    @Test
    fun `deleteFile should clear selected file if it matches deleted file`() = runTest {
        // Arrange
        val fileName = "test_file.json"
        val selectedFile = createMockDataFile(fileName)
        
        coEvery { mockRepository.deleteFile(fileName) } returns Result.success(Unit)
        coEvery { mockRepository.loadSavedFiles() } returns emptyList()
        
        viewModel.selectFile(selectedFile)
        
        // Act
        viewModel.deleteFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        assertNull(viewModel.selectedFile.value)
    }
    
    @Test
    fun `loadDataFile should load file successfully`() = runTest {
        // Arrange
        val fileName = "test_file.json"
        val mockBatch = createMockSensorDataBatch()
        coEvery { mockRepository.loadDataFile(fileName) } returns Result.success(mockBatch)
        
        // Act
        viewModel.loadDataFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        assertEquals(mockBatch, uiState.loadedData)
        
        coVerify { mockRepository.loadDataFile(fileName) }
    }
    
    @Test
    fun `loadDataFile should handle load failure`() = runTest {
        // Arrange
        val fileName = "test_file.json"
        coEvery { mockRepository.loadDataFile(fileName) } returns Result.failure(Exception("Load failed"))
        
        // Act
        viewModel.loadDataFile(fileName)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals("Load failed", uiState.error)
        assertNull(uiState.loadedData)
    }
    
    @Test
    fun `selectFile should update selected file`() {
        // Arrange
        val file = createMockDataFile()
        
        // Act
        viewModel.selectFile(file)
        
        // Assert
        assertEquals(file, viewModel.selectedFile.value)
    }
    
    @Test
    fun `clearSelection should clear selected file`() {
        // Arrange
        val file = createMockDataFile()
        viewModel.selectFile(file)
        
        // Act
        viewModel.clearSelection()
        
        // Assert
        assertNull(viewModel.selectedFile.value)
    }
    
    @Test
    fun `clearMessages should clear error and success messages`() = runTest {
        // Arrange - trigger an error first
        coEvery { mockRepository.deleteFile(any()) } returns Result.failure(Exception("Test error"))
        viewModel.deleteFile("test.json")
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify error is set
        assertEquals("Test error", viewModel.uiState.value.error)
        
        // Act
        viewModel.clearMessages()
        
        // Assert
        val uiState = viewModel.uiState.value
        assertNull(uiState.error)
        assertNull(uiState.successMessage)
    }
    
    @Test
    fun `formatTimestamp should format timestamp correctly`() {
        // Arrange
        val timestamp = 1640995200000L // 2022-01-01 00:00:00 UTC
        
        // Act
        val formatted = viewModel.formatTimestamp(timestamp)
        
        // Assert
        assertTrue(formatted.contains("2022") || formatted.contains("2021")) // Account for timezone differences
    }
    
    @Test
    fun `generateFileName should create valid filename`() {
        // Act
        val fileName = viewModel.generateFileName("test_data")
        
        // Assert
        assertTrue(fileName.startsWith("test_data_"))
        assertTrue(fileName.endsWith(".json"))
        assertTrue(fileName.contains("_")) // Should contain timestamp separator
    }
    
    private fun createMockDataFile(
        fileName: String = "test_file.json",
        createdAt: Long = System.currentTimeMillis()
    ): DataFile {
        return DataFile(
            fileName = fileName,
            filePath = "/data/files/$fileName",
            createdAt = createdAt,
            fileSize = 1024L,
            dataPointCount = 10
        )
    }
    
    private fun createMockSensorDataBatch(): SensorDataBatch {
        return SensorDataBatch(
            sessionId = "test_session_001",
            startTime = System.currentTimeMillis() - 60000,
            endTime = System.currentTimeMillis(),
            deviceInfo = mockDeviceInfo,
            dataPoints = listOf(
                SensorDataPacket(
                    timestamp = System.currentTimeMillis(),
                    deviceId = "test_device_001",
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
            ),
            metadata = SessionMetadata()
        )
    }
}