package com.example.etongue.data.repository

import com.example.etongue.data.models.*
import com.example.etongue.data.storage.JSONFileManager
import com.example.etongue.data.streaming.SensorDataBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/**
 * Implementation of SensorDataRepository for managing sensor data streaming and persistence
 */
class SensorDataRepositoryImpl(
    private val jsonFileManager: JSONFileManager,
    private val sensorDataBuffer: SensorDataBuffer
) : SensorDataRepository {

    private val _streamingStatus = MutableStateFlow(StreamingStatus.IDLE)
    private var isStreaming = false

    override suspend fun startStreaming(): Flow<SensorDataPacket> = flow {
        _streamingStatus.value = StreamingStatus.STARTING
        isStreaming = true
        _streamingStatus.value = StreamingStatus.STREAMING
        
        // Simulate sensor data streaming with mock data
        var counter = 0
        while (isStreaming) {
            val mockData = generateMockSensorData(counter++)
            sensorDataBuffer.addData(mockData)
            emit(mockData)
            delay(1000) // Emit data every second
        }
    }

    override suspend fun stopStreaming(): Result<Unit> {
        return try {
            _streamingStatus.value = StreamingStatus.STOPPING
            isStreaming = false
            _streamingStatus.value = StreamingStatus.IDLE
            Result.success(Unit)
        } catch (e: Exception) {
            _streamingStatus.value = StreamingStatus.ERROR
            Result.failure(e)
        }
    }

    override fun getStreamingStatus(): Flow<StreamingStatus> {
        return _streamingStatus.asStateFlow()
    }

    override suspend fun saveData(data: SensorDataBatch): Result<String> {
        return try {
            val fileName = "sensor_data_${System.currentTimeMillis()}.json"
            val result = jsonFileManager.saveBatch(data, fileName)
            if (result.isSuccess) {
                Result.success(fileName)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to save data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadSavedFiles(): List<DataFile> {
        return jsonFileManager.listFiles().getOrNull() ?: emptyList()
    }

    override suspend fun loadDataFile(fileName: String): Result<SensorDataBatch> {
        return jsonFileManager.loadBatch(fileName)
    }

    override suspend fun deleteFile(fileName: String): Result<Unit> {
        return try {
            jsonFileManager.deleteFile(fileName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBufferSize(): Int {
        return sensorDataBuffer.getBufferSize()
    }

    override suspend fun clearBuffer() {
        sensorDataBuffer.clear()
    }

    override suspend fun getBufferedData(): List<SensorDataPacket> {
        return sensorDataBuffer.getAllData()
    }

    /**
     * Generates mock sensor data for testing purposes
     */
    private fun generateMockSensorData(counter: Int): SensorDataPacket {
        val baseTime = System.currentTimeMillis()
        
        // Generate realistic sensor values with some variation
        val ph = 7.0 + (Math.sin(counter * 0.1) * 2.0)
        val tds = 500.0 + (Math.cos(counter * 0.15) * 100.0)
        val temperature = 25.0 + (Math.sin(counter * 0.05) * 5.0)
        val uvAbsorbance = 0.5 + (Math.random() * 0.3)
        val moisture = 50.0 + (Math.sin(counter * 0.08) * 20.0)
        
        // Generate color data
        val red = (128 + Math.sin(counter * 0.1) * 127).toInt().coerceIn(0, 255)
        val green = (128 + Math.cos(counter * 0.12) * 127).toInt().coerceIn(0, 255)
        val blue = (128 + Math.sin(counter * 0.14) * 127).toInt().coerceIn(0, 255)
        
        val sensorReadings = SensorReadings(
            ph = ph.coerceIn(0.0, 14.0),
            tds = tds.coerceAtLeast(0.0),
            uvAbsorbance = uvAbsorbance.coerceAtLeast(0.0),
            temperature = temperature,
            colorRgb = ColorData(red, green, blue),
            moisturePercentage = moisture.coerceIn(0.0, 100.0)
        )
        
        // Generate electrode readings
        val electrodeReadings = ElectrodeReadings(
            platinum = Math.random() * 1000 - 500,
            silver = Math.random() * 800 - 400,
            silverChloride = Math.random() * 600 - 300,
            stainlessSteel = Math.random() * 900 - 450,
            copper = Math.random() * 700 - 350,
            carbon = Math.random() * 500 - 250,
            zinc = Math.random() * 800 - 400
        )
        
        return SensorDataPacket(
            timestamp = baseTime,
            deviceId = "ESP32_MOCK_001",
            sensorReadings = sensorReadings,
            electrodeReadings = electrodeReadings
        )
    }
}