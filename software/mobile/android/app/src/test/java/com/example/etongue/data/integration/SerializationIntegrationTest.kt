package com.example.etongue.data.integration

import com.example.etongue.data.models.*
import com.example.etongue.data.serialization.DataSerializer
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for the complete serialization flow
 * Tests end-to-end JSON serialization and deserialization
 */
class SerializationIntegrationTest {
    
    private fun createCompleteTestBatch(): SensorDataBatch {
        val colorData = ColorData(255, 128, 64)
        
        val sensorReadings = SensorReadings(
            ph = 7.2,
            tds = 450.0,
            uvAbsorbance = 0.85,
            temperature = 23.5,
            colorRgb = colorData,
            moisturePercentage = 65.3
        )
        
        val electrodeReadings = ElectrodeReadings(
            platinum = 1.23,
            silver = -0.45,
            silverChloride = 0.78,
            stainlessSteel = -1.12,
            copper = 2.34,
            carbon = 0.56,
            zinc = -0.89
        )
        
        val dataPackets = listOf(
            SensorDataPacket(
                timestamp = 1640995200000L,
                deviceId = "ESP32_001",
                sensorReadings = sensorReadings,
                electrodeReadings = electrodeReadings
            ),
            SensorDataPacket(
                timestamp = 1640995210000L,
                deviceId = "ESP32_001",
                sensorReadings = sensorReadings.copy(ph = 7.3, temperature = 23.6),
                electrodeReadings = electrodeReadings.copy(platinum = 1.25, silver = -0.43)
            ),
            SensorDataPacket(
                timestamp = 1640995220000L,
                deviceId = "ESP32_001",
                sensorReadings = sensorReadings.copy(ph = 7.1, temperature = 23.4),
                electrodeReadings = electrodeReadings.copy(platinum = 1.21, silver = -0.47)
            )
        )
        
        val calibrationData = CalibrationData(
            calibrationDate = 1640908800000L,
            phCalibration = SensorCalibration(offset = 0.1, scale = 1.02),
            tdsCalibration = SensorCalibration(offset = -5.0, scale = 0.98),
            electrodeCalibrations = mapOf(
                "platinum" to SensorCalibration(offset = 0.01, scale = 1.001),
                "silver" to SensorCalibration(offset = -0.02, scale = 0.999)
            )
        )
        
        val metadata = SessionMetadata(
            sampleType = "Herbal Tea Sample",
            testConditions = "Room temperature, 25°C, 60% humidity",
            operatorNotes = "Test sample batch #123 - Quality control measurement",
            calibrationData = calibrationData
        )
        
        val device = ESP32Device(
            id = "ESP32_001",
            name = "E-Tongue Sensor Array",
            macAddress = "AA:BB:CC:DD:EE:FF",
            signalStrength = -45,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
        
        return SensorDataBatch(
            sessionId = "session_20220101_001",
            startTime = 1640995200000L,
            endTime = 1640995220000L,
            deviceInfo = device,
            dataPoints = dataPackets,
            metadata = metadata
        )
    }
    
    @Test
    fun `complete serialization and deserialization maintains all data integrity`() {
        // Given
        val originalBatch = createCompleteTestBatch()
        
        // When - Serialize to JSON
        val jsonString = DataSerializer.serializeBatch(originalBatch)
        
        // Then - JSON should be valid and contain expected structure
        assertTrue("JSON should not be empty", jsonString.isNotBlank())
        assertTrue("JSON should contain session ID", jsonString.contains("session_20220101_001"))
        assertTrue("JSON should contain device info", jsonString.contains("ESP32_001"))
        assertTrue("JSON should contain sensor readings", jsonString.contains("ph"))
        assertTrue("JSON should contain electrode readings", jsonString.contains("platinum"))
        assertTrue("JSON should contain metadata", jsonString.contains("Herbal Tea Sample"))
        assertTrue("JSON should contain calibration data", jsonString.contains("calibrationDate"))
        
        // When - Deserialize back to object
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        
        // Then - All data should be preserved exactly
        assertEquals("Session ID should match", originalBatch.sessionId, deserializedBatch.sessionId)
        assertEquals("Start time should match", originalBatch.startTime, deserializedBatch.startTime)
        assertEquals("End time should match", originalBatch.endTime, deserializedBatch.endTime)
        assertEquals("Data point count should match", originalBatch.dataPoints.size, deserializedBatch.dataPoints.size)
        
        // Verify device info
        assertEquals("Device ID should match", originalBatch.deviceInfo.id, deserializedBatch.deviceInfo.id)
        assertEquals("Device name should match", originalBatch.deviceInfo.name, deserializedBatch.deviceInfo.name)
        assertEquals("MAC address should match", originalBatch.deviceInfo.macAddress, deserializedBatch.deviceInfo.macAddress)
        assertEquals("Connection type should match", originalBatch.deviceInfo.connectionType, deserializedBatch.deviceInfo.connectionType)
        
        // Verify first data packet in detail
        val originalPacket = originalBatch.dataPoints[0]
        val deserializedPacket = deserializedBatch.dataPoints[0]
        
        assertEquals("Timestamp should match", originalPacket.timestamp, deserializedPacket.timestamp)
        assertEquals("Device ID should match", originalPacket.deviceId, deserializedPacket.deviceId)
        
        // Verify sensor readings
        val originalSensors = originalPacket.sensorReadings
        val deserializedSensors = deserializedPacket.sensorReadings
        
        assertEquals("pH should match", originalSensors.ph, deserializedSensors.ph, 0.001)
        assertEquals("TDS should match", originalSensors.tds, deserializedSensors.tds, 0.001)
        assertEquals("UV absorbance should match", originalSensors.uvAbsorbance, deserializedSensors.uvAbsorbance, 0.001)
        assertEquals("Temperature should match", originalSensors.temperature, deserializedSensors.temperature, 0.001)
        assertEquals("Moisture should match", originalSensors.moisturePercentage, deserializedSensors.moisturePercentage, 0.001)
        
        // Verify color data
        assertEquals("Red value should match", originalSensors.colorRgb.red, deserializedSensors.colorRgb.red)
        assertEquals("Green value should match", originalSensors.colorRgb.green, deserializedSensors.colorRgb.green)
        assertEquals("Blue value should match", originalSensors.colorRgb.blue, deserializedSensors.colorRgb.blue)
        assertEquals("Hex value should match", originalSensors.colorRgb.hex, deserializedSensors.colorRgb.hex)
        
        // Verify electrode readings
        val originalElectrodes = originalPacket.electrodeReadings
        val deserializedElectrodes = deserializedPacket.electrodeReadings
        
        assertEquals("Platinum should match", originalElectrodes.platinum, deserializedElectrodes.platinum, 0.001)
        assertEquals("Silver should match", originalElectrodes.silver, deserializedElectrodes.silver, 0.001)
        assertEquals("Silver chloride should match", originalElectrodes.silverChloride, deserializedElectrodes.silverChloride, 0.001)
        assertEquals("Stainless steel should match", originalElectrodes.stainlessSteel, deserializedElectrodes.stainlessSteel, 0.001)
        assertEquals("Copper should match", originalElectrodes.copper, deserializedElectrodes.copper, 0.001)
        assertEquals("Carbon should match", originalElectrodes.carbon, deserializedElectrodes.carbon, 0.001)
        assertEquals("Zinc should match", originalElectrodes.zinc, deserializedElectrodes.zinc, 0.001)
        
        // Verify metadata
        val originalMetadata = originalBatch.metadata
        val deserializedMetadata = deserializedBatch.metadata
        
        assertEquals("Sample type should match", originalMetadata.sampleType, deserializedMetadata.sampleType)
        assertEquals("Test conditions should match", originalMetadata.testConditions, deserializedMetadata.testConditions)
        assertEquals("Operator notes should match", originalMetadata.operatorNotes, deserializedMetadata.operatorNotes)
        
        // Verify calibration data
        val originalCalibration = originalMetadata.calibrationData!!
        val deserializedCalibration = deserializedMetadata.calibrationData!!
        
        assertEquals("Calibration date should match", originalCalibration.calibrationDate, deserializedCalibration.calibrationDate)
        assertEquals("pH calibration offset should match", originalCalibration.phCalibration!!.offset, deserializedCalibration.phCalibration!!.offset, 0.001)
        assertEquals("pH calibration scale should match", originalCalibration.phCalibration!!.scale, deserializedCalibration.phCalibration!!.scale, 0.001)
        assertEquals("TDS calibration offset should match", originalCalibration.tdsCalibration!!.offset, deserializedCalibration.tdsCalibration!!.offset, 0.001)
        assertEquals("TDS calibration scale should match", originalCalibration.tdsCalibration!!.scale, deserializedCalibration.tdsCalibration!!.scale, 0.001)
        
        // Verify electrode calibrations
        assertEquals("Electrode calibration count should match", originalCalibration.electrodeCalibrations.size, deserializedCalibration.electrodeCalibrations.size)
        
        val originalPtCal = originalCalibration.electrodeCalibrations["platinum"]!!
        val deserializedPtCal = deserializedCalibration.electrodeCalibrations["platinum"]!!
        assertEquals("Platinum calibration offset should match", originalPtCal.offset, deserializedPtCal.offset, 0.001)
        assertEquals("Platinum calibration scale should match", originalPtCal.scale, deserializedPtCal.scale, 0.001)
    }
    
    @Test
    fun `serialized JSON is human readable and properly formatted`() {
        // Given
        val batch = createCompleteTestBatch()
        
        // When
        val jsonString = DataSerializer.serializeBatch(batch)
        
        // Then
        assertTrue("JSON should contain newlines for readability", jsonString.contains("\n"))
        assertTrue("JSON should contain proper indentation", jsonString.contains("  "))
        assertTrue("JSON should start with opening brace", jsonString.trim().startsWith("{"))
        assertTrue("JSON should end with closing brace", jsonString.trim().endsWith("}"))
        
        // Verify key sections are present and properly formatted
        val lines = jsonString.lines()
        assertTrue("Should have multiple lines", lines.size > 10)
        
        // Check for proper JSON structure
        assertTrue("Should contain sessionId field", jsonString.contains("\"sessionId\""))
        assertTrue("Should contain dataPoints array", jsonString.contains("\"dataPoints\""))
        assertTrue("Should contain metadata object", jsonString.contains("\"metadata\""))
        assertTrue("Should contain deviceInfo object", jsonString.contains("\"deviceInfo\""))
    }
    
    @Test
    fun `validation methods work correctly on deserialized data`() {
        // Given
        val originalBatch = createCompleteTestBatch()
        val jsonString = DataSerializer.serializeBatch(originalBatch)
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        
        // When & Then
        assertTrue("Deserialized batch should be valid", deserializedBatch.isValid())
        assertTrue("Deserialized metadata should be valid", deserializedBatch.metadata.isValid())
        assertTrue("Deserialized device should be valid", deserializedBatch.deviceInfo.isValid())
        
        // Test individual data packets
        for (packet in deserializedBatch.dataPoints) {
            assertTrue("Each packet should be valid", packet.isValid())
            assertTrue("Each sensor reading should be valid", packet.sensorReadings.isValid())
            assertTrue("Each electrode reading should be valid", packet.electrodeReadings.isValid())
            assertTrue("Each color data should be valid", packet.sensorReadings.colorRgb.isValid())
        }
        
        // Test calibration data
        val calibrationData = deserializedBatch.metadata.calibrationData!!
        assertTrue("Calibration data should be valid", calibrationData.isValid())
    }
    
    @Test
    fun `large dataset serialization maintains performance and accuracy`() {
        // Given - Create a batch with many data points
        val basePacket = createCompleteTestBatch().dataPoints[0]
        val largeDataPoints = (0..999).map { index ->
            basePacket.copy(
                timestamp = basePacket.timestamp + (index * 1000L), // 1 second intervals
                sensorReadings = basePacket.sensorReadings.copy(
                    ph = 7.0 + (index % 100) * 0.01, // Vary pH slightly
                    temperature = 23.0 + (index % 50) * 0.1 // Vary temperature slightly
                ),
                electrodeReadings = basePacket.electrodeReadings.copy(
                    platinum = basePacket.electrodeReadings.platinum + (index % 10) * 0.01
                )
            )
        }
        
        val largeBatch = createCompleteTestBatch().copy(
            dataPoints = largeDataPoints,
            endTime = basePacket.timestamp + 999000L // 999 seconds later
        )
        
        // When
        val startTime = System.currentTimeMillis()
        val jsonString = DataSerializer.serializeBatch(largeBatch)
        val serializationTime = System.currentTimeMillis() - startTime
        
        val deserializeStartTime = System.currentTimeMillis()
        val deserializedBatch = DataSerializer.deserializeBatch(jsonString)
        val deserializationTime = System.currentTimeMillis() - deserializeStartTime
        
        // Then
        assertEquals("Data point count should match", 1000, deserializedBatch.dataPoints.size)
        assertTrue("Serialization should complete in reasonable time", serializationTime < 5000) // 5 seconds max
        assertTrue("Deserialization should complete in reasonable time", deserializationTime < 5000) // 5 seconds max
        
        // Verify first and last data points
        val firstOriginal = largeBatch.dataPoints.first()
        val firstDeserialized = deserializedBatch.dataPoints.first()
        assertEquals("First packet timestamp should match", firstOriginal.timestamp, firstDeserialized.timestamp)
        
        val lastOriginal = largeBatch.dataPoints.last()
        val lastDeserialized = deserializedBatch.dataPoints.last()
        assertEquals("Last packet timestamp should match", lastOriginal.timestamp, lastDeserialized.timestamp)
        
        // Verify sampling rate calculation works with large dataset
        val samplingRate = deserializedBatch.getAverageSamplingRate()
        assertEquals("Sampling rate should be approximately 1 Hz", 1.0, samplingRate, 0.1)
    }
}