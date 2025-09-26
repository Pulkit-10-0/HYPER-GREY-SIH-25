package com.example.etongue.data.models

import com.example.etongue.data.validation.SensorDataValidator
import com.example.etongue.data.validation.ValidationResult
import org.junit.Test
import org.junit.Assert.*

class DataModelsTest {
    
    @Test
    fun `ColorData hex conversion works correctly`() {
        val color = ColorData(255, 128, 0)
        assertEquals("#FF8000", color.hex)
    }
    
    @Test
    fun `ColorData fromHex creates correct instance`() {
        val color = ColorData.fromHex("#FF8000")
        assertNotNull(color)
        assertEquals(255, color!!.red)
        assertEquals(128, color.green)
        assertEquals(0, color.blue)
    }
    
    @Test
    fun `ColorData fromHex handles invalid hex`() {
        val color = ColorData.fromHex("invalid")
        assertNull(color)
    }
    
    @Test
    fun `ColorData validation works correctly`() {
        val validColor = ColorData(255, 128, 0)
        assertTrue(validColor.isValid())
        
        val invalidColor = ColorData(256, -1, 300)
        assertFalse(invalidColor.isValid())
    }
    
    @Test
    fun `ESP32Device validation works correctly`() {
        val validDevice = ESP32Device(
            id = "device1",
            name = "ESP32-Sensor",
            macAddress = "AA:BB:CC:DD:EE:FF",
            signalStrength = -50,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
        assertTrue(validDevice.isValid())
        
        val invalidDevice = ESP32Device(
            id = "",
            name = "ESP32-Sensor",
            macAddress = "invalid-mac",
            signalStrength = -50,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
        assertFalse(invalidDevice.isValid())
    }
    
    @Test
    fun `ESP32Device signal strength description works correctly`() {
        val device = ESP32Device("id", "name", "AA:BB:CC:DD:EE:FF", -45, ConnectionType.BLUETOOTH_LE)
        assertEquals("Excellent", device.getSignalStrengthDescription())
        
        val weakDevice = ESP32Device("id", "name", "AA:BB:CC:DD:EE:FF", -85, ConnectionType.BLUETOOTH_LE)
        assertEquals("Very Weak", weakDevice.getSignalStrengthDescription())
    }
    
    @Test
    fun `ConnectionStatus helper methods work correctly`() {
        assertTrue(ConnectionStatus.CONNECTED.isActive())
        assertTrue(ConnectionStatus.STREAMING.isActive())
        assertFalse(ConnectionStatus.DISCONNECTED.isActive())
        
        assertTrue(ConnectionStatus.CONNECTED.canStartStreaming())
        assertFalse(ConnectionStatus.STREAMING.canStartStreaming())
    }
    
    @Test
    fun `SensorDataValidator validates pH correctly`() {
        assertTrue(SensorDataValidator.isValidPH(7.0))
        assertTrue(SensorDataValidator.isValidPH(0.0))
        assertTrue(SensorDataValidator.isValidPH(14.0))
        assertFalse(SensorDataValidator.isValidPH(-1.0))
        assertFalse(SensorDataValidator.isValidPH(15.0))
        assertFalse(SensorDataValidator.isValidPH(Double.NaN))
    }
    
    @Test
    fun `SensorDataValidator validates TDS correctly`() {
        assertTrue(SensorDataValidator.isValidTDS(1000.0))
        assertTrue(SensorDataValidator.isValidTDS(0.0))
        assertTrue(SensorDataValidator.isValidTDS(5000.0))
        assertFalse(SensorDataValidator.isValidTDS(-1.0))
        assertFalse(SensorDataValidator.isValidTDS(5001.0))
    }
    
    @Test
    fun `SensorDataValidator validates temperature correctly`() {
        assertTrue(SensorDataValidator.isValidTemperature(25.0))
        assertTrue(SensorDataValidator.isValidTemperature(-40.0))
        assertTrue(SensorDataValidator.isValidTemperature(125.0))
        assertFalse(SensorDataValidator.isValidTemperature(-41.0))
        assertFalse(SensorDataValidator.isValidTemperature(126.0))
    }
    
    @Test
    fun `SensorDataValidator validates complete sensor readings`() {
        val validReadings = SensorReadings(
            ph = 7.0,
            tds = 1000.0,
            uvAbsorbance = 1.5,
            temperature = 25.0,
            colorRgb = ColorData(255, 128, 0),
            moisturePercentage = 50.0
        )
        
        val result = SensorDataValidator.validateSensorReadings(validReadings)
        assertTrue(result.isValid())
        
        val invalidReadings = SensorReadings(
            ph = -1.0, // Invalid
            tds = 1000.0,
            uvAbsorbance = 1.5,
            temperature = 25.0,
            colorRgb = ColorData(255, 128, 0),
            moisturePercentage = 50.0
        )
        
        val invalidResult = SensorDataValidator.validateSensorReadings(invalidReadings)
        assertFalse(invalidResult.isValid())
        assertTrue(invalidResult.getErrorMessages().isNotEmpty())
    }
    
    @Test
    fun `Extension functions work correctly`() {
        val validReadings = SensorReadings(
            ph = 7.0,
            tds = 1000.0,
            uvAbsorbance = 1.5,
            temperature = 25.0,
            colorRgb = ColorData(255, 128, 0),
            moisturePercentage = 50.0
        )
        
        assertTrue(validReadings.isValid())
        
        val readingsMap = validReadings.toMap()
        assertEquals(7.0, readingsMap["pH"])
        assertEquals("#FF8000", readingsMap["Color"])
    }
}