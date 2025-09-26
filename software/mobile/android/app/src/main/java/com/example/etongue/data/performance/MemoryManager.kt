package com.example.etongue.data.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages memory usage and provides memory monitoring capabilities
 */
class MemoryManager(private val context: Context) {
    
    private val _memoryStatus = MutableStateFlow(MemoryStatus())
    val memoryStatus: StateFlow<MemoryStatus> = _memoryStatus.asStateFlow()
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    /**
     * Gets current memory usage information
     */
    fun getCurrentMemoryUsage(): MemoryUsage {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        val nativeHeapSize = Debug.getNativeHeapSize()
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize()
        val nativeHeapFree = Debug.getNativeHeapFreeSize()
        
        return MemoryUsage(
            usedMemory = usedMemory,
            availableMemory = availableMemory,
            maxMemory = maxMemory,
            systemAvailableMemory = memoryInfo.availMem,
            systemTotalMemory = memoryInfo.totalMem,
            isLowMemory = memoryInfo.lowMemory,
            memoryUsagePercentage = (usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt(),
            nativeHeapSize = nativeHeapSize,
            nativeHeapAllocated = nativeHeapAllocated,
            nativeHeapFree = nativeHeapFree
        )
    }
    
    /**
     * Updates memory status and triggers cleanup if needed
     */
    fun updateMemoryStatus() {
        val memoryUsage = getCurrentMemoryUsage()
        val status = when {
            memoryUsage.memoryUsagePercentage > 90 -> MemoryPressure.CRITICAL
            memoryUsage.memoryUsagePercentage > 75 -> MemoryPressure.HIGH
            memoryUsage.memoryUsagePercentage > 50 -> MemoryPressure.MODERATE
            else -> MemoryPressure.LOW
        }
        
        _memoryStatus.value = MemoryStatus(
            memoryUsage = memoryUsage,
            memoryPressure = status,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Trigger cleanup if memory pressure is high
        if (status == MemoryPressure.HIGH || status == MemoryPressure.CRITICAL) {
            performMemoryCleanup()
        }
    }
    
    /**
     * Performs memory cleanup operations
     */
    private fun performMemoryCleanup() {
        // Force garbage collection
        System.gc()
        
        // Additional cleanup can be added here
        // This could include clearing caches, reducing buffer sizes, etc.
    }
    
    /**
     * Checks if memory usage is within safe limits
     */
    fun isMemoryUsageSafe(): Boolean {
        val usage = getCurrentMemoryUsage()
        return usage.memoryUsagePercentage < 80 && !usage.isLowMemory
    }
    
    /**
     * Gets recommended buffer size based on available memory
     */
    fun getRecommendedBufferSize(): Int {
        val usage = getCurrentMemoryUsage()
        return when {
            usage.memoryUsagePercentage > 80 -> 500  // Reduce buffer size under high memory pressure
            usage.memoryUsagePercentage > 60 -> 750  // Moderate buffer size
            else -> 1000  // Full buffer size when memory is available
        }
    }
}

/**
 * Data class representing current memory usage
 */
data class MemoryUsage(
    val usedMemory: Long,
    val availableMemory: Long,
    val maxMemory: Long,
    val systemAvailableMemory: Long,
    val systemTotalMemory: Long,
    val isLowMemory: Boolean,
    val memoryUsagePercentage: Int,
    val nativeHeapSize: Long,
    val nativeHeapAllocated: Long,
    val nativeHeapFree: Long
)

/**
 * Data class representing memory status
 */
data class MemoryStatus(
    val memoryUsage: MemoryUsage = MemoryUsage(0, 0, 0, 0, 0, false, 0, 0, 0, 0),
    val memoryPressure: MemoryPressure = MemoryPressure.LOW,
    val lastUpdated: Long = 0L
)

/**
 * Enum representing memory pressure levels
 */
enum class MemoryPressure {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}