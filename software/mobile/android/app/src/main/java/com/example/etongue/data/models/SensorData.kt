package com.example.etongue.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a complete sensor data packet received from ESP32 device
 */
@Serializable
data class SensorDataPacket(
    val timestamp: Long,
    val deviceId: String,
    val sensorReadings: SensorReadings,
    val electrodeReadings: ElectrodeReadings
) {
    /**
     * Validates that the sensor data packet has valid values
     */
    fun isValid(): Boolean {
        return timestamp > 0 &&
               deviceId.isNotBlank() &&
               sensorReadings.isValid() &&
               electrodeReadings.isValid()
    }
}

/**
 * Contains readings from all environmental sensors
 */
@Serializable
data class SensorReadings(
    val ph: Double,
    val tds: Double,
    val uvAbsorbance: Double,
    val temperature: Double,
    val colorRgb: ColorData,
    val moisturePercentage: Double
) {
    /**
     * Validates that all sensor readings are within expected ranges
     */
    fun isValid(): Boolean {
        return ph in 0.0..14.0 &&
               tds >= 0.0 &&
               uvAbsorbance >= 0.0 &&
               temperature in -40.0..125.0 && // DS18B20 sensor range
               colorRgb.isValid() &&
               moisturePercentage in 0.0..100.0
    }
}

/**
 * Contains readings from all electrode channels
 */
@Serializable
data class ElectrodeReadings(
    val platinum: Double,
    val silver: Double,
    val silverChloride: Double,
    val stainlessSteel: Double,
    val copper: Double,
    val carbon: Double,
    val zinc: Double
) {
    /**
     * Validates that electrode readings are within reasonable ranges
     * Note: Electrode readings can be negative, so we check for finite values
     */
    fun isValid(): Boolean {
        return listOf(platinum, silver, silverChloride, stainlessSteel, copper, carbon, zinc)
            .all { it.isFinite() }
    }
    
    /**
     * Returns all electrode readings as a map for easier processing
     */
    fun toMap(): Map<String, Double> {
        return mapOf(
            "platinum" to platinum,
            "silver" to silver,
            "silverChloride" to silverChloride,
            "stainlessSteel" to stainlessSteel,
            "copper" to copper,
            "carbon" to carbon,
            "zinc" to zinc
        )
    }
}