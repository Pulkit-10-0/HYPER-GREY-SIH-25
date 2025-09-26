package com.example.etongue.data.communication

import com.example.etongue.data.models.ColorData
import com.example.etongue.data.models.ElectrodeReadings
import com.example.etongue.data.models.SensorReadings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DataPacketParserTest {
    
    private lateinit var parser: DataPacketParser
    private val testDeviceId = "ESP32_TEST_001"
    
    @Before
    fun setUp() {
        parser = DataPacketParser()
    }
    
    @Test
    fun `parseDataPacket should parse valid JSON format successfully`() {
        val jsonData = """
            {
                "timestamp": 1640995200000,
                "sensors": {
                    "ph": 7.2,
                    "tds": 450.5,
                    "uv": 0.85,
                    "temperature": 23.5,
                    "color": {
                        "r": 255,
                        "g": 128,
                        "b": 64
                    },
                    "moisture": 65.3
                },
                "electrodes": {
                    "pt": 1.23,
                    "ag": -0.45,
                    "agcl": 0.78,
                    "ss": 2.15,
                    "cu": -1.67,
                    "c": 0.92,
                    "zn": 1.44
                }
            }
        """.trimIndent()
        
        val result = parser.parseDataPacket(jsonData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        
        assertEquals(1640995200000L, packet.timestamp)
        assertEquals(testDeviceId, packet.deviceId)
        
        // Verify sensor readings
        assertEquals(7.2, packet.sensorReadings.ph, 0.001)
        assertEquals(450.5, packet.sensorReadings.tds, 0.001)
        assertEquals(0.85, packet.sensorReadings.uvAbsorbance, 0.001)
        assertEquals(23.5, packet.sensorReadings.temperature, 0.001)
        assertEquals(65.3, packet.sensorReadings.moisturePercentage, 0.001)
        
        // Verify color data
        assertEquals(255, packet.sensorReadings.colorRgb.red)
        assertEquals(128, packet.sensorReadings.colorRgb.green)
        assertEquals(64, packet.sensorReadings.colorRgb.blue)
        
        // Verify electrode readings
        assertEquals(1.23, packet.electrodeReadings.platinum, 0.001)
        assertEquals(-0.45, packet.electrodeReadings.silver, 0.001)
        assertEquals(0.78, packet.electrodeReadings.silverChloride, 0.001)
        assertEquals(2.15, packet.electrodeReadings.stainlessSteel, 0.001)
        assertEquals(-1.67, packet.electrodeReadings.copper, 0.001)
        assertEquals(0.92, packet.electrodeReadings.carbon, 0.001)
        assertEquals(1.44, packet.electrodeReadings.zinc, 0.001)
    }
    
    @Test
    fun `parseDataPacket should parse valid CSV format successfully`() {
        val csvData = "7.2,450.5,0.85,23.5,255,128,64,65.3,1.23,-0.45,0.78,2.15,-1.67,0.92,1.44"
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        
        assertEquals(testDeviceId, packet.deviceId)
        assertTrue(packet.timestamp > 0) // Should have current timestamp
        
        // Verify sensor readings
        assertEquals(7.2, packet.sensorReadings.ph, 0.001)
        assertEquals(450.5, packet.sensorReadings.tds, 0.001)
        assertEquals(0.85, packet.sensorReadings.uvAbsorbance, 0.001)
        assertEquals(23.5, packet.sensorReadings.temperature, 0.001)
        assertEquals(65.3, packet.sensorReadings.moisturePercentage, 0.001)
        
        // Verify color data
        assertEquals(255, packet.sensorReadings.colorRgb.red)
        assertEquals(128, packet.sensorReadings.colorRgb.green)
        assertEquals(64, packet.sensorReadings.colorRgb.blue)
        
        // Verify electrode readings
        assertEquals(1.23, packet.electrodeReadings.platinum, 0.001)
        assertEquals(-0.45, packet.electrodeReadings.silver, 0.001)
        assertEquals(0.78, packet.electrodeReadings.silverChloride, 0.001)
        assertEquals(2.15, packet.electrodeReadings.stainlessSteel, 0.001)
        assertEquals(-1.67, packet.electrodeReadings.copper, 0.001)
        assertEquals(0.92, packet.electrodeReadings.carbon, 0.001)
        assertEquals(1.44, packet.electrodeReadings.zinc, 0.001)
    }
    
    @Test
    fun `parseDataPacket should handle JSON with missing timestamp`() {
        val jsonData = """
            {
                "sensors": {
                    "ph": 7.0,
                    "tds": 400.0,
                    "uv": 0.5,
                    "temperature": 25.0,
                    "color": {"r": 100, "g": 150, "b": 200},
                    "moisture": 50.0
                },
                "electrodes": {
                    "pt": 1.0, "ag": 0.0, "agcl": 0.5, "ss": 1.5, "cu": -1.0, "c": 0.8, "zn": 1.2
                }
            }
        """.trimIndent()
        
        val result = parser.parseDataPacket(jsonData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        assertTrue(packet.timestamp > 0) // Should use current timestamp
    }
    
    @Test
    fun `parseDataPacket should handle JSON with missing sensor data using defaults`() {
        val jsonData = """
            {
                "timestamp": 1640995200000,
                "sensors": {
                    "ph": 7.0
                },
                "electrodes": {
                    "pt": 1.0
                }
            }
        """.trimIndent()
        
        val result = parser.parseDataPacket(jsonData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        
        // Should use defaults for missing values
        assertEquals(7.0, packet.sensorReadings.ph, 0.001)
        assertEquals(0.0, packet.sensorReadings.tds, 0.001) // Default
        assertEquals(0.0, packet.sensorReadings.uvAbsorbance, 0.001) // Default
        assertEquals(25.0, packet.sensorReadings.temperature, 0.001) // Default
        assertEquals(0.0, packet.sensorReadings.moisturePercentage, 0.001) // Default
        
        // Color should use defaults
        assertEquals(0, packet.sensorReadings.colorRgb.red)
        assertEquals(0, packet.sensorReadings.colorRgb.green)
        assertEquals(0, packet.sensorReadings.colorRgb.blue)
        
        // Electrodes should use defaults for missing values
        assertEquals(1.0, packet.electrodeReadings.platinum, 0.001)
        assertEquals(0.0, packet.electrodeReadings.silver, 0.001) // Default
    }
    
    @Test
    fun `parseDataPacket should fail with insufficient CSV values`() {
        val csvData = "7.2,450.5,0.85,23.5" // Only 4 values, need at least 15
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must have at least 15 values") == true)
    }
    
    @Test
    fun `parseDataPacket should fail with invalid CSV values`() {
        val csvData = "7.2,invalid,0.85,23.5,255,128,64,65.3,1.23,-0.45,0.78,2.15,-1.67,0.92,1.44"
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `parseDataPacket should fail with invalid JSON`() {
        val invalidJson = "{ invalid json structure"
        
        val result = parser.parseDataPacket(invalidJson, testDeviceId)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `parseDataPacket should fail with unknown format`() {
        val unknownFormat = "some random text that is not JSON or CSV"
        
        val result = parser.parseDataPacket(unknownFormat, testDeviceId)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unknown data format") == true)
    }
    
    @Test
    fun `parseDataPacket should fail with invalid sensor values in CSV`() {
        // pH value of 20 is invalid (should be 0-14)
        val csvData = "20.0,450.5,0.85,23.5,255,128,64,65.3,1.23,-0.45,0.78,2.15,-1.67,0.92,1.44"
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid sensor data values") == true)
    }
    
    @Test
    fun `isValidDataFormat should return true for valid JSON`() {
        val validJson = """{"sensors": {"ph": 7.0}, "electrodes": {"pt": 1.0}}"""
        
        assertTrue(parser.isValidDataFormat(validJson))
    }
    
    @Test
    fun `isValidDataFormat should return true for valid CSV`() {
        val validCsv = "7.2,450.5,0.85,23.5,255,128,64,65.3,1.23,-0.45,0.78,2.15,-1.67,0.92,1.44"
        
        assertTrue(parser.isValidDataFormat(validCsv))
    }
    
    @Test
    fun `isValidDataFormat should return false for invalid JSON`() {
        val invalidJson = "{ invalid json"
        
        assertFalse(parser.isValidDataFormat(invalidJson))
    }
    
    @Test
    fun `isValidDataFormat should return false for insufficient CSV values`() {
        val insufficientCsv = "7.2,450.5,0.85"
        
        assertFalse(parser.isValidDataFormat(insufficientCsv))
    }
    
    @Test
    fun `isValidDataFormat should return false for CSV with non-numeric values`() {
        val invalidCsv = "7.2,invalid,0.85,23.5,255,128,64,65.3,1.23,-0.45,0.78,2.15,-1.67,0.92,1.44"
        
        assertFalse(parser.isValidDataFormat(invalidCsv))
    }
    
    @Test
    fun `isValidDataFormat should return false for unknown format`() {
        val unknownFormat = "some random text"
        
        assertFalse(parser.isValidDataFormat(unknownFormat))
    }
    
    @Test
    fun `parseDataPacket should handle edge case values correctly`() {
        // Test with edge case values
        val csvData = "0.0,0.0,0.0,-40.0,0,0,0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0"
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        
        assertEquals(0.0, packet.sensorReadings.ph, 0.001)
        assertEquals(-40.0, packet.sensorReadings.temperature, 0.001) // Valid DS18B20 range
        assertEquals(0.0, packet.sensorReadings.moisturePercentage, 0.001)
    }
    
    @Test
    fun `parseDataPacket should handle maximum valid values correctly`() {
        // Test with maximum valid values
        val csvData = "14.0,9999.9,10.0,125.0,255,255,255,100.0,999.9,-999.9,999.9,999.9,-999.9,999.9,999.9"
        
        val result = parser.parseDataPacket(csvData, testDeviceId)
        
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        
        assertEquals(14.0, packet.sensorReadings.ph, 0.001) // Max pH
        assertEquals(125.0, packet.sensorReadings.temperature, 0.001) // Max DS18B20 range
        assertEquals(100.0, packet.sensorReadings.moisturePercentage, 0.001) // Max moisture
        assertEquals(255, packet.sensorReadings.colorRgb.red) // Max RGB
    }
}