package com.example.etongue.domain.errors

/**
 * Sealed class representing different types of application errors
 */
sealed class AppError : Exception() {
    
    /**
     * Connection-related errors
     */
    data class ConnectionError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()
    
    /**
     * Data parsing errors
     */
    data class DataParsingError(
        val rawData: String,
        override val cause: Throwable? = null
    ) : AppError() {
        override val message: String = "Failed to parse data: ${cause?.message ?: "Unknown parsing error"}"
    }
    
    /**
     * Storage operation errors
     */
    data class StorageError(
        val operation: String,
        override val cause: Throwable? = null
    ) : AppError() {
        override val message: String = "Storage operation failed: $operation - ${cause?.message ?: "Unknown error"}"
    }
    
    /**
     * Device-related errors
     */
    data class DeviceError(
        val deviceId: String,
        override val message: String
    ) : AppError()
    
    /**
     * Data validation errors
     */
    data class DataValidationError(
        override val message: String
    ) : AppError()
    
    /**
     * Input validation errors
     */
    data class ValidationError(
        override val message: String
    ) : AppError()
    
    /**
     * Network-related errors
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()
    
    /**
     * Permission-related errors
     */
    data class PermissionError(
        override val message: String
    ) : AppError()
}

/**
 * Error recovery actions that can be taken
 */
sealed class ErrorRecoveryAction {
    object Retry : ErrorRecoveryAction()
    object Ignore : ErrorRecoveryAction()
    data class ShowUserError(val message: String) : ErrorRecoveryAction()
    object Reconnect : ErrorRecoveryAction()
    object RequestPermission : ErrorRecoveryAction()
}

/**
 * Interface for handling application errors
 */
interface ErrorHandler {
    suspend fun handleError(error: AppError): ErrorRecoveryAction
}