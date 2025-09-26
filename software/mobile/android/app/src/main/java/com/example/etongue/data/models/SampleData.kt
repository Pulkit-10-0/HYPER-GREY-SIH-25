package com.example.etongue.data.models

/**
 * Sample data for testing and demonstration purposes
 */
object SampleData {
    
    /**
     * Creates a sample ESP32 device for testing
     */
    fun createSampleESP32Device(): ESP32Device {
        return ESP32Device(
            id = "esp32_001",
            name = "E-Tongue Sensor Array",
            macAddress = "AA:BB:CC:DD:EE:FF",
            signalStrength = -55,
            connectionType = ConnectionType.BLUETOOTH_LE
        )
    }
    
    /**
     * Creates sample sensor readings with typical values
     */
    fun createSampleSensorReadings(): SensorReadings {
        return SensorReadings(
            ph = 7.2,
            tds = 850.0,
            uvAbsorbance = 1.25,
            temperature = 23.5,
            colorRgb = ColorData(180, 120, 80), // Light brown color
            moisturePercentage = 65.0
        )
    }
    
    /**
     * Creates sample electrode readings with typical values
     */
    fun createSampleElectrodeReadings(): ElectrodeReadings {
        return ElectrodeReadings(
            platinum = 245.6,
            silver = -123.4,
            silverChloride = 89.2,
            stainlessSteel = 156.8,
            copper = -78.9,
            carbon = 234.1,
            zinc = -45.3
        )
    }
    
    /**
     * Creates a complete sample sensor data packet
     */
    fun createSampleSensorDataPacket(): SensorDataPacket {
        return SensorDataPacket(
            timestamp = System.currentTimeMillis(),
            deviceId = "esp32_001",
            sensorReadings = createSampleSensorReadings(),
            electrodeReadings = createSampleElectrodeReadings()
        )
    }
    
    /**
     * Creates sample data with invalid values for testing validation
     */
    fun createInvalidSensorReadings(): SensorReadings {
        return SensorReadings(
            ph = -1.0, // Invalid: below 0
            tds = 6000.0, // Invalid: above 5000
            uvAbsorbance = 5.0, // Invalid: above 4.0
            temperature = 150.0, // Invalid: above 125°C
            colorRgb = ColorData(300, -50, 400), // Invalid RGB values
            moisturePercentage = 150.0 // Invalid: above 100%
        )
    }
}