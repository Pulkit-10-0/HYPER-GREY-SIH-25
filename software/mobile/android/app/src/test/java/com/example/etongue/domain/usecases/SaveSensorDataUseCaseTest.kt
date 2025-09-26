package com.example.etongue.domain.usecases

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SaveSensorDataUseCaseTest {
    
    private lateinit var mockRepository: SensorDataRepository
    private lateinit var saveUseCase: SaveSensorDataUseCase
    
    private val mockDeviceInfo = ESP32Device(
        id = "test_device_001",
        name = "Test ESP32",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -50,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    @Before
    fun setup() {
        mockRepository = mockk()
        saveUseCase = SaveSensorDataUseCase(mockRepository)
    }
    
    @Test
    fun `saveCurrentSession should save buffered data successfully`() = runTest {
        // Arrange
        val mockDataPoints = createMockSensorDataPoints(5)
        coEvery { mockRepository.getBufferedData() } returns mockDataPoints
        coEvery { mockRepository.saveData(any()) } returns Result.success("test_file.json")
        coEvery { mockRepository.clearBuffer() } just Runs
        
        val metadata = SessionMetadata(
            sampleType = "Test Sample",
            testConditions = "Room temperature"
        )
        
        // Act
        val result = saveUseCase.saveCurrentSession(mockDeviceInfo, metadata)
        
        // Assert
        assertTrue(result.isSuccess)
        assertEquals("test_file.json", result.getOrNull())
        
        coVerify { mockRepository.getBufferedData() }
        coVerify { mockRepository.saveData(any()) }
        coVerify { mockRepository.clearBuffer() }
    }
    
    @Test
    fun `saveCurrentSession should fail when no data is available`() = runTest {
        // Arrange
        coEvery { mockRepository.getBufferedData() } returns emptyList()
        
        // Act
        val result = saveUseCase.saveCurrentSession(mockDeviceInfo)
        
        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("No data to save", result.exceptionOrNull()?.message)
        
        coVerify { mockRepository.getBufferedData() }
        coVerify(exactly = 0) { mockRepository.saveData(any()) }
        coVerify(exactly = 0) { mockRepository.clearBuffer() }
    }
    
    @Test
    fun `saveCurrentSession should not clear buffer when save fails`() = runTest {
        // Arrange
        val mockDataPoints = createMockSensorDataPoints(3)
        coEvery { mockRepository.getBufferedData() } returns mockDataPoints
        coEvery { mockRepository.saveData(any()) } returns Result.failure(Exception("Save failed"))
        
        // Act
        val result = saveUseCase.saveCurrentSession(mockDeviceInfo)
        
        // Assert
        assertTrue(result.isFailure)
        assertEquals("Save failed", result.exceptionOrNull()?.message)
        
        coVerify { mockRepository.getBufferedData() }
        coVerify { mockRepository.saveData(any()) }
        coVerify(exactly = 0) { mockRepository.clearBuffer() }
    }
    
    @Test
    fun `saveDataPoints should save provided data successfully`() = runTest {
        // Arrange
        val dataPoints = createMockSensorDataPoints(10)
        coEvery { mockRepository.saveData(any()) } returns Result.success("custom_file.json")
        
        val metadata = SessionMetadata(
            sampleType = "Custom Sample",
            operatorNotes = "Test notes"
        )
        
        // Act
        val result = saveUseCase.saveDataPoints(dataPoints, mockDeviceInfo, metadata)
        
        // Assert
        assertTrue(result.isSuccess)
        assertEquals("custom_file.json", result.getOrNull())
        
        coVerify { mockRepository.saveData(any()) }
        coVerify(exactly = 0) { mockRepository.getBufferedData() }
        coVerify(exactly = 0) { mockRepository.clearBuffer() }
    }
    
    @Test
    fun `saveDataPoints should fail when data points list is empty`() = runTest {
        // Arrange
        val emptyDataPoints = emptyList<SensorDataPacket>()
        
        // Act
        val result = saveUseCase.saveDataPoints(emptyDataPoints, mockDeviceInfo)
        
        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("No data points to save", result.exceptionOrNull()?.message)
        
        coVerify(exactly = 0) { mockRepository.saveData(any()) }
    }
    
    @Test
    fun `createSensorDataBatch should create valid batch with sorted data points`() = runTest {
        // Arrange
        val dataPoints = listOf(
            createMockSensorDataPacket(timestamp = 1000L),
            createMockSensorDataPacket(timestamp = 500L),
            createMockSensorDataPacket(timestamp = 1500L)
        )
        
        coEvery { mockRepository.saveData(any()) } answers {
            val batch = firstArg<SensorDataBatch>()
            
            // Verify batch properties
            assertTrue(batch.sessionId.startsWith("session_"))
            assertEquals(500L, batch.startTime)
            assertEquals(1500L, batch.endTime)
            assertEquals(mockDeviceInfo, batch.deviceInfo)
            assertEquals(3, batch.dataPoints.size)
            
            // Verify data points are sorted by timestamp
            assertEquals(500L, batch.dataPoints[0].timestamp)
            assertEquals(1000L, batch.dataPoints[1].timestamp)
            assertEquals(1500L, batch.dataPoints[2].timestamp)
            
            // Verify metadata
            assertEquals("1.0", batch.metadata.appVersion)
            assertEquals("1.0", batch.metadata.dataFormatVersion)
            
            Result.success("test_file.json")
        }
        
        // Act
        val result = saveUseCase.saveDataPoints(dataPoints, mockDeviceInfo)
        
        // Assert
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `generateSessionId should create unique identifiers`() {
        // Act
        val id1 = saveUseCase.generateBatchId()
        val id2 = saveUseCase.generateBatchId()
        
        // Assert
        assertTrue(id1.startsWith("batch_"))
        assertTrue(id2.startsWith("batch_"))
        assertTrue(id1 != id2) // Should be unique
    }
    
    @Test
    fun `createSessionMetadata should create valid metadata`() {
        // Act
        val metadata = saveUseCase.createSessionMetadata(
            sampleType = "Herbal Tea",
            testConditions = "25°C, 60% humidity",
            operatorNotes = "First test run"
        )
        
        // Assert
        assertEquals("Herbal Tea", metadata.sampleType)
        assertEquals("25°C, 60% humidity", metadata.testConditions)
        assertEquals("First test run", metadata.operatorNotes)
        assertEquals("1.0", metadata.appVersion)
        assertEquals("1.0", metadata.dataFormatVersion)
        assertTrue(metadata.isValid())
    }
    
    @Test
    fun `saveCurrentSession should handle repository exceptions`() = runTest {
        // Arrange
        coEvery { mockRepository.getBufferedData() } throws RuntimeException("Repository error")
        
        // Act
        val result = saveUseCase.saveCurrentSession(mockDeviceInfo)
        
        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Repository error", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `saveDataPoints should handle repository exceptions`() = runTest {
        // Arrange
        val dataPoints = createMockSensorDataPoints(2)
        coEvery { mockRepository.saveData(any()) } throws RuntimeException("Save error")
        
        // Act
        val result = saveUseCase.saveDataPoints(dataPoints, mockDeviceInfo)
        
        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Save error", result.exceptionOrNull()?.message)
    }
    
    private fun createMockSensorDataPoints(count: Int): List<SensorDataPacket> {
        return (1..count).map { index ->
            createMockSensorDataPacket(timestamp = System.currentTimeMillis() + index * 1000L)
        }
    }
    
    private fun createMockSensorDataPacket(timestamp: Long = System.currentTimeMillis()): SensorDataPacket {
        return SensorDataPacket(
            timestamp = timestamp,
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
    }
}