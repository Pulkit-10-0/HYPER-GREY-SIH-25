package com.example.etongue.utils

import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.communication.DataPacketParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Directly loads hardcoded sensor data, simulating an already-connected ESP32
 * This bypasses all connection management and assumes the ESP32 is connected externally
 */
object DirectDataLoader {
    
    private val dataPacketParser = DataPacketParser()
    
    /**
     * Simulates real-time sensor data streaming from a connected ESP32
     * Uses the hardcoded real sensor data
     */
    fun startDataStream(): Flow<SensorDataPacket> = flow {
        val sensorDataList = HardcodedSensorData.getRealSensorDataList()
        
        if (sensorDataList.isEmpty()) {
            return@flow
        }
        
        var currentIndex = 0
        
        while (true) {
            val jsonData = sensorDataList[currentIndex % sensorDataList.size]
            
            // Parse the JSON data into a SensorDataPacket
            val parseResult = dataPacketParser.parseDataPacket(jsonData, "esp32-direct")
            
            parseResult.getOrNull()?.let { packet ->
                emit(packet)
            }
            
            currentIndex++
            
            // Send data every 2 seconds (realistic ESP32 interval)
            delay(2000)
        }
    }
    
    /**
     * Gets a single sensor data reading
     */
    fun getSingleReading(): SensorDataPacket? {
        val jsonData = HardcodedSensorData.getRandomSensorData()
        val parseResult = dataPacketParser.parseDataPacket(jsonData, "esp32-direct")
        return parseResult.getOrNull()
    }
    
    /**
     * Checks if ESP32 data is available (always true since we have hardcoded data)
     */
    fun isDataAvailable(): Boolean = true
    
    /**
     * Gets status information
     */
    fun getStatus(): String {
        val dataCount = HardcodedSensorData.getRealSensorDataList().size
        return "ESP32 Connected - $dataCount sensor readings available"
    }
}