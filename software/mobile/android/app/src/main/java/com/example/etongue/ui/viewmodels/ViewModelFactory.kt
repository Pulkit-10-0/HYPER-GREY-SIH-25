package com.example.etongue.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.etongue.data.communication.BLEConnectionManager
import com.example.etongue.data.communication.DataPacketParser
import com.example.etongue.data.communication.WiFiConnectionManager
import com.example.etongue.data.repository.DeviceConnectionRepository
import com.example.etongue.data.repository.DeviceConnectionRepositoryImpl
import com.example.etongue.data.repository.SensorDataRepository
import com.example.etongue.data.repository.SensorDataRepositoryImpl
import com.example.etongue.data.storage.JSONFileManager
import com.example.etongue.data.streaming.SensorDataBuffer
import com.example.etongue.domain.usecases.SaveSensorDataUseCase
import com.example.etongue.domain.usecases.LoadHistoricalDataUseCase

/**
 * Factory for creating ViewModels with dependencies
 */
class ViewModelFactory(
    private val context: Context,
    private val sensorDataRepository: SensorDataRepository? = null
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            ConnectionViewModel::class.java -> {
                // Create data packet parser
                val dataPacketParser = DataPacketParser()
                
                // Create connection managers
                val bleConnectionManager = BLEConnectionManager(context, dataPacketParser)
                val wifiConnectionManager = WiFiConnectionManager(context, dataPacketParser)
                
                // Create repository with dependencies
                val repository: DeviceConnectionRepository = DeviceConnectionRepositoryImpl(
                    context = context,
                    bleConnectionManager = bleConnectionManager,
                    wifiConnectionManager = wifiConnectionManager
                )
                ConnectionViewModel(repository) as T
            }
            SensorDataViewModel::class.java -> {
                // Create data packet parser
                val dataPacketParser = DataPacketParser()
                
                // Create connection managers
                val bleConnectionManager = BLEConnectionManager(context, dataPacketParser)
                val wifiConnectionManager = WiFiConnectionManager(context, dataPacketParser)
                
                // Create device connection repository
                val deviceRepository: DeviceConnectionRepository = DeviceConnectionRepositoryImpl(
                    context = context,
                    bleConnectionManager = bleConnectionManager,
                    wifiConnectionManager = wifiConnectionManager
                )
                
                // Create sensor data repository if not provided
                val sensorRepository = sensorDataRepository ?: run {
                    val jsonFileManager = JSONFileManager(context)
                    val sensorDataBuffer = SensorDataBuffer()
                    SensorDataRepositoryImpl(jsonFileManager, sensorDataBuffer)
                }
                
                SensorDataViewModel(sensorRepository, deviceRepository) as T
            }
            DataManagementViewModel::class.java -> {
                // Create sensor data repository if not provided
                val sensorRepository = sensorDataRepository ?: run {
                    val jsonFileManager = JSONFileManager(context)
                    val sensorDataBuffer = SensorDataBuffer()
                    SensorDataRepositoryImpl(jsonFileManager, sensorDataBuffer)
                }
                
                // Create use case
                val saveSensorDataUseCase = SaveSensorDataUseCase(sensorRepository)
                
                DataManagementViewModel(sensorRepository, saveSensorDataUseCase) as T
            }
            AnalysisViewModel::class.java -> {
                // Create sensor data repository if not provided
                val sensorRepository = sensorDataRepository ?: run {
                    val jsonFileManager = JSONFileManager(context)
                    val sensorDataBuffer = SensorDataBuffer()
                    SensorDataRepositoryImpl(jsonFileManager, sensorDataBuffer)
                }
                
                // Create use case
                val loadHistoricalDataUseCase = LoadHistoricalDataUseCase(sensorRepository)
                
                AnalysisViewModel(loadHistoricalDataUseCase) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}