package com.example.etongue.data.streaming

import com.example.etongue.data.models.SensorDataPacket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe buffer for storing sensor data packets during streaming
 */
class SensorDataBuffer(
    private val maxBufferSize: Int = 1000
) {
    private val buffer = mutableListOf<SensorDataPacket>()
    private val mutex = Mutex()

    /**
     * Adds a new sensor data packet to the buffer
     * If buffer is full, removes the oldest entry
     */
    suspend fun addData(data: SensorDataPacket) {
        mutex.withLock {
            if (buffer.size >= maxBufferSize) {
                buffer.removeAt(0) // Remove oldest entry
            }
            buffer.add(data)
        }
    }

    /**
     * Gets all data from the buffer
     */
    suspend fun getAllData(): List<SensorDataPacket> {
        return mutex.withLock {
            buffer.toList()
        }
    }

    /**
     * Gets the most recent data packet
     */
    suspend fun getLatestData(): SensorDataPacket? {
        return mutex.withLock {
            buffer.lastOrNull()
        }
    }

    /**
     * Gets the current buffer size
     */
    fun getBufferSize(): Int {
        return buffer.size
    }

    /**
     * Clears all data from the buffer
     */
    suspend fun clear() {
        mutex.withLock {
            buffer.clear()
        }
    }

    /**
     * Gets data from a specific time range
     */
    suspend fun getDataInRange(startTime: Long, endTime: Long): List<SensorDataPacket> {
        return mutex.withLock {
            buffer.filter { it.timestamp in startTime..endTime }
        }
    }
}