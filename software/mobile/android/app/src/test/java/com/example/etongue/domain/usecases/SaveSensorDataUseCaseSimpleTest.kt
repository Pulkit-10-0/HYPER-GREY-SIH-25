package com.example.etongue.domain.usecases

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SaveSensorDataUseCaseSimpleTest {
    
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
        val mockDataPoints = listOf(createMockSensorDataPacket())
        coEvery { mockRepository.getBufferedData() } returns mockDataPoints
        coEvery { mockRepository.saveData(any()) } returns Result.success("test_file.json")
        coEvery { mockRepository.clearBuffer() } just Runs
        
        // Act
        val result = saveUseCase.saveCurrentSession(mockDeviceInfo)
        
        // Assert
        assertTrue("Save should be successful", result.isSuccess)
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
        assertTrue("Save should fail", result.isFailure)
        assertEquals("No data to save", result.exceptionOrNull()?.message)
        
        coVerify { mockRepository.getBufferedData() }
        coVerify(exactly = 0) { mockRepository.saveData(any()) }
        coVerify(exactly = 0) { mockRepository.clearBuffer() }
    }
    
    @Test
    fun `generateBatchId should create unique identifiers`() {
        // Act
        val id1 = saveUseCase.generateBatchId()
        val id2 = saveUseCase.generateBatchId()
        
        // Assert
        assertTrue("ID should start with batch_", id1.startsWith("batch_"))
        assertTrue("ID should start with batch_", id2.startsWith("batch_"))
        assertNotEquals("IDs should be unique", id1, id2)
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
        assertTrue("Metadata should be valid", metadata.isValid())
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