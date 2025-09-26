package com.example.etongue.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.etongue.data.models.*
import com.example.etongue.ui.viewmodels.SensorDataViewModel
import com.example.etongue.ui.viewmodels.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDataManagement: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToConnection: () -> Unit = {},
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    val viewModel: SensorDataViewModel = viewModel(factory = viewModelFactory)
    
    val uiState by viewModel.uiState.collectAsState()
    val currentSensorData by viewModel.currentSensorData.collectAsState()
    val streamingStatus by viewModel.streamingStatus.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with connection status and streaming controls
        DashboardHeader(
            connectionStatus = connectionStatus,
            streamingStatus = streamingStatus,
            onStartStreaming = { viewModel.startStreaming() },
            onStopStreaming = { viewModel.stopStreaming() },
            isLoading = uiState.isLoading
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message display
        errorMessage?.let { error ->
            ErrorCard(
                message = error,
                onDismiss = { viewModel.clearError() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Data timeout warning
        if (uiState.isDataTimeout) {
            DataTimeoutWarning()
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Main content
        if (currentSensorData != null) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Sensor readings section
                    SensorReadingsSection(
                        sensorReadings = currentSensorData!!.sensorReadings
                    )
                }
                
                item {
                    // Electrode readings section
                    ElectrodeReadingsSection(
                        electrodeReadings = currentSensorData!!.electrodeReadings
                    )
                }
                
                item {
                    // Device info section
                    DeviceInfoSection(
                        deviceId = currentSensorData!!.deviceId,
                        timestamp = currentSensorData!!.timestamp
                    )
                }
            }
        } else if (!uiState.hasReceivedData && streamingStatus == StreamingStatus.STREAMING) {
            // Waiting for data
            WaitingForDataCard()
        } else {
            // No data available
            NoDataCard(
                connectionStatus = connectionStatus,
                streamingStatus = streamingStatus
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    connectionStatus: ConnectionStatus,
    streamingStatus: StreamingStatus,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "E-Tongue Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                ConnectionStatusIndicator(connectionStatus = connectionStatus)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamingStatusIndicator(streamingStatus = streamingStatus)
                
                StreamingControls(
                    streamingStatus = streamingStatus,
                    connectionStatus = connectionStatus,
                    onStartStreaming = onStartStreaming,
                    onStopStreaming = onStopStreaming,
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusIndicator(connectionStatus: ConnectionStatus) {
    val color = when (connectionStatus) {
        ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> Color.Green
        ConnectionStatus.CONNECTING -> Color.Yellow
        ConnectionStatus.ERROR -> Color.Red
        ConnectionStatus.DISCONNECTED -> Color.Gray
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = connectionStatus.getDisplayName(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StreamingStatusIndicator(streamingStatus: StreamingStatus) {
    val color = when (streamingStatus) {
        StreamingStatus.STREAMING -> Color.Green
        StreamingStatus.STARTING, StreamingStatus.STOPPING -> Color.Yellow
        StreamingStatus.ERROR -> Color.Red
        else -> Color.Gray
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = streamingStatus.getDisplayName(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StreamingControls(
    streamingStatus: StreamingStatus,
    connectionStatus: ConnectionStatus,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    isLoading: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (streamingStatus.canStart() && connectionStatus.isActive()) {
            Button(
                onClick = onStartStreaming,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start")
            }
        }
        
        if (streamingStatus.canStop()) {
            Button(
                onClick = onStopStreaming,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = "Stop")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun SensorReadingsSection(sensorReadings: SensorReadings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sensor Readings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SensorCard(
                        title = "pH",
                        value = String.format("%.2f", sensorReadings.ph),
                        unit = "pH",
                        color = getPhColor(sensorReadings.ph)
                    )
                }
                
                item {
                    SensorCard(
                        title = "TDS",
                        value = String.format("%.0f", sensorReadings.tds),
                        unit = "ppm",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                item {
                    SensorCard(
                        title = "Temperature",
                        value = String.format("%.1f", sensorReadings.temperature),
                        unit = "°C",
                        color = getTemperatureColor(sensorReadings.temperature)
                    )
                }
                
                item {
                    SensorCard(
                        title = "UV Absorbance",
                        value = String.format("%.3f", sensorReadings.uvAbsorbance),
                        unit = "AU",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                item {
                    SensorCard(
                        title = "Moisture",
                        value = String.format("%.1f", sensorReadings.moisturePercentage),
                        unit = "%",
                        color = getMoistureColor(sensorReadings.moisturePercentage)
                    )
                }
                
                item {
                    ColorSensorCard(colorData = sensorReadings.colorRgb)
                }
            }
        }
    }
}

@Composable
private fun SensorCard(
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ColorSensorCard(colorData: ColorData) {
    val displayColor = Color(colorData.red, colorData.green, colorData.blue)
    
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Color",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(displayColor)
                    .border(1.dp, Color.Gray, CircleShape)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = colorData.hex,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ElectrodeReadingsSection(electrodeReadings: ElectrodeReadings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Electrode Readings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val electrodeData = electrodeReadings.toMap()
            val electrodeNames = mapOf(
                "platinum" to "Pt",
                "silver" to "Ag",
                "silverChloride" to "AgCl",
                "stainlessSteel" to "SS",
                "copper" to "Cu",
                "carbon" to "C",
                "zinc" to "Zn"
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(electrodeData.toList()) { (electrode, value) ->
                    ElectrodeCard(
                        name = electrodeNames[electrode] ?: electrode,
                        value = value
                    )
                }
            }
        }
    }
}

@Composable
private fun ElectrodeCard(
    name: String,
    value: Double
) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
            
            Text(
                text = "mV",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DeviceInfoSection(
    deviceId: String,
    timestamp: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Device ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Last Update",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatTimestamp(timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun DataTimeoutWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.tertiary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "No data received for more than 5 seconds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun WaitingForDataCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Waiting for sensor data...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Make sure the ESP32 device is connected and streaming",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoDataCard(
    connectionStatus: ConnectionStatus,
    streamingStatus: StreamingStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Sensor Data",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val message = when {
                connectionStatus == ConnectionStatus.DISCONNECTED -> 
                    "Connect to an ESP32 device to start receiving sensor data"
                streamingStatus == StreamingStatus.IDLE -> 
                    "Press the Start button to begin streaming sensor data"
                else -> 
                    "Sensor data will appear here once streaming begins"
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// Helper functions for color coding
@Composable
private fun getPhColor(ph: Double): Color {
    return when {
        ph < 6.0 -> Color.Red // Acidic
        ph > 8.0 -> Color.Blue // Basic
        else -> Color.Green // Neutral
    }
}

@Composable
private fun getTemperatureColor(temperature: Double): Color {
    return when {
        temperature < 10.0 -> Color.Blue // Cold
        temperature > 30.0 -> Color.Red // Hot
        else -> Color.Green // Normal
    }
}

@Composable
private fun getMoistureColor(moisture: Double): Color {
    return when {
        moisture < 30.0 -> Color.Red // Dry
        moisture > 70.0 -> Color.Blue // Wet
        else -> Color.Green // Normal
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val diff = currentTime - timestamp
    
    return when {
        diff < 1000 -> "Just now"
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }
}