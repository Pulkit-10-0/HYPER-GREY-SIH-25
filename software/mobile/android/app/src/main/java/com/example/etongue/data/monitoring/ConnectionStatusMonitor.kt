package com.example.etongue.data.monitoring

import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.repository.DeviceConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors connection health and handles automatic reconnection
 */
class ConnectionStatusMonitor(
    private val deviceConnectionRepository: DeviceConnectionRepository
) {
    
    companion object {
        private const val HEALTH_CHECK_INTERVAL = 10_000L // 10 seconds
        private const val CONNECTION_TIMEOUT = 30_000L // 30 seconds
        private const val MAX_RECONNECTION_ATTEMPTS = 3
        private const val RECONNECTION_DELAY = 5_000L // 5 seconds
    }
    
    private val monitoringScope = CoroutineScope(Dispatchers.IO)
    private var monitoringJob: Job? = null
    private var reconnectionJob: Job? = null
    
    private val _connectionHealth = MutableStateFlow(ConnectionHealth.UNKNOWN)
    val connectionHealth: StateFlow<ConnectionHealth> = _connectionHealth.asStateFlow()
    
    private val _reconnectionAttempts = MutableStateFlow(0)
    val reconnectionAttempts: StateFlow<Int> = _reconnectionAttempts.asStateFlow()
    
    private val _lastHealthCheck = MutableStateFlow(0L)
    val lastHealthCheck: StateFlow<Long> = _lastHealthCheck.asStateFlow()
    
    private var isMonitoring = false
    private var lastSuccessfulConnection = 0L
    
    /**
     * Starts monitoring the connection health
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = monitoringScope.launch {
            while (isActive && isMonitoring) {
                performHealthCheck()
                delay(HEALTH_CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * Stops monitoring the connection health
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        reconnectionJob?.cancel()
        _connectionHealth.value = ConnectionHealth.UNKNOWN
        _reconnectionAttempts.value = 0
    }
    
    /**
     * Performs a health check on the current connection
     */
    private suspend fun performHealthCheck() {
        try {
            val currentTime = System.currentTimeMillis()
            _lastHealthCheck.value = currentTime
            
            val connectedDevice = deviceConnectionRepository.getConnectedDevice()
            val connectionStatus = deviceConnectionRepository.getConnectionStatus()
            
            // Collect current connection status
            connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> {
                        handleHealthyConnection(connectedDevice, currentTime)
                    }
                    ConnectionStatus.CONNECTING -> {
                        handleConnectingState(currentTime)
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        handleDisconnectedState(connectedDevice)
                    }
                    ConnectionStatus.ERROR -> {
                        handleErrorState(connectedDevice)
                    }
                }
            }
        } catch (e: Exception) {
            _connectionHealth.value = ConnectionHealth.UNHEALTHY
        }
    }
    
    /**
     * Handles healthy connection state
     */
    private suspend fun handleHealthyConnection(device: ESP32Device?, currentTime: Long) {
        if (device != null) {
            // Check if device is still reachable
            val isReachable = deviceConnectionRepository.isDeviceReachable(device)
            
            if (isReachable) {
                _connectionHealth.value = ConnectionHealth.HEALTHY
                lastSuccessfulConnection = currentTime
                _reconnectionAttempts.value = 0
                
                // Cancel any ongoing reconnection attempts
                reconnectionJob?.cancel()
            } else {
                _connectionHealth.value = ConnectionHealth.UNHEALTHY
                initiateReconnection(device)
            }
        } else {
            _connectionHealth.value = ConnectionHealth.DISCONNECTED
        }
    }
    
    /**
     * Handles connecting state
     */
    private suspend fun handleConnectingState(currentTime: Long) {
        // Check if connection attempt is taking too long
        if (currentTime - lastSuccessfulConnection > CONNECTION_TIMEOUT) {
            _connectionHealth.value = ConnectionHealth.UNHEALTHY
            
            val device = deviceConnectionRepository.getConnectedDevice()
            if (device != null) {
                initiateReconnection(device)
            }
        } else {
            _connectionHealth.value = ConnectionHealth.CONNECTING
        }
    }
    
    /**
     * Handles disconnected state
     */
    private suspend fun handleDisconnectedState(lastDevice: ESP32Device?) {
        _connectionHealth.value = ConnectionHealth.DISCONNECTED
        
        // If we had a device connected before, attempt reconnection
        if (lastDevice != null && _reconnectionAttempts.value < MAX_RECONNECTION_ATTEMPTS) {
            initiateReconnection(lastDevice)
        }
    }
    
    /**
     * Handles error state
     */
    private suspend fun handleErrorState(device: ESP32Device?) {
        _connectionHealth.value = ConnectionHealth.ERROR
        
        if (device != null && _reconnectionAttempts.value < MAX_RECONNECTION_ATTEMPTS) {
            initiateReconnection(device)
        }
    }
    
    /**
     * Initiates automatic reconnection
     */
    private fun initiateReconnection(device: ESP32Device) {
        // Cancel any existing reconnection job
        reconnectionJob?.cancel()
        
        reconnectionJob = monitoringScope.launch {
            if (_reconnectionAttempts.value >= MAX_RECONNECTION_ATTEMPTS) {
                _connectionHealth.value = ConnectionHealth.FAILED
                return@launch
            }
            
            _reconnectionAttempts.value += 1
            _connectionHealth.value = ConnectionHealth.RECONNECTING
            
            // Wait before attempting reconnection
            delay(RECONNECTION_DELAY)
            
            try {
                // Attempt to refresh the connection
                val result = deviceConnectionRepository.refreshConnection()
                
                if (result.isSuccess) {
                    _connectionHealth.value = ConnectionHealth.HEALTHY
                    _reconnectionAttempts.value = 0
                    lastSuccessfulConnection = System.currentTimeMillis()
                } else {
                    // If refresh failed, try full reconnection
                    val reconnectResult = deviceConnectionRepository.connectToDevice(device)
                    
                    if (reconnectResult.isSuccess) {
                        _connectionHealth.value = ConnectionHealth.HEALTHY
                        _reconnectionAttempts.value = 0
                        lastSuccessfulConnection = System.currentTimeMillis()
                    } else {
                        _connectionHealth.value = ConnectionHealth.UNHEALTHY
                        
                        // If we've reached max attempts, mark as failed
                        if (_reconnectionAttempts.value >= MAX_RECONNECTION_ATTEMPTS) {
                            _connectionHealth.value = ConnectionHealth.FAILED
                        }
                    }
                }
            } catch (e: Exception) {
                _connectionHealth.value = ConnectionHealth.UNHEALTHY
                
                if (_reconnectionAttempts.value >= MAX_RECONNECTION_ATTEMPTS) {
                    _connectionHealth.value = ConnectionHealth.FAILED
                }
            }
        }
    }
    
    /**
     * Manually triggers a reconnection attempt
     */
    suspend fun triggerReconnection(): Result<Unit> {
        return try {
            val device = deviceConnectionRepository.getConnectedDevice()
                ?: return Result.failure(Exception("No device to reconnect to"))
            
            _reconnectionAttempts.value = 0 // Reset attempts for manual trigger
            initiateReconnection(device)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Resets the connection health monitoring
     */
    fun resetMonitoring() {
        _connectionHealth.value = ConnectionHealth.UNKNOWN
        _reconnectionAttempts.value = 0
        _lastHealthCheck.value = 0L
        lastSuccessfulConnection = System.currentTimeMillis()
    }
    
    /**
     * Gets connection statistics
     */
    fun getConnectionStats(): ConnectionStats {
        return ConnectionStats(
            health = _connectionHealth.value,
            reconnectionAttempts = _reconnectionAttempts.value,
            lastHealthCheck = _lastHealthCheck.value,
            lastSuccessfulConnection = lastSuccessfulConnection,
            isMonitoring = isMonitoring
        )
    }
}

/**
 * Represents the health status of the connection
 */
enum class ConnectionHealth {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY,
    CONNECTING,
    RECONNECTING,
    DISCONNECTED,
    ERROR,
    FAILED;
    
    fun getDisplayName(): String {
        return when (this) {
            UNKNOWN -> "Unknown"
            HEALTHY -> "Healthy"
            UNHEALTHY -> "Unhealthy"
            CONNECTING -> "Connecting"
            RECONNECTING -> "Reconnecting"
            DISCONNECTED -> "Disconnected"
            ERROR -> "Error"
            FAILED -> "Failed"
        }
    }
    
    fun isActive(): Boolean {
        return this == HEALTHY || this == CONNECTING || this == RECONNECTING
    }
}

/**
 * Connection statistics data class
 */
data class ConnectionStats(
    val health: ConnectionHealth,
    val reconnectionAttempts: Int,
    val lastHealthCheck: Long,
    val lastSuccessfulConnection: Long,
    val isMonitoring: Boolean
)