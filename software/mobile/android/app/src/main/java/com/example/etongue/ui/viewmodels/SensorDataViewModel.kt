package com.example.etongue.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.SessionMetadata
import com.example.etongue.data.models.StreamingStatus
import com.example.etongue.data.repository.DeviceConnectionRepository
import com.example.etongue.data.repository.SensorDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for managing real-time sensor data display and streaming controls
 */
class SensorDataViewModel(
    private val sensorDataRepository: SensorDataRepository,
    private val deviceConnectionRepository: DeviceConnectionRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(SensorDataUiState())
    val uiState: StateFlow<SensorDataUiState> = _uiState.asStateFlow()

    // Current sensor data
    private val _currentSensorData = MutableStateFlow<SensorDataPacket?>(null)
    val currentSensorData: StateFlow<SensorDataPacket?> = _currentSensorData.asStateFlow()

    // Streaming status
    private val _streamingStatus = MutableStateFlow(StreamingStatus.IDLE)
    val streamingStatus: StateFlow<StreamingStatus> = _streamingStatus.asStateFlow()

    // Connection status
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Data timeout tracking
    private val _lastDataTimestamp = MutableStateFlow(0L)
    val lastDataTimestamp: StateFlow<Long> = _lastDataTimestamp.asStateFlow()

    init {
        observeConnectionStatus()
        // Auto-start direct data streaming since ESP32 is connected externally
        startDirectDataStreaming()
        observeStreamingStatus()
        checkDataTimeout()
    }

    /**
     * Starts sensor data streaming
     */
    fun startStreaming() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                _errorMessage.value = null

                sensorDataRepository.startStreaming()
                    .catch { exception ->
                        _errorMessage.value = "Streaming error: ${exception.message}"
                        _streamingStatus.value = StreamingStatus.ERROR
                    }
                    .collect { sensorData ->
                        _currentSensorData.value = sensorData
                        _lastDataTimestamp.value = System.currentTimeMillis()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasReceivedData = true
                        )
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start streaming: ${e.message}"
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Stops sensor data streaming
     */
    fun stopStreaming() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                sensorDataRepository.stopStreaming()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to stop streaming: ${e.message}"
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Clears the current error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Observes connection status from the device repository
     */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            deviceConnectionRepository.getConnectionStatus()
                .collect { status ->
                    _connectionStatus.value = status
                }
        }
    }

    /**
     * Observes streaming status from the sensor repository
     */
    private fun observeStreamingStatus() {
        viewModelScope.launch {
            sensorDataRepository.getStreamingStatus()
                .collect { status ->
                    _streamingStatus.value = status
                }
        }
    }

    /**
     * Checks for data timeout (no data received for 5 seconds)
     */
    private fun checkDataTimeout() {
        viewModelScope.launch {
            combine(
                _lastDataTimestamp,
                _streamingStatus
            ) { lastTimestamp, streamingStatus ->
                if (streamingStatus == StreamingStatus.STREAMING && lastTimestamp > 0) {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastData = currentTime - lastTimestamp
                    
                    _uiState.value = _uiState.value.copy(
                        isDataTimeout = timeSinceLastData > DATA_TIMEOUT_MS
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isDataTimeout = false)
                }
            }.collect { }
        }
    }

    /**
     * Starts direct data streaming using hardcoded sensor data
     * This simulates ESP32 connected externally
     */
    private fun startDirectDataStreaming() {
        viewModelScope.launch {
            try {
                _streamingStatus.value = StreamingStatus.STREAMING
                _connectionStatus.value = ConnectionStatus.CONNECTED
                
                com.example.etongue.utils.DirectDataLoader.startDataStream()
                    .catch { error ->
                        _errorMessage.value = "Data streaming error: ${error.message}"
                        _streamingStatus.value = StreamingStatus.ERROR
                    }
                    .collect { sensorData ->
                        _currentSensorData.value = sensorData
                        _lastDataTimestamp.value = System.currentTimeMillis()
                        
                        _uiState.value = _uiState.value.copy(
                            hasReceivedData = true,
                            isDataTimeout = false
                        )
                        
                        // Save the data (create a batch with single packet)
                        val deviceInfo = ESP32Device(
                            id = sensorData.deviceId,
                            name = "Direct ESP32 Connection",
                            macAddress = "Unknown",
                            signalStrength = -30,
                            connectionType = ConnectionType.WIFI
                        )
                        val metadata = SessionMetadata(
                            sampleType = "Direct Stream Sample",
                            operatorNotes = "Real-time data from ESP32"
                        )
                        val batch = SensorDataBatch(
                            sessionId = "direct-stream-${System.currentTimeMillis()}",
                            startTime = sensorData.timestamp,
                            endTime = sensorData.timestamp,
                            deviceInfo = deviceInfo,
                            dataPoints = listOf(sensorData),
                            metadata = metadata
                        )
                        sensorDataRepository.saveData(batch)
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start data streaming: ${e.message}"
                _streamingStatus.value = StreamingStatus.ERROR
            }
        }
    }
    
    /**
     * Manually refresh data (get a single reading)
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                val sensorData = com.example.etongue.utils.DirectDataLoader.getSingleReading()
                if (sensorData != null) {
                    _currentSensorData.value = sensorData
                    _lastDataTimestamp.value = System.currentTimeMillis()
                    
                    _uiState.value = _uiState.value.copy(
                        hasReceivedData = true,
                        isDataTimeout = false
                    )
                    
                    // Save the data (create a batch with single packet)
                    val deviceInfo = ESP32Device(
                        id = sensorData.deviceId,
                        name = "Direct ESP32 Connection",
                        macAddress = "Unknown",
                        signalStrength = -30,
                        connectionType = ConnectionType.WIFI
                    )
                    val metadata = SessionMetadata(
                        sampleType = "Manual Refresh Sample",
                        operatorNotes = "Single reading from ESP32"
                    )
                    val batch = SensorDataBatch(
                        sessionId = "manual-refresh-${System.currentTimeMillis()}",
                        startTime = sensorData.timestamp,
                        endTime = sensorData.timestamp,
                        deviceInfo = deviceInfo,
                        dataPoints = listOf(sensorData),
                        metadata = metadata
                    )
                    sensorDataRepository.saveData(batch)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh data: ${e.message}"
            }
        }
    }

    companion object {
        private const val DATA_TIMEOUT_MS = 5000L // 5 seconds
    }
}

/**
 * UI state for the sensor data screen
 */
data class SensorDataUiState(
    val isLoading: Boolean = false,
    val hasReceivedData: Boolean = false,
    val isDataTimeout: Boolean = false
)