package com.example.etongue.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.repository.DeviceConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for managing device connection UI state and operations
 */
class ConnectionViewModel(
    private val deviceConnectionRepository: DeviceConnectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private var scanningJob: kotlinx.coroutines.Job? = null

    init {
        observeConnectionStatus()
    }

    /**
     * Starts scanning for available ESP32 devices
     */
    fun startScanning() {
        // Cancel any existing scanning
        scanningJob?.cancel()
        
        scanningJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                availableDevices = emptyList() // Clear previous results
            )

            try {
                // Add a small delay to show loading state
                delay(500)
                
                deviceConnectionRepository.scanForDevices()
                    .catch { error ->
                        _uiState.value = _uiState.value.copy(
                            isScanning = false,
                            errorMessage = "Failed to scan for devices: ${error.message}"
                        )
                    }
                    .collect { devices ->
                        _uiState.value = _uiState.value.copy(
                            availableDevices = devices
                        )
                        // Keep scanning indicator active for continuous scanning
                        // User can stop manually with stopScanning()
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Scanning error: ${e.message}"
                )
            }
        }
    }

    /**
     * Stops scanning for devices
     */
    fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    /**
     * Connects to the specified device
     */
    fun connectToDevice(device: ESP32Device) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedDevice = device,
                isConnecting = true,
                errorMessage = null
            )

            try {
                val result = deviceConnectionRepository.connectToDevice(device)
                result.fold(
                    onSuccess = { status ->
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            connectedDevice = device
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            selectedDevice = null,
                            errorMessage = "Failed to connect: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    selectedDevice = null,
                    errorMessage = "Connection error: ${e.message}"
                )
            }
        }
    }

    /**
     * Disconnects from the currently connected device
     */
    fun disconnectFromDevice() {
        viewModelScope.launch {
            try {
                val result = deviceConnectionRepository.disconnectFromDevice()
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            connectedDevice = null,
                            selectedDevice = null,
                            errorMessage = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to disconnect: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Disconnect error: ${e.message}"
                )
            }
        }
    }

    /**
     * Selects a device from the list
     */
    fun selectDevice(device: ESP32Device) {
        _uiState.value = _uiState.value.copy(selectedDevice = device)
    }

    /**
     * Clears the current error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Filters devices by connection type
     */
    fun filterDevicesByType(connectionType: ConnectionType?) {
        _uiState.value = _uiState.value.copy(selectedConnectionType = connectionType)
    }

    /**
     * Gets filtered devices based on selected connection type
     */
    fun getFilteredDevices(): List<ESP32Device> {
        val currentState = _uiState.value
        return if (currentState.selectedConnectionType != null) {
            currentState.availableDevices.filter { 
                it.connectionType == currentState.selectedConnectionType 
            }
        } else {
            currentState.availableDevices
        }
    }

    /**
     * Refreshes the connection for the currently connected device
     */
    fun refreshConnection() {
        viewModelScope.launch {
            try {
                val result = deviceConnectionRepository.refreshConnection()
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(errorMessage = null)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to refresh connection: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Refresh error: ${e.message}"
                )
            }
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            deviceConnectionRepository.getConnectionStatus()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Connection status error: ${error.message}"
                    )
                }
                .collect { status ->
                    _connectionStatus.value = status
                    
                    // Update UI state based on connection status
                    when (status) {
                        ConnectionStatus.DISCONNECTED -> {
                            _uiState.value = _uiState.value.copy(
                                connectedDevice = null,
                                isConnecting = false
                            )
                        }
                        ConnectionStatus.CONNECTING -> {
                            _uiState.value = _uiState.value.copy(isConnecting = true)
                        }
                        ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> {
                            _uiState.value = _uiState.value.copy(
                                isConnecting = false,
                                connectedDevice = deviceConnectionRepository.getConnectedDevice()
                            )
                        }
                        ConnectionStatus.ERROR -> {
                            _uiState.value = _uiState.value.copy(
                                isConnecting = false,
                                errorMessage = "Connection error occurred"
                            )
                        }
                    }
                }
        }
    }
    
    /**
     * Connects manually to a device using provided address
     */
    fun connectManually(connectionType: ConnectionType, address: String) {
        viewModelScope.launch {
            // Create a manual device entry
            val manualDevice = ESP32Device(
                id = address,
                name = when (connectionType) {
                    ConnectionType.WIFI -> "Manual WiFi Device ($address)"
                    ConnectionType.BLUETOOTH_LE -> "Manual BLE Device ($address)"
                },
                macAddress = if (connectionType == ConnectionType.BLUETOOTH_LE) address else "Unknown",
                signalStrength = -50, // Default signal strength
                connectionType = connectionType
            )
            
            // Add to available devices list so it shows up
            val currentDevices = _uiState.value.availableDevices.toMutableList()
            currentDevices.removeAll { it.id == address } // Remove if already exists
            currentDevices.add(0, manualDevice) // Add at top
            
            _uiState.value = _uiState.value.copy(
                availableDevices = currentDevices,
                selectedDevice = manualDevice
            )
            
            // Connect to the manual device
            connectToDevice(manualDevice)
        }
    }
}

/**
 * UI state for the connection screen
 */
data class ConnectionUiState(
    val availableDevices: List<ESP32Device> = emptyList(),
    val selectedDevice: ESP32Device? = null,
    val connectedDevice: ESP32Device? = null,
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val selectedConnectionType: ConnectionType? = null,
    val errorMessage: String? = null
)