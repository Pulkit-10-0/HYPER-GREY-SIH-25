package com.example.etongue.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.etongue.data.models.SensorDataBatch
import com.example.etongue.data.models.DataFile
import com.example.etongue.domain.usecases.LoadHistoricalDataUseCase
import com.example.etongue.domain.usecases.DataSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing data analysis and visualization state
 */
class AnalysisViewModel(
    private val loadHistoricalDataUseCase: LoadHistoricalDataUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    
    /**
     * Loads available data files for selection
     */
    fun loadAvailableFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            loadHistoricalDataUseCase.loadAvailableFiles().fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        availableFiles = files,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to load files",
                        isLoading = false
                    )
                }
            )
        }
    }
    
    /**
     * Loads a specific data file for analysis
     */
    fun loadDataFile(fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingData = true, 
                error = null,
                selectedFileName = fileName
            )
            
            loadHistoricalDataUseCase.loadDataFile(fileName).fold(
                onSuccess = { batch ->
                    val summary = loadHistoricalDataUseCase.getDataSummary(batch)
                    _uiState.value = _uiState.value.copy(
                        selectedData = batch,
                        dataSummary = summary,
                        isLoadingData = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to load data file",
                        isLoadingData = false
                    )
                }
            )
        }
    }
    
    /**
     * Clears the currently selected data
     */
    fun clearSelectedData() {
        _uiState.value = _uiState.value.copy(
            selectedData = null,
            dataSummary = null,
            selectedFileName = null,
            error = null
        )
    }
    
    /**
     * Clears any error messages
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Refreshes the available files list
     */
    fun refreshFiles() {
        loadAvailableFiles()
    }
}

/**
 * UI state for the analysis screen
 */
data class AnalysisUiState(
    val availableFiles: List<DataFile> = emptyList(),
    val selectedData: SensorDataBatch? = null,
    val dataSummary: DataSummary? = null,
    val selectedFileName: String? = null,
    val isLoading: Boolean = false,
    val isLoadingData: Boolean = false,
    val error: String? = null
) {
    val hasData: Boolean
        get() = selectedData != null
    
    val hasFiles: Boolean
        get() = availableFiles.isNotEmpty()
}