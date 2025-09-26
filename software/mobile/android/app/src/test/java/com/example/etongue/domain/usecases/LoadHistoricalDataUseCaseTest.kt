package com.example.etongue.domain.usecases

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import com.example.etongue.domain.errors.AppError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LoadHistoricalDataUseCaseTest {
    
    private lateinit var sensorDataRepository: SensorDataRepository
    private lateinit var loadHistoricalDataUseCase: LoadHistoricalDataUseCase
    
    @Before
    fun setup() {
        sensorDataRepository = mockk()
        loadHistoricalDataUseCase = LoadHistoricalDataUseCase(sensorDataRepository)
    }
    
    @Test
    fun `loadAvailableFiles returns sorted files on success`() = runTest {
        // Given
        val files = listOf(
            createDataFile("file1.json", 1000L),
            createDataFile("file2.json", 2000L),
            createDataFile("file3.json", 1500L)
        )
        coEvery { sensorDataRepository.loadSavedFiles() } returns files
        
        // When
        val result = loadHistoricalDataUseCase.loadAvailableFiles()
        
        // Then
        assertTrue(result.isSuccess)
        val sortedFiles = result.getOrNull()!!
        assertEquals(3, sortedFiles.size)
        // Should be sorted by creation time descending
        assertEquals("file2.json", sortedFiles[0].fileName)
        assertEquals("file3.json", sortedFiles[1].fileName)
        assertEquals("file1.json", sortedFiles[2].fileName)
    }
    
    @Test
    fun `loadAvailableFiles returns error on repository failure`() = runTest {
        // Given
        coEvery { sensorDataRepository.loadSavedFiles() } throws RuntimeException("Storage error")
        
        // When
        val result = loadHistoricalDataUseCase.loadAvailableFiles()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.StorageError)
    }
    
    @Test
    fun `loadDataFile returns data on success`() = runTest {
        // Given
        val fileName = "test.json"
        val batch = createValidSensorDataBatch()
        coEvery { sensorDataRepository.loadDataFile(fileName) } returns Result.success(batch)
        
        // When
        val result = loadHistoricalDataUseCase.loadDataFile(fileName)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(batch, result.getOrNull())
    }
    
    @Test
    fun `loadDataFile returns error for empty filename`() = runTest {
        // When
        val result = loadHistoricalDataUseCase.loadDataFile("")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.ValidationError)
    }
    
    @Test
    fun `loadDataFile returns error for invalid data batch`() = runTest {
        // Given
        val fileName = "test.json"
        val invalidBatch = createInvalidSensorDataBatch()
        coEvery { sensorDataRepository.loadDataFile(fileName) } returns Result.success(invalidBatch)
        
        // When
        val result = loadHistoricalDataUseCase.loadDataFile(fileName)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.DataValidationError)
    }
    
    @Test
    fun `loadDataFile returns error on repository failure`() = runTest {
        // Given
        val fileName = "test.json"
        val error = RuntimeException("File not found")
        coEvery { sensorDataRepository.loadDataFile(fileName) } returns Result.failure(error)
        
        // When
        val result = loadHistoricalDataUseCase.loadDataFile(fileName)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.StorageError)
    }
    
    @Test
    fun `validateDataForAnalysis returns true for valid batch`() {
        // Given
        val batch = createValidSensorDataBatch()
        
        // When
        val isValid = loadHistoricalDataUseCase.validateDataForAnalysis(batch)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `validateDataForAnalysis returns false for invalid batch`() {
        // Given
        val batch = createInvalidSensorDataBatch()
        
        // When
        val isValid = loadHistoricalDataUseCase.validateDataForAnalysis(batch)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `getDataSummary returns correct summary`() {
        // Given
        val batch = createValidSensorDataBatch()
        
        // When
        val summary = loadHistoricalDataUseCase.getDataSummary(batch)
        
        // Then
        assertEquals(batch.sessionId, summary.sessionId)
        assertEquals(batch.deviceInfo.id, summary.deviceId)
        assertEquals(batch.startTime, summary.startTime)
        assertEquals(batch.endTime, summary.endTime)
        assertEquals(batch.duration, summary.duration)
        assertEquals(batch.dataPoints.size, summary.totalDataPoints)
        assertEquals(batch.getAverageSamplingRate(), summary.averageSamplingRate, 0.01)
    }
    
    private fun createDataFile(fileName: String, createdAt: Long): DataFile {
        return DataFile(
            fileName = fileName,
            filePath = "/data/$fileName",
            createdAt = createdAt,
            fileSize = 1024L,
            dataPointCount = 10,
            sessionId = "session_$fileName",
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
    
    private fun createInvalidSensorDataBatch(): SensorDataBatch {
        return SensorDataBatch(
            sessionId = "", // Invalid empty session ID
            startTime = 0L,
            endTime = 0L,
            deviceInfo = ESP32Device("", "", "", 0, ConnectionType.BLUETOOTH_LE),
            dataPoints = emptyList(), // Invalid empty data points
            metadata = SessionMetadata()
        )
    }
}