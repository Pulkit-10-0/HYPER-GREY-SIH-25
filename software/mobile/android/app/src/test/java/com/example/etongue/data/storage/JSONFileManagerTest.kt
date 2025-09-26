package com.example.etongue.data.storage

import android.content.Context
import com.example.etongue.data.models.*
import com.example.etongue.data.serialization.DataSerializer
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/**
 * Unit tests for JSONFileManager class
 * Tests file operations for sensor data persistence
 */
class JSONFileManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var fileManager: JSONFileManager
    
    @Before
    fun setup() {
        mockContext = mockk()
        fileManager = JSONFileManager(mockContext)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    private fun createTestColorData() = ColorData(255, 128, 64)
    
    private fun createTestSensorReadings() = SensorReadings(
        ph = 7.2,
        tds = 450.0,
        uvAbsorbance = 0.85,
        temperature = 23.5,
        colorRgb = createTestColorData(),
        moisturePercentage = 65.3
    )
    
    private fun createTestElectrodeReadings() = ElectrodeReadings(
        platinum = 1.23,
        silver = -0.45,
        silverChloride = 0.78,
        stainlessSteel = -1.12,
        copper = 2.34,
        carbon = 0.56,
        zinc = -0.89
    )
    
    private fun createTestSensorDataPacket() = SensorDataPacket(
        timestamp = 1640995200000L,
        deviceId = "ESP32_001",
        sensorReadings = createTestSensorReadings(),
        electrodeReadings = createTestElectrodeReadings()
    )
    
    private fun createTestESP32Device() = ESP32Device(
        id = "ESP32_001",
        name = "E-Tongue Sensor",
        macAddress = "AA:BB:CC:DD:EE:FF",
        signalStrength = -45,
        connectionType = ConnectionType.BLUETOOTH_LE
    )
    
    private fun createTestSessionMetadata() = SessionMetadata(
        sampleType = "Herbal Tea",
        testConditions = "Room temperature, 25°C",
        operatorNotes = "Test sample batch #123"
    )
    
    private fun createTestSensorDataBatch() = SensorDataBatch(
        sessionId = "session_123",
        startTime = 1640995200000L,
        endTime = 1640995260000L,
        deviceInfo = createTestESP32Device(),
        dataPoints = listOf(createTestSensorDataPacket()),
        metadata = createTestSessionMetadata()
    )
    
    @Test
    fun `test data models validation works correctly`() {
        // Test SensorDataBatch validation
        val validBatch = createTestSensorDataBatch()
        assertTrue("Valid batch should pass validation", validBatch.isValid())
        
        val invalidBatch = validBatch.copy(sessionId = "")
        assertFalse("Invalid batch should fail validation", invalidBatch.isValid())
        
        // Test SessionMetadata validation
        val validMetadata = createTestSessionMetadata()
        assertTrue("Valid metadata should pass validation", validMetadata.isValid())
        
        // Test CalibrationData validation
        val validCalibration = CalibrationData(calibrationDate = System.currentTimeMillis())
        assertTrue("Valid calibration should pass validation", validCalibration.isValid())
        
        val invalidCalibration = CalibrationData(calibrationDate = 0)
        assertFalse("Invalid calibration should fail validation", invalidCalibration.isValid())
    }
    
    @Test
    fun `test sensor calibration applies correctly`() {
        // Given
        val calibration = SensorCalibration(offset = 0.5, scale = 1.2)
        val rawValue = 10.0
        
        // When
        val calibratedValue = calibration.calibrate(rawValue)
        
        // Then
        assertEquals(12.6, calibratedValue, 0.001) // (10.0 + 0.5) * 1.2 = 12.6
    }
    
    @Test
    fun `test data file formatted size calculation`() {
        // Test bytes
        val smallFile = DataFile("test.json", "/path", 0L, 512L, 10)
        assertEquals("512 B", smallFile.getFormattedFileSize())
        
        // Test KB
        val mediumFile = DataFile("test.json", "/path", 0L, 2048L, 10)
        assertEquals("2 KB", mediumFile.getFormattedFileSize())
        
        // Test MB
        val largeFile = DataFile("test.json", "/path", 0L, 2097152L, 10) // 2MB
        assertEquals("2 MB", largeFile.getFormattedFileSize())
    }
    
    @Test
    fun `test sensor data batch calculations`() {
        // Given
        val batch = createTestSensorDataBatch()
        
        // When & Then
        assertEquals(60000L, batch.duration) // 1 minute in milliseconds
        assertEquals(1, batch.dataPointCount)
        
        // Test sampling rate calculation
        val batchWithMultiplePoints = batch.copy(
            dataPoints = listOf(
                createTestSensorDataPacket().copy(timestamp = 1640995200000L),
                createTestSensorDataPacket().copy(timestamp = 1640995210000L), // +10 seconds
                createTestSensorDataPacket().copy(timestamp = 1640995220000L)  // +20 seconds
            ),
            endTime = 1640995220000L // 20 seconds total
        )
        
        assertEquals(0.1, batchWithMultiplePoints.getAverageSamplingRate(), 0.001) // 2 intervals / 20 seconds = 0.1 Hz
    }
}