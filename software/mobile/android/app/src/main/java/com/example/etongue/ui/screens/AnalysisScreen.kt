package com.example.etongue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.etongue.data.models.SensorDataPacket
import com.example.etongue.data.models.SensorReadings
import com.example.etongue.data.models.ElectrodeReadings
import com.example.etongue.data.models.ColorData
import com.example.etongue.ui.viewmodels.AnalysisViewModel
import com.example.etongue.ui.viewmodels.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    fileName: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AnalysisViewModel = viewModel(
        factory = ViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    // Load data when fileName is provided
    LaunchedEffect(fileName) {
        if (fileName != null) {
            viewModel.loadDataFile(fileName)
        } else {
            viewModel.loadAvailableFiles()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text(
                    text = if (fileName != null) "Analysis: $fileName" else "Data Analysis",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = { 
                        if (fileName != null) {
                            viewModel.loadDataFile(fileName)
                        } else {
                            viewModel.refreshFiles()
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Content based on state
        when {
            uiState.isLoading || uiState.isLoadingData -> {
                LoadingContent()
            }
            uiState.error != null -> {
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = { 
                        viewModel.clearError()
                        if (fileName != null) {
                            viewModel.loadDataFile(fileName)
                        } else {
                            viewModel.loadAvailableFiles()
                        }
                    }
                )
            }
            uiState.hasData -> {
                DataAnalysisContent(
                    data = uiState.selectedData!!,
                    summary = uiState.dataSummary!!
                )
            }
            fileName == null && uiState.hasFiles -> {
                FileSelectionContent(
                    files = uiState.availableFiles,
                    onFileSelected = { selectedFileName ->
                        viewModel.loadDataFile(selectedFileName)
                    }
                )
            }
            else -> {
                EmptyContent()
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading data...")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No data files available",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FileSelectionContent(
    files: List<com.example.etongue.data.models.DataFile>,
    onFileSelected: (String) -> Unit
) {
    LazyColumn {
        item {
            Text(
                text = "Select a file to analyze:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        items(files) { file ->
            FileItem(
                file = file,
                onClick = { onFileSelected(file.fileName) }
            )
        }
    }
}

@Composable
private fun FileItem(
    file: com.example.etongue.data.models.DataFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Created: ${formatTimestamp(file.createdAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Size: ${file.getFormattedFileSize()} • ${file.dataPointCount} data points",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
@Composable
private fun DataAnalysisContent(
    data: com.example.etongue.data.models.SensorDataBatch,
    summary: com.example.etongue.domain.usecases.DataSummary
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Session Summary
        item {
            SessionSummaryCard(summary = summary)
        }
        
        // Device Information
        item {
            DeviceInfoCard(deviceInfo = data.deviceInfo)
        }
        
        // Metadata
        if (data.metadata.sampleType != null || data.metadata.operatorNotes != null) {
            item {
                MetadataCard(metadata = data.metadata)
            }
        }
        
        // Data Points Header
        item {
            Text(
                text = "Data Points (${data.dataPoints.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Data Points
        items(data.dataPoints) { dataPoint ->
            DataPointCard(dataPoint = dataPoint)
        }
    }
}

@Composable
private fun SessionSummaryCard(
    summary: com.example.etongue.domain.usecases.DataSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            SummaryRow("Session ID", summary.sessionId)
            SummaryRow("Device ID", summary.deviceId)
            SummaryRow("Start Time", formatTimestamp(summary.startTime))
            SummaryRow("End Time", formatTimestamp(summary.endTime))
            SummaryRow("Duration", formatDuration(summary.duration))
            SummaryRow("Data Points", summary.totalDataPoints.toString())
            SummaryRow("Sampling Rate", "${String.format("%.2f", summary.averageSamplingRate)} Hz")
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceInfo: com.example.etongue.data.models.ESP32Device
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Device Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            SummaryRow("Device Name", deviceInfo.name)
            SummaryRow("MAC Address", deviceInfo.macAddress)
            SummaryRow("Connection Type", deviceInfo.connectionType.name)
            SummaryRow("Signal Strength", "${deviceInfo.signalStrength} dBm")
        }
    }
}

@Composable
private fun MetadataCard(
    metadata: com.example.etongue.data.models.SessionMetadata
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Session Metadata",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            metadata.sampleType?.let { 
                SummaryRow("Sample Type", it)
            }
            metadata.testConditions?.let { 
                SummaryRow("Test Conditions", it)
            }
            metadata.operatorNotes?.let { 
                SummaryRow("Notes", it)
            }
            SummaryRow("App Version", metadata.appVersion)
            SummaryRow("Data Format", metadata.dataFormatVersion)
        }
    }
}

@Composable
private fun DataPointCard(
    dataPoint: SensorDataPacket
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Data Point - ${formatTimestamp(dataPoint.timestamp)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sensor Readings
            SensorReadingsSection(readings = dataPoint.sensorReadings)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Electrode Readings
            ElectrodeReadingsSection(readings = dataPoint.electrodeReadings)
        }
    }
}

@Composable
private fun SensorReadingsSection(
    readings: SensorReadings
) {
    Column {
        Text(
            text = "Sensor Readings",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SensorValueItem("pH", String.format("%.2f", readings.ph))
                SensorValueItem("TDS", "${String.format("%.1f", readings.tds)} ppm")
                SensorValueItem("Temperature", "${String.format("%.1f", readings.temperature)}°C")
            }
            Column(modifier = Modifier.weight(1f)) {
                SensorValueItem("UV Absorbance", String.format("%.3f", readings.uvAbsorbance))
                SensorValueItem("Moisture", "${String.format("%.1f", readings.moisturePercentage)}%")
                ColorValueItem("Color", readings.colorRgb)
            }
        }
    }
}

@Composable
private fun ElectrodeReadingsSection(
    readings: ElectrodeReadings
) {
    Column {
        Text(
            text = "Electrode Readings",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        val electrodeMap = readings.toMap()
        val electrodeNames = listOf(
            "platinum" to "Platinum",
            "silver" to "Silver", 
            "silverChloride" to "Silver Chloride",
            "stainlessSteel" to "Stainless Steel",
            "copper" to "Copper",
            "carbon" to "Carbon",
            "zinc" to "Zinc"
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                electrodeNames.take(4).forEach { (key, displayName) ->
                    electrodeMap[key]?.let { value ->
                        SensorValueItem(displayName, String.format("%.3f", value))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                electrodeNames.drop(4).forEach { (key, displayName) ->
                    electrodeMap[key]?.let { value ->
                        SensorValueItem(displayName, String.format("%.3f", value))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SensorValueItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ColorValueItem(
    label: String,
    colorData: ColorData
) {
    Column(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = Color(colorData.red, colorData.green, colorData.blue),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = colorData.hex,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}