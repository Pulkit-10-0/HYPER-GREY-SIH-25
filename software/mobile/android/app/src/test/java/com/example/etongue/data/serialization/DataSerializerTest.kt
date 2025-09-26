package com.example.etongue.data.serialization

import com.example.etongue.data.models.*
import kotlinx.serialization.SerializationException
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for DataSerializer class
 * Tests JSON serialization and deserialization accuracy
 */
class DataSerializerTest {
    
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
        timestamp = 1640995200000L, // 2022-01-01 00:00:00 UTC
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
        operatorNotes = "Test sample batch #123",
        calibrationData = CalibrationData(
            calibrationDate = 1640908800000L, // 2021-12-31 00:00:00 UTC
            phCalibration = SensorCalibration(offset = 0.1, scale = 1.02),
            tdsCalibration = SensorCalibration(offset = -5.0, scale = 0.98)
        )
    )
    
    private fun createTestSensorDataBatch() = SensorDataBatch(
        sessionId = "session_123",
        startTime = 1640995200000L,
        endTime = 1640995260000L, // 1 minute later
        deviceInfo = createTestESP32Device(),
        dataPoints = listOf(
            createTestSensorDataPacket(),
            createTestSensorDataPacket().copy(timestamp = 1640995210000L),
            createTestSensorDataPacket().copy(timestamp = 1640995220000L)
        ),
        metadata = createTestSessionMetadata()
    )
    
    @Test
    fun `serialize and deserialize SensorDataPacket maintains data integrity`() {
        // Given
        val originalPacket = createTestSensorDataPacket()
        
        // When
        val jsonString = DataSerializer.serializePacket(originalPacket)
        val deserializedPacket = DataSerializer.deserializePacket(jsonString)
        
        // Then
        assertEquals(originalPacket, deserializedPacket)
        assertEquals(originalPacket.timestamp, deserializedPacket.timestamp)
        assertEquals(originalPacket.deviceId, deserializedPacket.deviceId)
        assertEquals(originalPacket.sensorReadings.ph, deserializedPacket.sensorReadings.ph, 0.001)
        assertEquals(originalPacket.electrodeReadings.platinum, deserializedPacket.electrodeReadings.platinum, 0.001)
    }
    
    @Test
    fun `serialize and deserialize SensorDataBatch maintains data integrity`() {
        // Given
        val originalBatch = createTestSensorDataBatch()
        
        // When
        val jsonString = DataSerializer.serializeBatch(originalBatch)
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        
        // Then
        assertEquals(originalBatch, deserializedBatch)
        assertEquals(originalBatch.sessionId, deserializedBatch.sessionId)
        assertEquals(originalBatch.dataPoints.size, deserializedBatch.dataPoints.size)
        assertEquals(originalBatch.metadata.sampleType, deserializedBatch.metadata.sampleType)
    }
    
    @Test
    fun `serialize and deserialize packet list maintains order and data`() {
        // Given
        val originalPackets = listOf(
            createTestSensorDataPacket(),
            createTestSensorDataPacket().copy(timestamp = 1640995210000L, deviceId = "ESP32_002"),
            createTestSensorDataPacket().copy(timestamp = 1640995220000L, deviceId = "ESP32_003")
        )
        
        // When
        val jsonString = DataSerializer.serializePacketList(originalPackets)
        val deserializedPackets = DataSerializer.deserializePacketList(jsonString)
        
        // Then
        assertEquals(originalPackets.size, deserializedPackets.size)
        assertEquals(originalPackets, deserializedPackets)
        
        // Check order is maintained
        for (i in originalPackets.indices) {
            assertEquals(originalPackets[i].timestamp, deserializedPackets[i].timestamp)
            assertEquals(originalPackets[i].deviceId, deserializedPackets[i].deviceId)
        }
    }
    
    @Test
    fun `serialize ColorData with RGB values correctly`() {
        // Given
        val colorData = ColorData(255, 128, 0)
        val packet = createTestSensorDataPacket().copy(
            sensorReadings = createTestSensorReadings().copy(colorRgb = colorData)
        )
        
        // When
        val jsonString = DataSerializer.serializePacket(packet)
        val deserializedPacket = DataSerializer.deserializePacket(jsonString)
        
        // Then
        val deserializedColor = deserializedPacket.sensorReadings.colorRgb
        assertEquals(255, deserializedColor.red)
        assertEquals(128, deserializedColor.green)
        assertEquals(0, deserializedColor.blue)
        assertEquals("#FF8000", deserializedColor.hex)
    }
    
    @Test
    fun `serialize and deserialize electrode readings with negative values`() {
        // Given
        val electrodeReadings = ElectrodeReadings(
            platinum = -1.23,
            silver = -0.45,
            silverChloride = -0.78,
            stainlessSteel = -1.12,
            copper = -2.34,
            carbon = -0.56,
            zinc = -0.89
        )
        val packet = createTestSensorDataPacket().copy(electrodeReadings = electrodeReadings)
        
        // When
        val jsonString = DataSerializer.serializePacket(packet)
        val deserializedPacket = DataSerializer.deserializePacket(jsonString)
        
        // Then
        val deserializedElectrodes = deserializedPacket.electrodeReadings
        assertEquals(-1.23, deserializedElectrodes.platinum, 0.001)
        assertEquals(-0.45, deserializedElectrodes.silver, 0.001)
        assertEquals(-0.78, deserializedElectrodes.silverChloride, 0.001)
        assertEquals(-1.12, deserializedElectrodes.stainlessSteel, 0.001)
        assertEquals(-2.34, deserializedElectrodes.copper, 0.001)
        assertEquals(-0.56, deserializedElectrodes.carbon, 0.001)
        assertEquals(-0.89, deserializedElectrodes.zinc, 0.001)
    }
    
    @Test
    fun `serialize DataFile maintains all properties`() {
        // Given
        val dataFile = DataFile(
            fileName = "test_data.json",
            filePath = "/data/test_data.json",
            createdAt = 1640995200000L,
            fileSize = 1024L,
            dataPointCount = 100,
            sessionId = "session_123",
            deviceId = "ESP32_001"
        )
        
        // When
        val jsonString = DataSerializer.serializeDataFile(dataFile)
        val deserializedFile = DataSerializer.deserializeDataFile(jsonString)
        
        // Then
        assertEquals(dataFile, deserializedFile)
        assertEquals(dataFile.fileName, deserializedFile.fileName)
        assertEquals(dataFile.filePath, deserializedFile.filePath)
        assertEquals(dataFile.createdAt, deserializedFile.createdAt)
        assertEquals(dataFile.fileSize, deserializedFile.fileSize)
        assertEquals(dataFile.dataPointCount, deserializedFile.dataPointCount)
        assertEquals(dataFile.sessionId, deserializedFile.sessionId)
        assertEquals(dataFile.deviceId, deserializedFile.deviceId)
    }
    
    @Test
    fun `serialize batch with calibration data maintains precision`() {
        // Given
        val calibrationData = CalibrationData(
            calibrationDate = 1640995200000L,
            phCalibration = SensorCalibration(offset = 0.123456, scale = 1.987654),
            tdsCalibration = SensorCalibration(offset = -12.345678, scale = 0.876543),
            electrodeCalibrations = mapOf(
                "platinum" to SensorCalibration(offset = 0.001234, scale = 1.000001),
                "silver" to SensorCalibration(offset = -0.000987, scale = 0.999999)
            )
        )
        val metadata = createTestSessionMetadata().copy(calibrationData = calibrationData)
        val batch = createTestSensorDataBatch().copy(metadata = metadata)
        
        // When
        val jsonString = DataSerializer.serializeBatch(batch)
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        
        // Then
        val deserializedCalibration = deserializedBatch.metadata.calibrationData!!
        assertEquals(calibrationData.calibrationDate, deserializedCalibration.calibrationDate)
        assertEquals(0.123456, deserializedCalibration.phCalibration!!.offset, 0.000001)
        assertEquals(1.987654, deserializedCalibration.phCalibration!!.scale, 0.000001)
        assertEquals(-12.345678, deserializedCalibration.tdsCalibration!!.offset, 0.000001)
        assertEquals(0.876543, deserializedCalibration.tdsCalibration!!.scale, 0.000001)
    }
    
    @Test
    fun `isValidBatchJson returns true for valid JSON`() {
        // Given
        val batch = createTestSensorDataBatch()
        val validJson = DataSerializer.serializeBatch(batch)
        
        // When & Then
        assertTrue(DataSerializer.isValidBatchJson(validJson))
    }
    
    @Test
    fun `isValidBatchJson returns false for invalid JSON`() {
        // Given
        val invalidJson = "{ invalid json structure"
        
        // When & Then
        assertFalse(DataSerializer.isValidBatchJson(invalidJson))
    }
    
    @Test
    fun `isValidPacketJson returns true for valid JSON`() {
        // Given
        val packet = createTestSensorDataPacket()
        val validJson = DataSerializer.serializePacket(packet)
        
        // When & Then
        assertTrue(DataSerializer.isValidPacketJson(validJson))
    }
    
    @Test
    fun `isValidPacketJson returns false for invalid JSON`() {
        // Given
        val invalidJson = "{ \"timestamp\": \"not_a_number\" }"
        
        // When & Then
        assertFalse(DataSerializer.isValidPacketJson(invalidJson))
    }
    
    @Test
    fun `deserialize throws SerializationException for malformed JSON`() {
        // Given
        val malformedJson = "{ \"timestamp\": }"
        
        // When & Then
        assertThrows(SerializationException::class.java) {
            DataSerializer.deserializePacket(malformedJson)
        }
    }
    
    @Test
    fun `serialize empty packet list returns valid JSON array`() {
        // Given
        val emptyList = emptyList<SensorDataPacket>()
        
        // When
        val jsonString = DataSerializer.serializePacketList(emptyList)
        val deserializedList = DataSerializer.deserializePacketList(jsonString)
        
        // Then
        assertTrue(deserializedList.isEmpty())
        assertEquals(emptyList, deserializedList)
    }
    
    @Test
    fun `serialize batch with null optional fields handles gracefully`() {
        // Given
        val metadata = SessionMetadata(
            sampleType = null,
            testConditions = null,
            operatorNotes = null,
            calibrationData = null
        )
        val batch = createTestSensorDataBatch().copy(metadata = metadata)
        
        // When
        val jsonString = DataSerializer.serializeBatch(batch)
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        
        // Then
        assertEquals(batch, deserializedBatch)
        assertNull(deserializedBatch.metadata.sampleType)
        assertNull(deserializedBatch.metadata.testConditions)
        assertNull(deserializedBatch.metadata.operatorNotes)
        assertNull(deserializedBatch.metadata.calibrationData)
    }
    
    @Test
    fun `JSON output is human readable when pretty print enabled`() {
        // Given
        val packet = createTestSensorDataPacket()
        
        // When
        val jsonString = DataSerializer.serializePacket(packet)
        
        // Then
        assertTrue("JSON should contain newlines for readability", jsonString.contains("\n"))
        assertTrue("JSON should contain proper indentation", jsonString.contains("  "))
        assertTrue("JSON should be properly formatted", jsonString.trim().startsWith("{"))
        assertTrue("JSON should be properly formatted", jsonString.trim().endsWith("}"))
    }
}