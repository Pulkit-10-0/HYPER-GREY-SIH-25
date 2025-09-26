package com.example.etongue.domain.usecases

import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import java.util.UUID

/**
 * Use case for saving sensor data to persistent storage
 * Handles data conversion, batch creation, and file management
 */
class SaveSensorDataUseCase(
    private val sensorDataRepository: SensorDataRepository
) {
    
    /**
     * Saves current buffered sensor data as a JSON file
     * @param deviceInfo Information about the connected device
     * @param metadata Optional session metadata
     * @return Result with the saved file name or error
     */
    suspend fun saveCurrentSession(
        deviceInfo: ESP32Device,
        metadata: SessionMetadata = SessionMetadata()
    ): Result<String> {
        return try {
            val bufferedData = sensorDataRepository.getBufferedData()
            
            if (bufferedData.isEmpty()) {
                return Result.failure(IllegalStateException("No data to save"))
            }
            
            val batch = createSensorDataBatch(bufferedData, deviceInfo, metadata)
            val result = sensorDataRepository.saveData(batch)
            
            if (result.isSuccess) {
                // Clear buffer after successful save
                sensorDataRepository.clearBuffer()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Saves a specific list of sensor data points
     * @param dataPoints List of sensor data packets to save
     * @param deviceInfo Information about the connected device
     * @param metadata Optional session metadata
     * @return Result with the saved file name or error
     */
    suspend fun saveDataPoints(
        dataPoints: List<SensorDataPacket>,
        deviceInfo: ESP32Device,
        metadata: SessionMetadata = SessionMetadata()
    ): Result<String> {
        return try {
            if (dataPoints.isEmpty()) {
                return Result.failure(IllegalStateException("No data points to save"))
            }
            
            val batch = createSensorDataBatch(dataPoints, deviceInfo, metadata)
            sensorDataRepository.saveData(batch)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Creates a SensorDataBatch from a list of data points
     */
    private fun createSensorDataBatch(
        dataPoints: List<SensorDataPacket>,
        deviceInfo: ESP32Device,
        metadata: SessionMetadata
    ): SensorDataBatch {
        val sessionId = generateSessionId()
        val startTime = dataPoints.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val endTime = dataPoints.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        
        return SensorDataBatch(
            sessionId = sessionId,
            startTime = startTime,
            endTime = endTime,
            deviceInfo = deviceInfo,
            dataPoints = dataPoints.sortedBy { it.timestamp },
            metadata = metadata.copy(
                appVersion = "1.0",
                dataFormatVersion = "1.0"
            )
        )
    }
    
    /**
     * Generates a unique session identifier
     */
    private fun generateSessionId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().take(8)
        return "session_${timestamp}_$uuid"
    }
    
    /**
     * Generates a batch identifier for data organization
     */
    fun generateBatchId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().take(8)
        return "batch_${timestamp}_$uuid"
    }
    
    /**
     * Creates session metadata with timestamp and batch information
     */
    fun createSessionMetadata(
        sampleType: String? = null,
        testConditions: String? = null,
        operatorNotes: String? = null,
        calibrationData: CalibrationData? = null
    ): SessionMetadata {
        return SessionMetadata(
            sampleType = sampleType,
            testConditions = testConditions,
            operatorNotes = operatorNotes,
            calibrationData = calibrationData,
            appVersion = "1.0",
            dataFormatVersion = "1.0"
        )
    }
}