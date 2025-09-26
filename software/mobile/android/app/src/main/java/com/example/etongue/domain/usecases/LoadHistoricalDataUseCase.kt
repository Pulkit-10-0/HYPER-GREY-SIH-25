package com.example.etongue.domain.usecases

import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.DataFile
import com.example.etongue.data.repository.SensorDataRepository
import com.example.etongue.domain.errors.AppError

/**
 * Use case for loading historical sensor data from saved JSON files
 * Handles data retrieval and validation for analysis purposes
 */
class LoadHistoricalDataUseCase(
    private val sensorDataRepository: SensorDataRepository
) {
    
    /**
     * Loads all available saved data files
     * @return Result with list of data files or error
     */
    suspend fun loadAvailableFiles(): Result<List<DataFile>> {
        return try {
            val files = sensorDataRepository.loadSavedFiles()
            Result.success(files.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(AppError.StorageError("Failed to load file list", e))
        }
    }
    
    /**
     * Loads a specific data file by name
     * @param fileName The name of the file to load
     * @return Result with the loaded sensor data batch or error
     */
    suspend fun loadDataFile(fileName: String): Result<SensorDataBatch> {
        if (fileName.isBlank()) {
            return Result.failure(AppError.ValidationError("File name cannot be empty"))
        }
        
        return try {
            val result = sensorDataRepository.loadDataFile(fileName)
            result.fold(
                onSuccess = { batch ->
                    if (batch.isValid()) {
                        Result.success(batch)
                    } else {
                        Result.failure(AppError.DataValidationError("Invalid data batch in file: $fileName"))
                    }
                },
                onFailure = { error ->
                    Result.failure(AppError.StorageError("Failed to load file: $fileName", error))
                }
            )
        } catch (e: Exception) {
            Result.failure(AppError.StorageError("Unexpected error loading file: $fileName", e))
        }
    }
    
    /**
     * Validates that a data batch contains analyzable data
     * @param batch The data batch to validate
     * @return True if the batch is valid for analysis
     */
    fun validateDataForAnalysis(batch: SensorDataBatch): Boolean {
        return batch.isValid() && 
               batch.dataPoints.isNotEmpty() &&
               batch.dataPoints.all { it.isValid() }
    }
    
    /**
     * Gets summary statistics for a data batch
     * @param batch The data batch to analyze
     * @return Summary statistics
     */
    fun getDataSummary(batch: SensorDataBatch): DataSummary {
        val dataPoints = batch.dataPoints
        
        return DataSummary(
            totalDataPoints = dataPoints.size,
            duration = batch.duration,
            averageSamplingRate = batch.getAverageSamplingRate(),
            startTime = batch.startTime,
            endTime = batch.endTime,
            deviceId = batch.deviceInfo.id,
            sessionId = batch.sessionId
        )
    }
}

/**
 * Summary statistics for a data batch
 */
data class DataSummary(
    val totalDataPoints: Int,
    val duration: Long,
    val averageSamplingRate: Double,
    val startTime: Long,
    val endTime: Long,
    val deviceId: String,
    val sessionId: String
)