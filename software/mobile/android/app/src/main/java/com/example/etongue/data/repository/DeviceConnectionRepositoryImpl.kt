package com.example.etongue.data.repository

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.example.etongue.data.communication.BLEConnectionManager
import com.example.etongue.data.communication.ConnectionManager
import com.example.etongue.data.communication.WiFiConnectionManager
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

/**
 * Implementation of DeviceConnectionRepository that manages both BLE and WiFi connections
 */
class DeviceConnectionRepositoryImpl(
    private val context: Context,
    private val bleConnectionManager: BLEConnectionManager,
    private val wifiConnectionManager: WiFiConnectionManager
) : DeviceConnectionRepository {
    
    private var activeConnectionManager: ConnectionManager? = null
    private var connectedDevice: ESP32Device? = null
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    override suspend fun scanForDevices(): Flow<List<ESP32Device>> {
        // Combine results from both BLE and WiFi scanning
        val bleDevices = if (bleConnectionManager.isBluetoothEnabled() && bleConnectionManager.isBleSupported()) {
            try {
                bleConnectionManager.scanForDevices()
            } catch (e: Exception) {
                flowOf(emptyList())
            }
        } else {
            flowOf(emptyList())
        }
        
        val wifiDevices = try {
            wifiConnectionManager.scanForDevices()
        } catch (e: Exception) {
            flowOf(emptyList())
        }
        
        return combine(bleDevices, wifiDevices) { ble, wifi ->
            (ble + wifi).distinctBy { it.id }
        }
    }
    
    override suspend fun connectToDevice(device: ESP32Device): Result<ConnectionStatus> {
        return try {
            // Disconnect from any existing connection first
            disconnectFromDevice()
            
            // Select appropriate connection manager
            val connectionManager = when (device.connectionType) {
                ConnectionType.BLUETOOTH_LE -> {
                    if (!bleConnectionManager.isBluetoothEnabled()) {
                        return Result.failure(Exception("Bluetooth is not enabled"))
                    }
                    if (!bleConnectionManager.isBleSupported()) {
                        return Result.failure(Exception("Bluetooth LE is not supported on this device"))
                    }
                    bleConnectionManager
                }
                ConnectionType.WIFI -> {
                    wifiConnectionManager
                }
            }
            
            // Attempt connection
            val connectResult = connectionManager.connect(device)
            
            if (connectResult.isSuccess) {
                activeConnectionManager = connectionManager
                connectedDevice = device
                
                // Start monitoring connection status in the background
                startConnectionStatusMonitoring(connectionManager)
                
                Result.success(ConnectionStatus.CONNECTED)
            } else {
                Result.failure(connectResult.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun disconnectFromDevice(): Result<Unit> {
        return try {
            val result = activeConnectionManager?.disconnect() ?: Result.success(Unit)
            
            if (result.isSuccess) {
                activeConnectionManager = null
                connectedDevice = null
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getConnectionStatus(): Flow<ConnectionStatus> {
        return activeConnectionManager?.getConnectionStatus() ?: flowOf(ConnectionStatus.DISCONNECTED)
    }
    
    override fun getConnectedDevice(): ESP32Device? = connectedDevice
    
    override suspend fun pairWithDevice(device: ESP32Device): Result<Unit> {
        return when (device.connectionType) {
            ConnectionType.BLUETOOTH_LE -> {
                try {
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
                    
                    if (bluetoothDevice != null) {
                        // For BLE devices, pairing is typically handled automatically during connection
                        // Some devices may require explicit pairing
                        val bondState = bluetoothDevice.bondState
                        
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Result.success(Unit)
                            }
                            BluetoothDevice.BOND_NONE -> {
                                // Attempt to create bond
                                val pairResult = bluetoothDevice.createBond()
                                if (pairResult) {
                                    Result.success(Unit)
                                } else {
                                    Result.failure(Exception("Failed to initiate pairing"))
                                }
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Result.failure(Exception("Device is currently pairing"))
                            }
                            else -> {
                                Result.failure(Exception("Unknown bond state"))
                            }
                        }
                    } else {
                        Result.failure(Exception("Bluetooth device not found"))
                    }
                } catch (e: SecurityException) {
                    Result.failure(Exception("Bluetooth permission not granted"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            ConnectionType.WIFI -> {
                // WiFi devices typically don't require pairing
                Result.success(Unit)
            }
        }
    }
    
    override suspend fun authenticateDevice(device: ESP32Device, authToken: String?): Result<Unit> {
        return try {
            val connectionManager = activeConnectionManager
                ?: return Result.failure(Exception("No active connection"))
            
            // Send authentication command
            val authCommand = if (authToken != null) {
                "AUTH:$authToken"
            } else {
                "AUTH:DEFAULT"
            }
            
            val result = connectionManager.sendCommand(authCommand)
            
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun isDeviceReachable(device: ESP32Device): Boolean {
        return try {
            when (device.connectionType) {
                ConnectionType.BLUETOOTH_LE -> {
                    // For BLE, check if device is in range by attempting a quick scan
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
                    bluetoothDevice != null && bleConnectionManager.isBluetoothEnabled()
                }
                ConnectionType.WIFI -> {
                    // For WiFi, ping the device
                    wifiConnectionManager.pingDevice(device)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getSignalStrength(): Int? {
        return connectedDevice?.signalStrength
    }
    
    override suspend fun refreshConnection(): Result<Unit> {
        return try {
            val device = connectedDevice
                ?: return Result.failure(Exception("No device connected"))
            
            val connectionManager = activeConnectionManager
                ?: return Result.failure(Exception("No active connection manager"))
            
            // Check if device is still reachable
            if (!isDeviceReachable(device)) {
                return Result.failure(Exception("Device is not reachable"))
            }
            
            // If connection is lost, attempt to reconnect
            val currentStatus = _connectionStatus.value
            if (currentStatus == ConnectionStatus.DISCONNECTED || currentStatus == ConnectionStatus.ERROR) {
                connectToDevice(device).map { Unit }
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Starts monitoring connection status from the active connection manager
     */
    private fun startConnectionStatusMonitoring(connectionManager: ConnectionManager) {
        // Use a separate coroutine to monitor connection status
        CoroutineScope(Dispatchers.IO).launch {
            connectionManager.getConnectionStatus().collect { status ->
                _connectionStatus.value = status
                
                // Handle disconnection
                if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
                    if (connectedDevice != null) {
                        // Connection was lost unexpectedly
                        connectedDevice = null
                        activeConnectionManager = null
                    }
                }
            }
        }
    }
    
    /**
     * Gets the appropriate connection manager for a device type
     */
    private fun getConnectionManagerForDevice(device: ESP32Device): ConnectionManager {
        return when (device.connectionType) {
            ConnectionType.BLUETOOTH_LE -> bleConnectionManager
            ConnectionType.WIFI -> wifiConnectionManager
        }
    }
    
    /**
     * Validates device compatibility with available connection methods
     */
    fun isDeviceSupported(device: ESP32Device): Boolean {
        return when (device.connectionType) {
            ConnectionType.BLUETOOTH_LE -> {
                bleConnectionManager.isBleSupported() && bleConnectionManager.isBluetoothEnabled()
            }
            ConnectionType.WIFI -> {
                true // WiFi is generally supported on all Android devices
            }
        }
    }
}