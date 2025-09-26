package com.example.etongue.data.models

import com.example.etongue.data.validation.SensorDataValidator
import com.example.etongue.data.validation.ValidationResult

/**
 * Extension functions for data models to provide additional functionality
 */

/**
 * Validates the sensor readings and returns validation result
 */
fun SensorReadings.validate(): ValidationResult {
    return SensorDataValidator.validateSensorReadings(this)
}

/**
 * Checks if all sensor readings are within valid ranges
 */
fun SensorReadings.isValid(): Boolean {
    return validate().isValid()
}

/**
 * Validates the electrode readings and returns validation result
 */
fun ElectrodeReadings.validate(): ValidationResult {
    return SensorDataValidator.validateElectrodeReadings(this)
}

/**
 * Checks if all electrode readings are within valid ranges
 */
fun ElectrodeReadings.isValid(): Boolean {
    return validate().isValid()
}

/**
 * Validates the complete sensor data packet
 */
fun SensorDataPacket.validate(): ValidationResult {
    return SensorDataValidator.validateSensorDataPacket(this)
}

/**
 * Checks if the sensor data packet is completely valid
 */
fun SensorDataPacket.isValid(): Boolean {
    return validate().isValid()
}

/**
 * Returns a formatted timestamp string
 */
fun SensorDataPacket.getFormattedTimestamp(): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

/**
 * Returns all electrode readings as a map for easier iteration
 */
fun ElectrodeReadings.toMap(): Map<String, Double> {
    return mapOf(
        "Platinum" to platinum,
        "Silver" to silver,
        "Silver Chloride" to silverChloride,
        "Stainless Steel" to stainlessSteel,
        "Copper" to copper,
        "Carbon" to carbon,
        "Zinc" to zinc
    )
}

/**
 * Returns all sensor readings as a map for easier iteration
 */
fun SensorReadings.toMap(): Map<String, Any> {
    return mapOf(
        "pH" to ph,
        "TDS (ppm)" to tds,
        "UV Absorbance" to uvAbsorbance,
        "Temperature (°C)" to temperature,
        "Color" to colorRgb.hex,
        "Moisture (%)" to moisturePercentage
    )
}