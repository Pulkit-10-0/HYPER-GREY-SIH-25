package com.example.etongue.data.models

import kotlinx.serialization.Serializable

/**
 * Represents the type of connection to ESP32 device
 */
@Serializable
enum class ConnectionType {
    BLUETOOTH_LE,
    WIFI;
    
    /**
     * Returns a human-readable name for the connection type
     */
    fun getDisplayName(): String {
        return when (this) {
            BLUETOOTH_LE -> "Bluetooth LE"
            WIFI -> "WiFi"
        }
    }
}

/**
 * Represents the current connection status
 */
@Serializable
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR;
    
    /**
     * Returns a human-readable description of the connection status
     */
    fun getDisplayName(): String {
        return when (this) {
            DISCONNECTED -> "Disconnected"
            CONNECTING -> "Connecting..."
            CONNECTED -> "Connected"
            STREAMING -> "Streaming Data"
            ERROR -> "Connection Error"
        }
    }
    
    /**
     * Checks if the connection is in an active state
     */
    fun isActive(): Boolean {
        return this == CONNECTED || this == STREAMING
    }
    
    /**
     * Checks if the connection can start streaming
     */
    fun canStartStreaming(): Boolean {
        return this == CONNECTED
    }
}

/**
 * Represents the current streaming status
 */
@Serializable
enum class StreamingStatus {
    IDLE,
    STARTING,
    STREAMING,
    STOPPING,
    PAUSED,
    ERROR;
    
    /**
     * Returns a human-readable description of the streaming status
     */
    fun getDisplayName(): String {
        return when (this) {
            IDLE -> "Ready to Stream"
            STARTING -> "Starting Stream..."
            STREAMING -> "Streaming Active"
            STOPPING -> "Stopping Stream..."
            PAUSED -> "Stream Paused"
            ERROR -> "Streaming Error"
        }
    }
    
    /**
     * Checks if streaming is currently active
     */
    fun isActive(): Boolean {
        return this == STREAMING
    }
    
    /**
     * Checks if streaming can be started
     */
    fun canStart(): Boolean {
        return this == IDLE || this == PAUSED
    }
    
    /**
     * Checks if streaming can be stopped
     */
    fun canStop(): Boolean {
        return this == STREAMING || this == STARTING
    }
}