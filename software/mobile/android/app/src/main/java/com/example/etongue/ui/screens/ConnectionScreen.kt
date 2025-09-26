package com.example.etongue.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.ui.viewmodels.ConnectionViewModel
import com.example.etongue.utils.PermissionHandler
import com.example.etongue.utils.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    
    var hasPermissions by remember { mutableStateOf(PermissionHandler.hasAllPermissions(context)) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            // Auto-start scanning when permissions are granted
            viewModel.startScanning()
        }
    }

    // Navigate to dashboard when connected
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.STREAMING) {
            onNavigateToDashboard()
        }
    }
    
    // Check permissions on first load
    LaunchedEffect(Unit) {
        hasPermissions = PermissionHandler.hasAllPermissions(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Device Connection",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Connection Status Card
        ConnectionStatusCard(
            connectionStatus = connectionStatus,
            connectedDevice = uiState.connectedDevice,
            onDisconnect = { viewModel.disconnectFromDevice() },
            onRefresh = { viewModel.refreshConnection() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Type Filter
        ConnectionTypeFilter(
            selectedType = uiState.selectedConnectionType,
            onTypeSelected = { viewModel.filterDevicesByType(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Permission Check or Scan Controls
        if (!hasPermissions) {
            PermissionRequestCard(
                onRequestPermissions = {
                    val missingPermissions = PermissionHandler.getMissingPermissions(context)
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                }
            )
        } else {
            ScanControls(
                isScanning = uiState.isScanning,
                onStartScan = { viewModel.startScanning() },
                onStopScan = { viewModel.stopScanning() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct Connection Section (ESP32 connected externally)
        DirectConnectionCard(
            onStartWithData = {
                // Navigate directly to dashboard since ESP32 is connected externally
                onNavigateToDashboard()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Manual Connection Section (for advanced users)
        ManualConnectionCard(
            selectedConnectionType = uiState.selectedConnectionType,
            onManualConnect = { connectionType, address ->
                viewModel.connectManually(connectionType, address)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error Message
        uiState.errorMessage?.let { error ->
            ErrorMessage(
                message = error,
                onDismiss = { viewModel.clearError() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Debug Information (only show when scanning WiFi and no devices found)
        if (uiState.isScanning && uiState.selectedConnectionType == ConnectionType.WIFI && viewModel.getFilteredDevices().isEmpty()) {
            NetworkDebugCard()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Device List
        DeviceList(
            devices = viewModel.getFilteredDevices(),
            selectedDevice = uiState.selectedDevice,
            isConnecting = uiState.isConnecting,
            onDeviceSelected = { viewModel.selectDevice(it) },
            onConnectToDevice = { viewModel.connectToDevice(it) }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionStatus: ConnectionStatus,
    connectedDevice: ESP32Device?,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ConnectionStatus.ERROR -> Color(0xFFF44336).copy(alpha = 0.1f)
                ConnectionStatus.CONNECTING -> Color(0xFFFF9800).copy(alpha = 0.1f)
                ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = connectionStatus.getDisplayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> Color(0xFF4CAF50)
                            ConnectionStatus.ERROR -> Color(0xFFF44336)
                            ConnectionStatus.CONNECTING -> Color(0xFFFF9800)
                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Icon(
                    imageVector = when (connectionStatus) {
                        ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> Icons.Default.CheckCircle
                        ConnectionStatus.ERROR -> Icons.Default.Warning
                        ConnectionStatus.CONNECTING -> Icons.Default.Refresh
                        ConnectionStatus.DISCONNECTED -> Icons.Default.Close
                    },
                    contentDescription = connectionStatus.getDisplayName(),
                    tint = when (connectionStatus) {
                        ConnectionStatus.CONNECTED, ConnectionStatus.STREAMING -> Color(0xFF4CAF50)
                        ConnectionStatus.ERROR -> Color(0xFFF44336)
                        ConnectionStatus.CONNECTING -> Color(0xFFFF9800)
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            connectedDevice?.let { device ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Connected Device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${device.connectionType.getDisplayName()} • ${device.getSignalStrengthDescription()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh")
                    }
                    
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTypeFilter(
    selectedType: ConnectionType?,
    onTypeSelected: (ConnectionType?) -> Unit
) {
    Column {
        Text(
            text = "Connection Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedType == ConnectionType.BLUETOOTH_LE,
                onClick = { onTypeSelected(ConnectionType.BLUETOOTH_LE) },
                label = { Text("Bluetooth LE") }
            )
            FilterChip(
                selected = selectedType == ConnectionType.WIFI,
                onClick = { onTypeSelected(ConnectionType.WIFI) },
                label = { Text("WiFi") }
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permissions",
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFFF9800)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This app needs Bluetooth and location permissions to scan for devices.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Grant Permissions",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun ScanControls(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Available Devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        if (isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Auto-refreshing every 5s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onStopScan) {
                        Text("Stop Scan")
                    }
                }
            }
        } else {
            Button(onClick = onStartScan) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Scan",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan")
            }
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<ESP32Device>,
    selectedDevice: ESP32Device?,
    isConnecting: Boolean,
    onDeviceSelected: (ESP32Device) -> Unit,
    onConnectToDevice: (ESP32Device) -> Unit
) {
    if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "No devices",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No devices found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tap 'Scan' to search for ESP32 devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                DeviceItem(
                    device = device,
                    isSelected = selectedDevice?.id == device.id,
                    isConnecting = isConnecting && selectedDevice?.id == device.id,
                    onDeviceSelected = { onDeviceSelected(device) },
                    onConnectToDevice = { onConnectToDevice(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: ESP32Device,
    isSelected: Boolean,
    isConnecting: Boolean,
    onDeviceSelected: () -> Unit,
    onConnectToDevice: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.connectionType.getDisplayName()} • ${device.getSignalStrengthDescription()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    SignalStrengthIndicator(signalStrength = device.signalStrength)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = onConnectToDevice,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Connect",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalStrengthIndicator(signalStrength: Int) {
    val bars = when {
        signalStrength >= -50 -> 4
        signalStrength >= -60 -> 3
        signalStrength >= -70 -> 2
        signalStrength >= -80 -> 1
        else -> 0
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + index * 2).dp)
                    .background(
                        color = if (index < bars) {
                            when {
                                bars >= 3 -> Color(0xFF4CAF50)
                                bars >= 2 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
private fun NetworkDebugCard() {
    val context = LocalContext.current
    var localIp by remember { mutableStateOf("Loading...") }
    var isExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        localIp = NetworkUtils.getLocalIpAddress() ?: "Unknown"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WiFi Scanning Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Your IP: $localIp",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (localIp != "Unknown") {
                    val networkPrefix = localIp.substringBeforeLast(".")
                    Text(
                        text = "Scanning: $networkPrefix.100-102, $networkPrefix.200-202",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tips:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                val tips = listOf(
                    "ESP32 should be on same WiFi network",
                    "ESP32 should listen on port 8080",
                    "Try common IPs: .100, .101, .200, .201"
                )
                tips.forEach { tip ->
                    Text(
                        text = "• $tip",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Testing Options:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                var mockServerStatus by remember { mutableStateOf("Not running") }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            mockServerStatus = com.example.etongue.utils.TestingUtils.startMockESP32Server()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start Mock Server", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Button(
                        onClick = {
                            mockServerStatus = com.example.etongue.utils.TestingUtils.stopMockESP32Server()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stop Mock Server", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mockServerStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.example.etongue.utils.TestingUtils.getTestConnectionInfo(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ManualConnectionCard(
    selectedConnectionType: ConnectionType?,
    onManualConnect: (ConnectionType, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("192.168.1.100") }
    var bluetoothAddress by remember { mutableStateOf("AA:BB:CC:DD:EE:FF") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Manual Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect directly to your ESP32 device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "WiFi",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect via WiFi")
                }
                
                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Bluetooth",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect via BLE")
                }
            }
        }
    }
    
    // Manual Connection Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Manual Connection")
            },
            text = {
                Column {
                    Text(
                        text = "Enter your ESP32 device details:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // WiFi IP Address Input
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("WiFi IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = "WiFi")
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Bluetooth Address Input
                    OutlinedTextField(
                        value = bluetoothAddress,
                        onValueChange = { bluetoothAddress = it },
                        label = { Text("Bluetooth MAC Address") },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = "Bluetooth")
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Common ESP32 WiFi IPs: 192.168.1.100, 192.168.4.1, 10.0.0.100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (ipAddress.isNotBlank()) {
                                onManualConnect(ConnectionType.WIFI, ipAddress.trim())
                                showDialog = false
                            }
                        }
                    ) {
                        Text("Connect WiFi")
                    }
                    
                    Button(
                        onClick = {
                            if (bluetoothAddress.isNotBlank()) {
                                onManualConnect(ConnectionType.BLUETOOTH_LE, bluetoothAddress.trim())
                                showDialog = false
                            }
                        }
                    ) {
                        Text("Connect BLE")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}@Composable

private fun DirectConnectionCard(
    onStartWithData: () -> Unit
) {
    val status = remember { com.example.etongue.utils.DirectDataLoader.getStatus() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESP32 Ready",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your ESP32 device is connected and ready to use. Connect your ESP32 to WiFi manually, then tap below to start collecting sensor data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onStartWithData,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Using E-Tongue App")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Instructions:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val instructions = listOf(
                "1. Connect your ESP32 to WiFi using its web interface",
                "2. Make sure ESP32 is on the same network as your phone",
                "3. Tap 'Start Using E-Tongue App' above",
                "4. The app will use real sensor data from your device"
            )
            
            instructions.forEach { instruction ->
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}