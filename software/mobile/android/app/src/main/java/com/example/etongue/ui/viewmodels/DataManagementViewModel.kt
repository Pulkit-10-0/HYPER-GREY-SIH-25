package com.example.etongue.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.etongue.data.models.*
import com.example.etongue.data.repository.SensorDataRepository
import com.example.etongue.domain.usecases.SaveSensorDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for managing data persistence and file operations
 * Handles saving, loading, deleting, and organizing sensor data files
 */
class DataManagementViewModel(
    private val sensorDataRepository: SensorDataRepository,
    private val saveSensorDataUseCase: SaveSensorDataUseCase
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()
    
    // File list state
    private val _files = MutableStateFlow<List<DataFile>>(emptyList())
    val files: StateFlow<List<DataFile>> = _files.asStateFlow()
    
    // Selected file state
    private val _selectedFile = MutableStateFlow<DataFile?>(null)
    val selectedFile: StateFlow<DataFile?> = _selectedFile.asStateFlow()
    
    // Storage info state
    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()
    
    init {
        loadFiles()
        updateStorageInfo()
    }
    
    /**
     * Saves current sensor data session
     */
    fun saveCurrentSession(
        deviceInfo: ESP32Device,
        sampleType: String? = null,
        testConditions: String? = null,
        operatorNotes: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val metadata = saveSensorDataUseCase.createSessionMetadata(
                    sampleType = sampleType,
                    testConditions = testConditions,
                    operatorNotes = operatorNotes
                )
                
                val result = saveSensorDataUseCase.saveCurrentSession(deviceInfo, metadata)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Data saved successfully: ${result.getOrNull()}"
                    )
                    loadFiles() // Refresh file list
                    updateStorageInfo()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to save data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    /**
     * Loads all saved data files
     */
    fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val fileList = sensorDataRepository.loadSavedFiles()
                _files.value = fileList.sortedByDescending { it.createdAt }
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load files"
                )
            }
        }
    }
    
    /**
     * Deletes a specific file
     */
    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val result = sensorDataRepository.deleteFile(fileName)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "File deleted successfully"
                    )
                    loadFiles() // Refresh file list
                    updateStorageInfo()
                    
                    // Clear selected file if it was deleted
                    if (_selectedFile.value?.fileName == fileName) {
                        _selectedFile.value = null
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to delete file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    /**
     * Loads a specific data file for viewing
     */
    fun loadDataFile(fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val result = sensorDataRepository.loadDataFile(fileName)
                
                if (result.isSuccess) {
                    val dataBatch = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadedData = dataBatch
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to load file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    /**
     * Selects a file for operations
     */
    fun selectFile(file: DataFile) {
        _selectedFile.value = file
    }
    
    /**
     * Clears the selected file
     */
    fun clearSelection() {
        _selectedFile.value = null
    }
    
    /**
     * Updates storage information
     */
    private fun updateStorageInfo() {
        viewModelScope.launch {
            try {
                val files = _files.value
                val totalSize = files.sumOf { it.fileSize }
                val fileCount = files.size
                
                _storageInfo.value = StorageInfo(
                    totalFiles = fileCount,
                    totalSize = totalSize,
                    formattedSize = formatFileSize(totalSize)
                )
            } catch (e: Exception) {
                // Handle storage info update error silently
            }
        }
    }
    
    /**
     * Formats file size for display
     */
    private fun formatFileSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            sizeInBytes < 1024 * 1024 * 1024 -> "${sizeInBytes / (1024 * 1024)} MB"
            else -> "${sizeInBytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * Formats timestamp for display
     */
    fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Clears any error or success messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
    
    /**
     * Generates a filename with timestamp
     */
    fun generateFileName(prefix: String = "sensor_data"): String {
        val timestamp = System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "${prefix}_${formatter.format(Date(timestamp))}.json"
    }
    
    /**
     * Exports a data file for sharing
     */
    fun exportFile(fileName: String): Result<String> {
        return try {
            val file = _files.value.find { it.fileName == fileName }
            if (file != null) {
                Result.success(file.filePath)
            } else {
                Result.failure(Exception("File not found: $fileName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * UI state for data management screen
 */
data class DataManagementUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val loadedData: SensorDataBatch? = null
)

/**
 * Storage information for display
 */
data class StorageInfo(
    val totalFiles: Int = 0,
    val totalSize: Long = 0L,
    val formattedSize: String = "0 B"
)