package com.example.etongue.data.models

import kotlinx.serialization.Serializable

/**
 * Represents an ESP32 device that can be connected to
 */
@Serializable
data class ESP32Device(
    val id: String,
    val name: String,
    val macAddress: String,
    val signalStrength: Int,
    val connectionType: ConnectionType
) {
    /**
     * Returns a human-readable signal strength description
     */
    fun getSignalStrengthDescription(): String {
        return when {
            signalStrength >= -50 -> "Excellent"
            signalStrength >= -60 -> "Good"
            signalStrength >= -70 -> "Fair"
            signalStrength >= -80 -> "Weak"
            else -> "Very Weak"
        }
    }
    
    /**
     * Validates that the device has required fields
     */
    fun isValid(): Boolean {
        return id.isNotBlank() && 
               name.isNotBlank() && 
               macAddress.isNotBlank() &&
               macAddress.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    }
}