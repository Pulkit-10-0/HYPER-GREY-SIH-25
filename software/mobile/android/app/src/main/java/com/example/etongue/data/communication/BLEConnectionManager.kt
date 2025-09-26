package com.example.etongue.data.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.example.etongue.data.models.ConnectionStatus
import com.example.etongue.data.models.ConnectionType
import com.example.etongue.data.models.ESP32Device
import com.example.etongue.data.models.SensorDataPacket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

/**
 * Manages Bluetooth Low Energy connections to ESP32 devices
 */
@SuppressLint("MissingPermission")
class BLEConnectionManager(
    private val context: Context,
    private val dataPacketParser: DataPacketParser
) : ConnectionManager() {
    
    companion object {
        // ESP32 service and characteristic UUIDs (these would be defined by your ESP32 firmware)
        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        private val DATA_CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
        private val COMMAND_CHARACTERISTIC_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: ESP32Device? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _dataFlow = MutableStateFlow<SensorDataPacket?>(null)
    
    override suspend fun scanForDevices(): Flow<List<ESP32Device>> = callbackFlow {
        val devices = mutableListOf<ESP32Device>()
        
        // Real BLE scanning - no mock devices
        
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                
                // Try multiple ways to get device name
                val deviceName = getDeviceName(device, result)
                
                println("BLE: Found device - Name: '$deviceName', Address: ${device.address}, RSSI: ${result.rssi}")
                
                val esp32Device = ESP32Device(
                    id = device.address,
                    name = deviceName,
                    macAddress = device.address,
                    signalStrength = result.rssi,
                    connectionType = ConnectionType.BLUETOOTH_LE
                )
                
                // Update or add device to list
                val existingIndex = devices.indexOfFirst { it.id == esp32Device.id }
                if (existingIndex >= 0) {
                    devices[existingIndex] = esp32Device
                } else {
                    devices.add(esp32Device)
                }
                
                trySend(devices.toList())
            }
            
            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed with error code: $errorCode"))
            }
        }
        
        bluetoothLeScanner?.startScan(scanCallback)
        
        awaitClose {
            bluetoothLeScanner?.stopScan(scanCallback)
        }
    }
    
    override suspend fun connect(device: ESP32Device): Result<Unit> {
        return try {
            if (!isDeviceCompatible(device)) {
                return Result.failure(IllegalArgumentException("Device is not compatible with BLE connection"))
            }
            
            _connectionStatus.value = ConnectionStatus.CONNECTING
            
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
                ?: return Result.failure(Exception("Cannot get Bluetooth device"))
            
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                _connectionStatus.value = ConnectionStatus.CONNECTED
                                println("BLE Connected to ${device.name}")
                                gatt?.discoverServices()
                            } else {
                                _connectionStatus.value = ConnectionStatus.ERROR
                                println("BLE Connection failed with status: $status")
                            }
                        }
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            _connectionStatus.value = ConnectionStatus.DISCONNECTED
                            connectedDevice = null
                            bluetoothGatt = null
                            println("BLE Disconnected from ${device.name}")
                        }
                        BluetoothGatt.STATE_CONNECTING -> {
                            println("BLE Connecting to ${device.name}...")
                        }
                    }
                }
                
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Log all available services for debugging
                        gatt?.services?.forEach { service ->
                            println("BLE Service found: ${service.uuid}")
                            service.characteristics.forEach { characteristic ->
                                println("  Characteristic: ${characteristic.uuid}")
                            }
                        }
                        
                        // Try to find the ESP32 service
                        val service = gatt?.getService(SERVICE_UUID)
                        if (service != null) {
                            dataCharacteristic = service.getCharacteristic(DATA_CHARACTERISTIC_UUID)
                            commandCharacteristic = service.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
                            
                            // Enable notifications for data characteristic
                            dataCharacteristic?.let { characteristic ->
                                gatt.setCharacteristicNotification(characteristic, true)
                                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        } else {
                            // Service not found - this is expected for non-ESP32 devices
                            println("ESP32 service not found on device ${device.name}")
                            // Still consider connection successful for testing
                        }
                    }
                }
                
                override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                    characteristic?.value?.let { data ->
                        val dataString = String(data)
                        val parseResult = dataPacketParser.parseDataPacket(dataString, device.id)
                        parseResult.getOrNull()?.let { packet ->
                            _dataFlow.value = packet
                        }
                    }
                }
            }
            
            bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback)
            connectedDevice = device
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            connectedDevice = null
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun startStreaming(): Flow<SensorDataPacket> {
        return if (_connectionStatus.value.canStartStreaming()) {
            _connectionStatus.value = ConnectionStatus.STREAMING
            sendCommand("START_STREAM")
            
            callbackFlow {
                _dataFlow.collect { packet ->
                    packet?.let { trySend(it) }
                }
                awaitClose { }
            }
        } else {
            emptyFlow()
        }
    }
    
    override suspend fun stopStreaming(): Result<Unit> {
        return try {
            sendCommand("STOP_STREAM")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getConnectionStatus(): Flow<ConnectionStatus> = connectionStatus
    
    override fun getConnectedDevice(): ESP32Device? = connectedDevice
    
    override suspend fun sendCommand(command: String): Result<Unit> {
        return try {
            commandCharacteristic?.let { characteristic ->
                characteristic.value = command.toByteArray()
                val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to send command: $command"))
                }
            } ?: Result.failure(Exception("Command characteristic not available"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isDeviceCompatible(device: ESP32Device): Boolean {
        return device.connectionType == ConnectionType.BLUETOOTH_LE
    }
    
    /**
     * Checks if Bluetooth is enabled and available
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Checks if BLE is supported on this device
     */
    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    }
    
    /**
     * Tries to get a meaningful device name from multiple sources
     */
    private fun getDeviceName(device: BluetoothDevice, scanResult: ScanResult): String {
        // Try device.name first
        if (!device.name.isNullOrBlank()) {
            return device.name
        }
        
        // Try to get name from scan record
        scanResult.scanRecord?.let { scanRecord ->
            val deviceNameBytes = scanRecord.getBytes()
            if (deviceNameBytes != null) {
                // Look for complete local name (0x09) or shortened local name (0x08)
                var i = 0
                while (i < deviceNameBytes.size - 1) {
                    val length = deviceNameBytes[i].toInt() and 0xFF
                    if (length == 0) break
                    
                    val type = deviceNameBytes[i + 1].toInt() and 0xFF
                    if (type == 0x08 || type == 0x09) { // Complete or shortened local name
                        val nameBytes = deviceNameBytes.sliceArray((i + 2)..(i + length))
                        val name = String(nameBytes, Charsets.UTF_8).trim()
                        if (name.isNotBlank()) {
                            return name
                        }
                    }
                    i += length + 1
                }
            }
        }
        
        // Try to identify device type by MAC address prefix
        val macPrefix = device.address.substring(0, 8)
        val deviceType = when (macPrefix) {
            "24:0A:C4", "30:AE:A4", "7C:9E:BD" -> "ESP32"
            "DC:A6:32", "B4:E6:2D", "AC:67:B2" -> "ESP32"
            "48:3F:DA", "84:CC:A8", "A0:B7:65" -> "ESP32"
            else -> "BLE Device"
        }
        
        return "$deviceType (${device.address.takeLast(5)})"
    }
}