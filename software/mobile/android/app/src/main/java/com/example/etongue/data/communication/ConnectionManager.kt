package com.example.etongue.data.communication

import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.models.SensorDataPacket
import kotlinx.coroutines.flow.Flow

/**
 * Abstract interface for managing connections to ESP32 devices
 * Provides common functionality for both BLE and WiFi connections
 */
abstract class ConnectionManager {
    
    /**
     * Scans for available ESP32 devices
     * @return Flow of discovered devices
     */
    abstract suspend fun scanForDevices(): Flow<List<ESP32Device>>
    
    /**
     * Connects to the specified ESP32 device
     * @param device The device to connect to
     * @return Result indicating success or failure with error details
     */
    abstract suspend fun connect(device: ESP32Device): Result<Unit>
    
    /**
     * Disconnects from the currently connected device
     * @return Result indicating success or failure
     */
    abstract suspend fun disconnect(): Result<Unit>
    
    /**
     * Starts streaming sensor data from the connected device
     * @return Flow of sensor data packets
     */
    abstract suspend fun startStreaming(): Flow<SensorDataPacket>
    
    /**
     * Stops streaming sensor data
     * @return Result indicating success or failure
     */
    abstract suspend fun stopStreaming(): Result<Unit>
    
    /**
     * Gets the current connection status
     * @return Flow of connection status updates
     */
    abstract fun getConnectionStatus(): Flow<ConnectionStatus>
    
    /**
     * Gets the currently connected device
     * @return The connected device or null if not connected
     */
    abstract fun getConnectedDevice(): ESP32Device?
    
    /**
     * Sends a command to the connected device
     * @param command The command string to send
     * @return Result indicating success or failure
     */
    abstract suspend fun sendCommand(command: String): Result<Unit>
    
    /**
     * Checks if the connection is currently active
     * @return true if connected and ready for communication
     */
    open fun isConnected(): Boolean {
        return getConnectedDevice() != null
    }
    
    /**
     * Validates that a device is compatible with this connection manager
     * @param device The device to validate
     * @return true if the device is compatible
     */
    abstract fun isDeviceCompatible(device: ESP32Device): Boolean
}