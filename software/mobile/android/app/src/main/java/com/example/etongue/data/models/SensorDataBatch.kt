package com.example.etongue.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a batch of sensor data collected during a measurement session
 * Used for data persistence and analysis
 */
@Serializable
data class SensorDataBatch(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val deviceInfo: ESP32Device,
    val dataPoints: List<SensorDataPacket>,
    val metadata: SessionMetadata
) {
    /**
     * Returns the duration of the measurement session in milliseconds
     */
    val duration: Long
        get() = endTime - startTime
    
    /**
     * Returns the number of data points in this batch
     */
    val dataPointCount: Int
        get() = dataPoints.size
    
    /**
     * Validates that the batch has consistent data
     */
    fun isValid(): Boolean {
        return sessionId.isNotBlank() &&
               startTime > 0 &&
               endTime >= startTime &&
               dataPoints.isNotEmpty() &&
               deviceInfo.isValid()
    }
    
    /**
     * Returns the average sampling rate in Hz (samples per second)
     */
    fun getAverageSamplingRate(): Double {
        if (dataPoints.size < 2 || duration == 0L) return 0.0
        return (dataPoints.size - 1) * 1000.0 / duration
    }
}

/**
 * Contains metadata about a measurement session
 */
@Serializable
data class SessionMetadata(
    val sampleType: String? = null,
    val testConditions: String? = null,
    val operatorNotes: String? = null,
    val calibrationData: CalibrationData? = null,
    val appVersion: String = "1.0",
    val dataFormatVersion: String = "1.0"
) {
    /**
     * Validates that required metadata fields are present
     */
    fun isValid(): Boolean {
        return appVersion.isNotBlank() && dataFormatVersion.isNotBlank()
    }
}

/**
 * Contains calibration information for the sensors
 */
@Serializable
data class CalibrationData(
    val calibrationDate: Long,
    val phCalibration: SensorCalibration? = null,
    val tdsCalibration: SensorCalibration? = null,
    val temperatureCalibration: SensorCalibration? = null,
    val electrodeCalibrations: Map<String, SensorCalibration> = emptyMap()
) {
    /**
     * Validates that calibration data is consistent
     */
    fun isValid(): Boolean {
        return calibrationDate > 0
    }
}

/**
 * Represents calibration parameters for a specific sensor
 */
@Serializable
data class SensorCalibration(
    val offset: Double = 0.0,
    val scale: Double = 1.0,
    val referenceValue: Double? = null,
    val calibrationNotes: String? = null
) {
    /**
     * Applies calibration to a raw sensor value
     */
    fun calibrate(rawValue: Double): Double {
        return (rawValue + offset) * scale
    }
}

/**
 * Represents information about a saved data file
 */
@Serializable
data class DataFile(
    val fileName: String,
    val filePath: String,
    val createdAt: Long,
    val fileSize: Long,
    val dataPointCount: Int,
    val sessionId: String? = null,
    val deviceId: String? = null
) {
    /**
     * Returns a human-readable file size
     */
    fun getFormattedFileSize(): String {
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Validates that file information is complete
     */
    fun isValid(): Boolean {
        return fileName.isNotBlank() &&
               filePath.isNotBlank() &&
               createdAt > 0 &&
               fileSize >= 0 &&
               dataPointCount >= 0
    }
}