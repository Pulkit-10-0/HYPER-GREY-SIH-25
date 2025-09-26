package com.example.etongue.data.repository

import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.DataFile
import com.example.etongue.data.models.StreamingStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing sensor data streaming and persistence
 * Handles real-time data streaming from ESP32 devices and data storage operations
 */
interface SensorDataRepository {
    
    /**
     * Starts streaming sensor data from the connected device
     * @return Flow of sensor data packets received from the device
     */
    suspend fun startStreaming(): Flow<SensorDataPacket>
    
    /**
     * Stops the current data streaming session
     * @return Result indicating success or failure
     */
    suspend fun stopStreaming(): Result<Unit>
    
    /**
     * Gets the current streaming status
     * @return Flow of streaming status updates
     */
    fun getStreamingStatus(): Flow<StreamingStatus>
    
    /**
     * Saves a batch of sensor data to persistent storage
     * @param data The sensor data batch to save
     * @return Result with the saved file name or error
     */
    suspend fun saveData(data: SensorDataBatch): Result<String>
    
    /**
     * Loads all saved data files
     * @return List of available data files
     */
    suspend fun loadSavedFiles(): List<DataFile>
    
    /**
     * Loads a specific data file by name
     * @param fileName The name of the file to load
     * @return Result with the loaded data batch or error
     */
    suspend fun loadDataFile(fileName: String): Result<SensorDataBatch>
    
    /**
     * Deletes a saved data file
     * @param fileName The name of the file to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(fileName: String): Result<Unit>
    
    /**
     * Gets the current data buffer size (number of buffered data points)
     * @return Current buffer size
     */
    fun getBufferSize(): Int
    
    /**
     * Clears the current data buffer
     */
    suspend fun clearBuffer()
    
    /**
     * Gets buffered data points for the current session
     * @return List of buffered sensor data packets
     */
    suspend fun getBufferedData(): List<SensorDataPacket>
}