package com.example.etongue.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility class for handling runtime permissions
 */
object PermissionHandler {
    
    /**
     * Gets the required permissions for Bluetooth scanning
     */
    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    /**
     * Gets the required permissions for WiFi operations
     */
    fun getWiFiPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
    }
    
    /**
     * Gets the required permissions for file operations
     */
    fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * Checks if all Bluetooth permissions are granted
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return getBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Checks if all WiFi permissions are granted
     */
    fun hasWiFiPermissions(context: Context): Boolean {
        return getWiFiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Checks if all storage permissions are granted
     */
    fun hasStoragePermissions(context: Context): Boolean {
        return getStoragePermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Gets all required permissions for the app
     */
    fun getAllRequiredPermissions(): Array<String> {
        return getBluetoothPermissions() + getWiFiPermissions() + getStoragePermissions()
    }
    
    /**
     * Checks if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasBluetoothPermissions(context) && 
               hasWiFiPermissions(context) && 
               hasStoragePermissions(context)
    }
    
    /**
     * Gets the missing permissions
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getAllRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}