package com.example.etongue.data.validation

import com.example.etongue.data.models.SensorReadings
import com.example.etongue.data.models.ElectrodeReadings
import com.example.etongue.data.models.SensorDataPacket

/**
 * Validation utilities for sensor data ranges and integrity
 */
object SensorDataValidator {
    
    // Sensor reading ranges based on typical sensor specifications
    private const val PH_MIN = 0.0
    private const val PH_MAX = 14.0
    private const val TDS_MIN = 0.0
    private const val TDS_MAX = 5000.0 // ppm
    private const val UV_ABSORBANCE_MIN = 0.0
    private const val UV_ABSORBANCE_MAX = 4.0
    private const val TEMPERATURE_MIN = -40.0 // Celsius
    private const val TEMPERATURE_MAX = 125.0 // Celsius
    private const val MOISTURE_MIN = 0.0
    private const val MOISTURE_MAX = 100.0 // percentage
    
    // Electrode reading ranges (typical voltage ranges in mV)
    private const val ELECTRODE_MIN = -2000.0
    private const val ELECTRODE_MAX = 2000.0
    
    /**
     * Validates pH sensor reading
     */
    fun isValidPH(ph: Double): Boolean {
        return ph.isFinite() && ph in PH_MIN..PH_MAX
    }
    
    /**
     * Validates TDS sensor reading
     */
    fun isValidTDS(tds: Double): Boolean {
        return tds.isFinite() && tds in TDS_MIN..TDS_MAX
    }
    
    /**
     * Validates UV absorbance reading
     */
    fun isValidUVAbsorbance(uvAbsorbance: Double): Boolean {
        return uvAbsorbance.isFinite() && uvAbsorbance in UV_ABSORBANCE_MIN..UV_ABSORBANCE_MAX
    }
    
    /**
     * Validates temperature reading
     */
    fun isValidTemperature(temperature: Double): Boolean {
        return temperature.isFinite() && temperature in TEMPERATURE_MIN..TEMPERATURE_MAX
    }
    
    /**
     * Validates moisture percentage reading
     */
    fun isValidMoisture(moisture: Double): Boolean {
        return moisture.isFinite() && moisture in MOISTURE_MIN..MOISTURE_MAX
    }
    
    /**
     * Validates electrode reading
     */
    fun isValidElectrodeReading(reading: Double): Boolean {
        return reading.isFinite() && reading in ELECTRODE_MIN..ELECTRODE_MAX
    }
    
    /**
     * Validates all sensor readings in SensorReadings object
     */
    fun validateSensorReadings(readings: SensorReadings): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (!isValidPH(readings.ph)) {
            errors.add("pH reading ${readings.ph} is out of valid range ($PH_MIN-$PH_MAX)")
        }
        
        if (!isValidTDS(readings.tds)) {
            errors.add("TDS reading ${readings.tds} is out of valid range ($TDS_MIN-$TDS_MAX ppm)")
        }
        
        if (!isValidUVAbsorbance(readings.uvAbsorbance)) {
            errors.add("UV absorbance ${readings.uvAbsorbance} is out of valid range ($UV_ABSORBANCE_MIN-$UV_ABSORBANCE_MAX)")
        }
        
        if (!isValidTemperature(readings.temperature)) {
            errors.add("Temperature ${readings.temperature}°C is out of valid range ($TEMPERATURE_MIN-$TEMPERATURE_MAX°C)")
        }
        
        if (!isValidMoisture(readings.moisturePercentage)) {
            errors.add("Moisture ${readings.moisturePercentage}% is out of valid range ($MOISTURE_MIN-$MOISTURE_MAX%)")
        }
        
        if (!readings.colorRgb.isValid()) {
            errors.add("Color RGB values are invalid")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Validates all electrode readings in ElectrodeReadings object
     */
    fun validateElectrodeReadings(readings: ElectrodeReadings): ValidationResult {
        val errors = mutableListOf<String>()
        
        val electrodeMap = mapOf(
            "Platinum" to readings.platinum,
            "Silver" to readings.silver,
            "Silver Chloride" to readings.silverChloride,
            "Stainless Steel" to readings.stainlessSteel,
            "Copper" to readings.copper,
            "Carbon" to readings.carbon,
            "Zinc" to readings.zinc
        )
        
        electrodeMap.forEach { (name, value) ->
            if (!isValidElectrodeReading(value)) {
                errors.add("$name electrode reading ${value}mV is out of valid range ($ELECTRODE_MIN-${ELECTRODE_MAX}mV)")
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Validates complete sensor data packet
     */
    fun validateSensorDataPacket(packet: SensorDataPacket): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate timestamp
        if (packet.timestamp <= 0) {
            errors.add("Invalid timestamp: ${packet.timestamp}")
        }
        
        // Validate device ID
        if (packet.deviceId.isBlank()) {
            errors.add("Device ID cannot be blank")
        }
        
        // Validate sensor readings
        when (val sensorResult = validateSensorReadings(packet.sensorReadings)) {
            is ValidationResult.Error -> errors.addAll(sensorResult.errors)
            ValidationResult.Success -> { /* Valid */ }
        }
        
        // Validate electrode readings
        when (val electrodeResult = validateElectrodeReadings(packet.electrodeReadings)) {
            is ValidationResult.Error -> errors.addAll(electrodeResult.errors)
            ValidationResult.Success -> { /* Valid */ }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
}

/**
 * Represents the result of a validation operation
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val errors: List<String>) : ValidationResult()
    
    /**
     * Returns true if validation was successful
     */
    fun isValid(): Boolean = this is Success
    
    /**
     * Returns error messages if validation failed, empty list if successful
     */
    fun getErrorMessages(): List<String> = when (this) {
        is Error -> errors
        Success -> emptyList()
    }
}