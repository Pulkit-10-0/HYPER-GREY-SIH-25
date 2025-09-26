package com.example.etongue.data.repository

import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ESP32Device
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing device connections and discovery
 */
interface DeviceConnectionRepository {
    
    /**
     * Scans for available ESP32 devices using all available connection methods
     * @return Flow of discovered devices list
     */
    suspend fun scanForDevices(): Flow<List<ESP32Device>>
    
    /**
     * Connects to the specified ESP32 device
     * @param device The device to connect to
     * @return Result indicating success or failure with error details
     */
    suspend fun connectToDevice(device: ESP32Device): Result<ConnectionStatus>
    
    /**
     * Disconnects from the currently connected device
     * @return Result indicating success or failure
     */
    suspend fun disconnectFromDevice(): Result<Unit>
    
    /**
     * Gets the current connection status
     * @return Flow of connection status updates
     */
    fun getConnectionStatus(): Flow<ConnectionStatus>
    
    /**
     * Gets the currently connected device
     * @return The connected device or null if not connected
     */
    fun getConnectedDevice(): ESP32Device?
    
    /**
     * Pairs with a device (for BLE devices that require pairing)
     * @param device The device to pair with
     * @return Result indicating success or failure
     */
    suspend fun pairWithDevice(device: ESP32Device): Result<Unit>
    
    /**
     * Authenticates with a connected device
     * @param device The device to authenticate with
     * @param authToken Optional authentication token
     * @return Result indicating success or failure
     */
    suspend fun authenticateDevice(device: ESP32Device, authToken: String? = null): Result<Unit>
    
    /**
     * Checks if a device is reachable
     * @param device The device to check
     * @return true if the device is reachable
     */
    suspend fun isDeviceReachable(device: ESP32Device): Boolean
    
    /**
     * Gets the signal strength of the connected device
     * @return Signal strength in dBm or null if not connected
     */
    fun getSignalStrength(): Int?
    
    /**
     * Refreshes the connection to handle temporary disconnections
     * @return Result indicating success or failure
     */
    suspend fun refreshConnection(): Result<Unit>
}