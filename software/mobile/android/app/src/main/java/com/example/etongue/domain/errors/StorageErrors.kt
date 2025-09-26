package com.example.etongue.domain.errors

/**
 * Exception classes for storage-related errors
 */

/**
 * Base class for all storage-related exceptions
 */
open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when there is insufficient storage space
 */
class InsufficientStorageException(
    requiredSpace: Long,
    availableSpace: Long,
    cause: Throwable? = null
) : StorageException(
    "Insufficient storage space. Required: ${formatBytes(requiredSpace)}, Available: ${formatBytes(availableSpace)}",
    cause
) {
    companion object {
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}

/**
 * Thrown when file operations fail due to permission issues
 */
class StoragePermissionException(
    operation: String,
    cause: Throwable? = null
) : StorageException("Permission denied for storage operation: $operation", cause)

/**
 * Thrown when a file operation fails
 */
class FileOperationException(
    operation: String,
    fileName: String,
    cause: Throwable? = null
) : StorageException("Failed to $operation file: $fileName", cause)

/**
 * Thrown when file format is invalid or corrupted
 */
class InvalidFileFormatException(
    fileName: String,
    expectedFormat: String,
    cause: Throwable? = null
) : StorageException("Invalid file format for $fileName. Expected: $expectedFormat", cause)

/**
 * Thrown when trying to access a file that doesn't exist
 */
class FileNotFoundException(
    fileName: String,
    cause: Throwable? = null
) : StorageException("File not found: $fileName", cause)

/**
 * Thrown when storage quota is exceeded
 */
class StorageQuotaExceededException(
    currentUsage: Long,
    maxQuota: Long,
    cause: Throwable? = null
) : StorageException(
    "Storage quota exceeded. Current usage: ${formatBytes(currentUsage)}, Max quota: ${formatBytes(maxQuota)}",
    cause
) {
    companion object {
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}

/**
 * Utility class for storage error handling
 */
object StorageErrorHandler {
    
    /**
     * Converts generic exceptions to storage-specific exceptions
     */
    fun handleStorageError(operation: String, fileName: String? = null, cause: Throwable): StorageException {
        return when {
            cause.message?.contains("No space left", ignoreCase = true) == true -> {
                InsufficientStorageException(0L, 0L, cause)
            }
            cause.message?.contains("Permission denied", ignoreCase = true) == true -> {
                StoragePermissionException(operation, cause)
            }
            cause.message?.contains("File not found", ignoreCase = true) == true && fileName != null -> {
                FileNotFoundException(fileName, cause)
            }
            fileName != null -> {
                FileOperationException(operation, fileName, cause)
            }
            else -> {
                StorageException("Storage operation failed: $operation", cause)
            }
        }
    }
    
    /**
     * Checks if an exception is recoverable
     */
    fun isRecoverableError(exception: StorageException): Boolean {
        return when (exception) {
            is InsufficientStorageException -> false // User needs to free space
            is StoragePermissionException -> false // User needs to grant permissions
            is FileOperationException -> true // Can retry
            is InvalidFileFormatException -> false // File is corrupted
            is FileNotFoundException -> false // File doesn't exist
            is StorageQuotaExceededException -> false // User needs to clean up
            else -> true
        }
    }
    
    /**
     * Gets user-friendly error message
     */
    fun getUserFriendlyMessage(exception: StorageException): String {
        return when (exception) {
            is InsufficientStorageException -> 
                "Not enough storage space available. Please free up some space and try again."
            is StoragePermissionException -> 
                "Storage permission required. Please grant storage access in app settings."
            is FileOperationException -> 
                "File operation failed. Please try again."
            is InvalidFileFormatException -> 
                "The selected file is corrupted or in an invalid format."
            is FileNotFoundException -> 
                "The requested file could not be found."
            is StorageQuotaExceededException -> 
                "Storage quota exceeded. Please delete some old files to free up space."
            else -> 
                "A storage error occurred. Please try again."
        }
    }
}