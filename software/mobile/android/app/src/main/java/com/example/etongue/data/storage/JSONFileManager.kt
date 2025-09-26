package com.example.etongue.data.storage

import android.content.Context
import android.os.StatFs
import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.DataFile
import com.example.etongue.data.serialization.DataSerializer
import com.example.etongue.domain.errors.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages JSON file operations for sensor data in internal storage
 * Handles saving, loading, listing, and deleting sensor data files
 */
class JSONFileManager(private val context: Context) {
    
    companion object {
        private const val DATA_DIRECTORY = "sensor_data"
        private const val FILE_EXTENSION = ".json"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB limit per file
    }
    
    private val dateFormatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    
    /**
     * Gets the directory where sensor data files are stored
     * Creates the directory if it doesn't exist
     */
    private fun getDataDirectory(): File {
        val dataDir = File(context.filesDir, DATA_DIRECTORY)
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        return dataDir
    }
    
    /**
     * Generates a filename for a sensor data batch
     * Format: sensor_data_YYYYMMDD_HHMMSS_sessionId.json
     */
    private fun generateFileName(batch: SensorDataBatch): String {
        val timestamp = dateFormatter.format(Date(batch.startTime))
        val sessionIdShort = batch.sessionId.take(8) // Use first 8 chars of session ID
        return "sensor_data_${timestamp}_${sessionIdShort}$FILE_EXTENSION"
    }
    
    /**
     * Saves a SensorDataBatch to a JSON file in internal storage
     * @param batch The sensor data batch to save
     * @param customFileName Optional custom filename (without extension)
     * @return Result containing the saved file path or error
     */
    suspend fun saveBatch(
        batch: SensorDataBatch, 
        customFileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate batch before saving
            if (!batch.isValid()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid sensor data batch")
                )
            }
            
            val fileName = customFileName?.let { "${it}$FILE_EXTENSION" } 
                ?: generateFileName(batch)
            val file = File(getDataDirectory(), fileName)
            
            // Serialize the batch to JSON
            val jsonString = DataSerializer.serializeBatch(batch)
            
            // Check storage space before saving
            val requiredSpace = jsonString.length.toLong()
            val availableSpace = getAvailableStorage()
            
            if (requiredSpace > availableSpace) {
                return@withContext Result.failure(
                    InsufficientStorageException(requiredSpace, availableSpace)
                )
            }
            
            // Check file size limit
            if (jsonString.length > MAX_FILE_SIZE) {
                return@withContext Result.failure(
                    StorageQuotaExceededException(jsonString.length.toLong(), MAX_FILE_SIZE.toLong())
                )
            }
            
            // Write to file
            file.writeText(jsonString)
            
            Result.success(file.absolutePath)
            
        } catch (e: SecurityException) {
            Result.failure(StoragePermissionException("save", e))
        } catch (e: IOException) {
            Result.failure(StorageErrorHandler.handleStorageError("save", customFileName, e))
        } catch (e: Exception) {
            Result.failure(StorageErrorHandler.handleStorageError("save", customFileName, e))
        }
    }
    
    /**
     * Loads a SensorDataBatch from a JSON file
     * @param fileName The name of the file to load
     * @return Result containing the loaded batch or error
     */
    suspend fun loadBatch(fileName: String): Result<SensorDataBatch> = withContext(Dispatchers.IO) {
        try {
            val file = File(getDataDirectory(), fileName)
            
            if (!file.exists()) {
                return@withContext Result.failure(
                    FileNotFoundException(fileName)
                )
            }
            
            val jsonString = file.readText()
            val batch = DataSerializer.deserializeBatch(jsonString)
            
            Result.success(batch)
            
        } catch (e: SecurityException) {
            Result.failure(StoragePermissionException("load", e))
        } catch (e: FileNotFoundException) {
            Result.failure(FileNotFoundException(fileName, e))
        } catch (e: IOException) {
            Result.failure(FileOperationException("load", fileName, e))
        } catch (e: Exception) {
            // Likely a JSON parsing error
            Result.failure(InvalidFileFormatException(fileName, "JSON", e))
        }
    }
    
    /**
     * Lists all saved sensor data files
     * @return Result containing list of DataFile objects or error
     */
    suspend fun listFiles(): Result<List<DataFile>> = withContext(Dispatchers.IO) {
        try {
            val dataDir = getDataDirectory()
            val files = dataDir.listFiles { file ->
                file.isFile && file.name.endsWith(FILE_EXTENSION)
            } ?: emptyArray()
            
            val dataFiles = files.map { file ->
                val dataPointCount = try {
                    // Try to get data point count without fully parsing the file
                    val jsonString = file.readText()
                    val batch = DataSerializer.deserializeBatch(jsonString)
                    batch.dataPointCount
                } catch (e: Exception) {
                    0 // Default to 0 if we can't parse the file
                }
                
                DataFile(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    createdAt = file.lastModified(),
                    fileSize = file.length(),
                    dataPointCount = dataPointCount
                )
            }.sortedByDescending { it.createdAt } // Sort by creation time, newest first
            
            Result.success(dataFiles)
            
        } catch (e: Exception) {
            Result.failure(Exception("Failed to list files: ${e.message}", e))
        }
    }
    
    /**
     * Deletes a sensor data file
     * @param fileName The name of the file to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(getDataDirectory(), fileName)
            
            if (!file.exists()) {
                return@withContext Result.failure(
                    FileNotFoundException(fileName)
                )
            }
            
            val deleted = file.delete()
            if (deleted) {
                Result.success(Unit)
            } else {
                Result.failure(FileOperationException("delete", fileName))
            }
            
        } catch (e: SecurityException) {
            Result.failure(StoragePermissionException("delete", e))
        } catch (e: Exception) {
            Result.failure(StorageErrorHandler.handleStorageError("delete", fileName, e))
        }
    }
    
    /**
     * Gets information about a specific file without loading its contents
     * @param fileName The name of the file
     * @return Result containing DataFile info or error
     */
    suspend fun getFileInfo(fileName: String): Result<DataFile> = withContext(Dispatchers.IO) {
        try {
            val file = File(getDataDirectory(), fileName)
            
            if (!file.exists()) {
                return@withContext Result.failure(
                    FileNotFoundException("File not found: $fileName")
                )
            }
            
            val dataFile = DataFile(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAt = file.lastModified(),
                fileSize = file.length(),
                dataPointCount = 0 // We don't parse the file for this method
            )
            
            Result.success(dataFile)
            
        } catch (e: Exception) {
            Result.failure(Exception("Error getting file info: ${e.message}", e))
        }
    }
    
    /**
     * Checks if a file exists
     * @param fileName The name of the file to check
     * @return true if file exists, false otherwise
     */
    fun fileExists(fileName: String): Boolean {
        val file = File(getDataDirectory(), fileName)
        return file.exists()
    }
    
    /**
     * Gets the total size of all sensor data files
     * @return Total size in bytes
     */
    suspend fun getTotalStorageUsed(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val dataDir = getDataDirectory()
            val files = dataDir.listFiles { file ->
                file.isFile && file.name.endsWith(FILE_EXTENSION)
            } ?: emptyArray()
            
            val totalSize = files.sumOf { it.length() }
            Result.success(totalSize)
            
        } catch (e: Exception) {
            Result.failure(Exception("Error calculating storage usage: ${e.message}", e))
        }
    }
    
    /**
     * Gets available storage space in the app's internal directory
     * @return Available space in bytes
     */
    fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBytes
        } catch (e: Exception) {
            context.filesDir.freeSpace
        }
    }
    
    /**
     * Checks if there's sufficient storage space for a given size
     * @param requiredBytes The number of bytes needed
     * @return true if sufficient space is available
     */
    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        val availableSpace = getAvailableStorage()
        val bufferSpace = 10 * 1024 * 1024 // Keep 10MB buffer
        return availableSpace > (requiredBytes + bufferSpace)
    }
    
    /**
     * Validates storage permissions and space before operations
     * @param requiredSpace The space needed for the operation
     * @return Result indicating if storage is ready for operations
     */
    suspend fun validateStorageReadiness(requiredSpace: Long = 0): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dataDir = getDataDirectory()
            
            // Check if we can write to the directory
            if (!dataDir.canWrite()) {
                return@withContext Result.failure(
                    StoragePermissionException("write access to data directory")
                )
            }
            
            // Check available space if required
            if (requiredSpace > 0 && !hasSufficientStorage(requiredSpace)) {
                val availableSpace = getAvailableStorage()
                return@withContext Result.failure(
                    InsufficientStorageException(requiredSpace, availableSpace)
                )
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(StorageErrorHandler.handleStorageError("validate storage", null, e))
        }
    }
    
    /**
     * Cleans up old files if storage is running low
     * Removes oldest files first until storage usage is below threshold
     * @param maxStorageBytes Maximum storage to use for sensor data
     * @return Number of files deleted
     */
    suspend fun cleanupOldFiles(maxStorageBytes: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val filesResult = listFiles()
            if (filesResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to list files for cleanup: ${filesResult.exceptionOrNull()?.message}")
                )
            }
            
            val files = filesResult.getOrNull() ?: emptyList()
            val totalSize = files.sumOf { it.fileSize }
            
            if (totalSize <= maxStorageBytes) {
                return@withContext Result.success(0) // No cleanup needed
            }
            
            // Sort by creation time, oldest first
            val sortedFiles = files.sortedBy { it.createdAt }
            var currentSize = totalSize
            var deletedCount = 0
            
            for (file in sortedFiles) {
                if (currentSize <= maxStorageBytes) break
                
                val deleteResult = deleteFile(file.fileName)
                if (deleteResult.isSuccess) {
                    currentSize -= file.fileSize
                    deletedCount++
                }
            }
            
            Result.success(deletedCount)
            
        } catch (e: Exception) {
            Result.failure(Exception("Error during cleanup: ${e.message}", e))
        }
    }
}