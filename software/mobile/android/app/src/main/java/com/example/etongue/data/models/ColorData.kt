package com.example.etongue.data.models

import kotlinx.serialization.Serializable

/**
 * Represents color data with RGB values and hex conversion utilities
 */
@Serializable
data class ColorData(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    /**
     * Converts RGB values to hex string representation
     */
    val hex: String
        get() = String.format("#%02X%02X%02X", red, green, blue)
    
    /**
     * Validates that RGB values are within valid range (0-255)
     */
    fun isValid(): Boolean {
        return red in 0..255 && green in 0..255 && blue in 0..255
    }
    
    companion object {
        /**
         * Creates ColorData from hex string
         * @param hex Hex color string (e.g., "#FF0000" or "FF0000")
         * @return ColorData instance or null if invalid hex
         */
        fun fromHex(hex: String): ColorData? {
            val cleanHex = hex.removePrefix("#")
            if (cleanHex.length != 6) return null
            
            return try {
                val red = cleanHex.substring(0, 2).toInt(16)
                val green = cleanHex.substring(2, 4).toInt(16)
                val blue = cleanHex.substring(4, 6).toInt(16)
                ColorData(red, green, blue)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        /**
         * Creates a black color (0, 0, 0)
         */
        fun black() = ColorData(0, 0, 0)
        
        /**
         * Creates a white color (255, 255, 255)
         */
        fun white() = ColorData(255, 255, 255)
    }
}