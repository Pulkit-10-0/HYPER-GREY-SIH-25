import React, { useState, useEffect } from 'react';
import {
    View,
    Text,
    StyleSheet,
    ScrollView,
    TouchableOpacity,
    Alert,
    Appearance,
    StatusBar,
    SafeAreaView,
    ActivityIndicator,
    FlatList,
    Dimensions,
    Image,
    Platform,
    PermissionsAndroid,
    Vibration
} from 'react-native';

const { width } = Dimensions.get('window');

const AyuSureApp = () => {
    // Core State Management
    const [isConnected, setIsConnected] = useState(false);
    const [connectionType, setConnectionType] = useState('All');
    const [availableDevices, setAvailableDevices] = useState([]);
    const [connectedDevice, setConnectedDevice] = useState(null);
    const [isScanning, setIsScanning] = useState(false);
    const [theme, setTheme] = useState(Appearance.getColorScheme() || 'dark');
    const [isStreaming, setIsStreaming] = useState(false);

    // Sensor Data State
    const [sensorData, setSensorData] = useState({
        ph: 0,
        tds: 0,
        temperature: 0,
        uv: 0,
        moisture: 0,
        color: '#000000'
    });

    // Electrode Data State
    const [electrodeData, setElectrodeData] = useState({
        pt: 0,
        ag: 0,
        agcl: 0,
        ss: 0,
        cu: 0,
        c: 0,
        zn: 0
    });

    // Theme Detection and Updates
    useEffect(() => {
        const subscription = Appearance.addChangeListener(({ colorScheme }) => {
            setTheme(colorScheme || 'dark');
        });
        return () => subscription?.remove();
    }, []);

    // Dynamic Theme Colors
    const getThemeColors = (currentTheme) => ({
        background: currentTheme === 'dark' ? '#1a1a1a' : '#f5f5f5',
        cardBackground: currentTheme === 'dark' ? '#2d2d2d' : '#ffffff',
        textPrimary: currentTheme === 'dark' ? '#ffffff' : '#000000',
        textSecondary: currentTheme === 'dark' ? '#cccccc' : '#666666',
        border: currentTheme === 'dark' ? '#404040' : '#e0e0e0',
        success: '#4CAF50',
        error: '#f44336',
        warning: '#ff9800',
        accent: '#2196F3',
        scanButton: currentTheme === 'dark' ? '#4a5568' : '#6366f1',
        stopButton: '#ff6b9d'
    });

    const colors = getThemeColors(theme);

    // Mock Data Generation Functions
    const generateMockSensorData = () => ({
        ph: (Math.random() * 14).toFixed(2),
        tds: Math.floor(Math.random() * 1000),
        temperature: (20 + Math.random() * 15).toFixed(1),
        uv: (Math.random() * 2).toFixed(3),
        moisture: (Math.random() * 100).toFixed(1),
        color: `#${Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0')}`
    });

    const generateMockElectrodeData = () => ({
        pt: (Math.random() * 1000 - 500).toFixed(2),
        ag: (Math.random() * 400 - 200).toFixed(2),
        agcl: (Math.random() * 200 - 100).toFixed(2),
        ss: (Math.random() * 600 - 300).toFixed(2),
        cu: (Math.random() * 500 - 250).toFixed(2),
        c: (Math.random() * 300 - 150).toFixed(2),
        zn: (Math.random() * 400 - 200).toFixed(2)
    });

    const generateMockDevices = () => [
        {
            id: '1',
            name: 'ESP32 Ready',
            macAddress: '6F:81:15:FE:41:B3',
            connectionType: 'Bluetooth LE',
            signalStrength: 'Good'
        },
        {
            id: '2',
            name: 'BLE Device (07:62)',
            macAddress: '6D:AE:A3:14:07:62',
            connectionType: 'Bluetooth LE',
            signalStrength: 'Good'
        },
        {
            id: '3',
            name: 'BLE Device (F1:74)',
            macAddress: '73:A4:6B:51:F1:74',
            connectionType: 'Bluetooth LE',
            signalStrength: 'Fair'
        },
        {
            id: '4',
            name: 'BLE Device (54:CD)',
            macAddress: '43:10:D2:9C:54:CD',
            connectionType: 'Bluetooth LE',
            signalStrength: 'Very Weak'
        },
        {
            id: '5',
            name: 'BLE Device (83:D2)',
            macAddress: '48:BB:F1:59:83:D2',
            connectionType: 'Bluetooth LE',
            signalStrength: 'Fair'
        }
    ];

    // Real-time Data Updates
    useEffect(() => {
        let interval;
        if (isConnected && isStreaming) {
            interval = setInterval(() => {
                setSensorData(generateMockSensorData());
                setElectrodeData(generateMockElectrodeData());
            }, 2000);
        }
        return () => clearInterval(interval);
    }, [isConnected, isStreaming]);

    // Permission Handling
    const requestPermissions = async () => {
        if (Platform.OS === 'android') {
            try {
                const granted = await PermissionsAndroid.requestMultiple([
                    PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
                    PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
                    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
                ]);
                return Object.values(granted).every(permission =>
                    permission === PermissionsAndroid.RESULTS.GRANTED
                );
            } catch (err) {
                console.warn(err);
                return false;
            }
        }
        return true;
    };

    // Device Scanning Functions
    const scanForDevices = async () => {
        const hasPermissions = await requestPermissions();
        if (!hasPermissions) {
            Alert.alert(
                'Permissions Required',
                'Bluetooth and location permissions are required to scan for devices.',
                [{ text: 'OK' }]
            );
            return;
        }

        setIsScanning(true);
        Vibration.vibrate(50); // Haptic feedback

        // Simulate scanning delay
        setTimeout(() => {
            const mockDevices = generateMockDevices();
            const filteredDevices = connectionType === 'All'
                ? mockDevices
                : mockDevices.filter(device => device.connectionType === connectionType);

            setAvailableDevices(filteredDevices);
            setIsScanning(false);
        }, 2000);
    };

    // Device Connection Functions
    const connectToDevice = async (device) => {
        Vibration.vibrate(50); // Haptic feedback

        try {
            // Simulate connection process
            setConnectedDevice(device);
            setIsConnected(true);
            setIsStreaming(true);

            // Initialize with mock data
            setSensorData(generateMockSensorData());
            setElectrodeData(generateMockElectrodeData());

            Alert.alert(
                'Connection Successful',
                `Connected to ${device.name}`,
                [{ text: 'OK' }]
            );
        } catch (error) {
            Alert.alert(
                'Connection Failed',
                'Failed to connect to device. Please try again.',
                [
                    { text: 'Cancel', style: 'cancel' },
                    { text: 'Retry', onPress: () => connectToDevice(device) }
                ]
            );
        }
    };

    // Disconnect Function
    const disconnectDevice = () => {
        Vibration.vibrate(50); // Haptic feedback
        setIsConnected(false);
        setIsStreaming(false);
        setConnectedDevice(null);
        setAvailableDevices([]);
    };

    // Get Signal Strength Bars
    const getSignalBars = (strength) => {
        const barCount = strength === 'Good' ? 4 : strength === 'Fair' ? 2 : 1;
        return Array.from({ length: 4 }, (_, i) => (
            <View
                key={i}
                style={[
                    styles.signalBar,
                    {
                        backgroundColor: i < barCount ? colors.success : colors.border,
                        height: 4 + (i * 2)
                    }
                ]}
            />
        ));
    };

    // Get Sensor Border Color
    const getSensorBorderColor = (sensorType) => {
        switch (sensorType) {
            case 'ph': return '#2196F3';
            case 'moisture': return '#4CAF50';
            case 'temperature': return '#4CAF50';
            case 'uv': return '#9E9E9E';
            case 'tds': return '#9E9E9E';
            case 'color': return '#000000';
            default: return colors.border;
        }
    };

    // Connection Screen Component
    const ConnectionScreen = () => (
        <ScrollView style={[styles.container, { backgroundColor: colors.background }]}>
            {/* Header */}
            <View style={styles.header}>
                <View style={styles.headerWithLogo}>
                    <Image
                        source={require('./logo.png')}
                        style={styles.logo}
                        resizeMode="contain"
                    />
                    <Text style={[styles.headerTitle, { color: colors.textPrimary }]}>
                        Device Connection
                    </Text>
                </View>
                <Text style={[styles.teamAttribution, { color: colors.textSecondary }]}>
                    Hyper Grey - MSIT
                </Text>
            </View>

            {/* Connection Status Card */}
            <View style={[styles.statusCard, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
                <View style={styles.statusContent}>
                    <Text style={[styles.statusTitle, { color: colors.textPrimary }]}>
                        Connection Status
                    </Text>
                    <Text style={[styles.statusText, { color: colors.textSecondary }]}>
                        Disconnected
                    </Text>
                </View>
                <Text style={[styles.statusIcon, { color: colors.error }]}>✕</Text>
            </View>

            {/* Connection Type Filters */}
            <View style={styles.filterSection}>
                <Text style={[styles.sectionTitle, { color: colors.textPrimary }]}>
                    Connection Type
                </Text>
                <View style={styles.filterButtons}>
                    {['All', 'Bluetooth LE', 'WiFi'].map((type) => (
                        <TouchableOpacity
                            key={type}
                            style={[
                                styles.filterButton,
                                {
                                    backgroundColor: connectionType === type ? colors.accent : colors.cardBackground,
                                    borderColor: colors.border
                                }
                            ]}
                            onPress={() => {
                                setConnectionType(type);
                                Vibration.vibrate(50);
                            }}
                        >
                            <Text style={[
                                styles.filterButtonText,
                                {
                                    color: connectionType === type ? '#ffffff' : colors.textPrimary
                                }
                            ]}>
                                {type}
                            </Text>
                        </TouchableOpacity>
                    ))}
                </View>
            </View>

            {/* Available Devices Section */}
            <View style={styles.devicesSection}>
                <View style={styles.devicesSectionHeader}>
                    <Text style={[styles.sectionTitle, { color: colors.textPrimary }]}>
                        Available Devices
                    </Text>
                    <TouchableOpacity
                        style={[styles.scanButton, { backgroundColor: colors.scanButton }]}
                        onPress={scanForDevices}
                        disabled={isScanning}
                    >
                        {isScanning ? (
                            <ActivityIndicator size="small" color="#ffffff" />
                        ) : (
                            <Text style={styles.scanButtonText}>🔍 Scan</Text>
                        )}
                    </TouchableOpacity>
                </View>

                {/* Device List */}
                {availableDevices.length === 0 ? (
                    <View style={styles.emptyState}>
                        <Text style={styles.emptyStateIcon}>🔍</Text>
                        <Text style={[styles.emptyStateTitle, { color: colors.textPrimary }]}>
                            No devices found
                        </Text>
                        <Text style={[styles.emptyStateText, { color: colors.textSecondary }]}>
                            Tap 'Scan' to search for ESP32 devices
                        </Text>
                    </View>
                ) : (
                    <FlatList
                        data={availableDevices}
                        keyExtractor={(item) => item.id}
                        renderItem={({ item }) => (
                            <View style={[styles.deviceCard, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
                                <View style={styles.deviceInfo}>
                                    <Text style={[styles.deviceName, { color: colors.textPrimary }]}>
                                        {item.name}
                                    </Text>
                                    <Text style={[styles.deviceMac, { color: colors.textSecondary }]}>
                                        {item.macAddress}
                                    </Text>
                                    <Text style={[styles.deviceType, { color: colors.textSecondary }]}>
                                        {item.connectionType} • {item.signalStrength}
                                    </Text>
                                </View>
                                <View style={styles.deviceActions}>
                                    <View style={styles.signalStrength}>
                                        {getSignalBars(item.signalStrength)}
                                    </View>
                                    <TouchableOpacity
                                        style={[styles.connectButton, { backgroundColor: colors.accent }]}
                                        onPress={() => connectToDevice(item)}
                                    >
                                        <Text style={styles.connectButtonText}>Connect</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>
                        )}
                    />
                )}
            </View>
        </ScrollView>
    );

    // Dashboard Screen Component
    const DashboardScreen = () => (
        <ScrollView style={[styles.container, { backgroundColor: colors.background }]}>
            {/* Dashboard Header */}
            <View style={[styles.dashboardHeader, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
                <View style={styles.dashboardHeaderContent}>
                    <View style={styles.dashboardTitleSection}>
                        <Image
                            source={require('./logo.png')}
                            style={styles.dashboardLogo}
                            resizeMode="contain"
                        />
                        <Text style={[styles.dashboardTitle, { color: colors.textPrimary }]}>
                            E-Tongue Dashboard
                        </Text>
                    </View>
                    <View style={styles.connectionStatus}>
                        <View style={[styles.statusDot, { backgroundColor: colors.success }]} />
                        <Text style={[styles.connectionStatusText, { color: colors.success }]}>
                            Connected
                        </Text>
                    </View>
                </View>
                <View style={styles.streamingControls}>
                    <View style={styles.streamingStatus}>
                        <View style={[styles.statusDot, { backgroundColor: colors.success }]} />
                        <Text style={[styles.streamingText, { color: colors.success }]}>
                            Streaming Active
                        </Text>
                    </View>
                    <TouchableOpacity
                        style={[styles.stopButton, { backgroundColor: colors.stopButton }]}
                        onPress={disconnectDevice}
                    >
                        <Text style={styles.stopButtonText}>✕ Stop</Text>
                    </TouchableOpacity>
                </View>
            </View>

            {/* Sensor Readings Section */}
            <View style={styles.section}>
                <Text style={[styles.sectionTitle, { color: colors.textPrimary }]}>
                    Sensor Readings
                </Text>
                <View style={styles.sensorGrid}>
                    {/* pH Sensor */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('ph')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>pH</Text>
                        <Text style={[styles.sensorValue, { color: '#2196F3' }]}>
                            {sensorData.ph}
                        </Text>
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>pH</Text>
                    </View>

                    {/* TDS Sensor */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('tds')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>TDS</Text>
                        <Text style={[styles.sensorValue, { color: colors.textPrimary }]}>
                            {sensorData.tds}
                        </Text>
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>ppm</Text>
                    </View>

                    {/* Temperature Sensor */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('temperature')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>Temperature</Text>
                        <Text style={[styles.sensorValue, { color: '#4CAF50' }]}>
                            {sensorData.temperature}
                        </Text>
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>°C</Text>
                    </View>

                    {/* UV Absorbance */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('uv')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>UV Absorbance</Text>
                        <Text style={[styles.sensorValue, { color: colors.textPrimary }]}>
                            {sensorData.uv}
                        </Text>
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>AU</Text>
                    </View>

                    {/* Moisture */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('moisture')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>Moisture</Text>
                        <Text style={[styles.sensorValue, { color: '#4CAF50' }]}>
                            {sensorData.moisture}
                        </Text>
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>%</Text>
                    </View>

                    {/* Color */}
                    <View style={[
                        styles.sensorCard,
                        {
                            backgroundColor: colors.cardBackground,
                            borderColor: getSensorBorderColor('color')
                        }
                    ]}>
                        <Text style={[styles.sensorLabel, { color: colors.textPrimary }]}>Color</Text>
                        <View style={[styles.colorDisplay, { backgroundColor: sensorData.color }]} />
                        <Text style={[styles.sensorUnit, { color: colors.textSecondary }]}>
                            {sensorData.color.toUpperCase()}
                        </Text>
                    </View>
                </View>
            </View>

            {/* Electrode Readings Section */}
            <View style={styles.section}>
                <Text style={[styles.sectionTitle, { color: colors.textPrimary }]}>
                    Electrode Readings
                </Text>
                <View style={styles.electrodeGrid}>
                    {Object.entries(electrodeData).map(([electrode, value]) => (
                        <View
                            key={electrode}
                            style={[
                                styles.electrodeCard,
                                {
                                    backgroundColor: colors.cardBackground,
                                    borderColor: colors.border
                                }
                            ]}
                        >
                            <Text style={[styles.electrodeLabel, { color: colors.textPrimary }]}>
                                {electrode.toUpperCase()}
                            </Text>
                            <Text style={[styles.electrodeValue, { color: colors.textPrimary }]}>
                                {value}
                            </Text>
                            <Text style={[styles.electrodeUnit, { color: colors.textSecondary }]}>
                                mV
                            </Text>
                        </View>
                    ))}
                </View>
            </View>

            {/* Device Information */}
            <View style={[styles.deviceInfoSection, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
                <View style={styles.deviceInfoRow}>
                    <Text style={[styles.deviceInfoLabel, { color: colors.textSecondary }]}>
                        Device ID
                    </Text>
                    <Text style={[styles.deviceInfoLabel, { color: colors.textSecondary }]}>
                        Last Update
                    </Text>
                </View>
                <View style={styles.deviceInfoRow}>
                    <Text style={[styles.deviceInfoValue, { color: colors.textPrimary }]}>
                        {connectedDevice?.name || 'ESP32_MOCK_001'}
                    </Text>
                    <Text style={[styles.deviceInfoValue, { color: colors.textPrimary }]}>
                        Just now
                    </Text>
                </View>
            </View>
        </ScrollView>
    );

    // Main Render
    return (
        <SafeAreaView style={[styles.safeArea, { backgroundColor: colors.background }]}>
            <StatusBar
                barStyle={theme === 'dark' ? 'light-content' : 'dark-content'}
                backgroundColor={colors.background}
            />
            {!isConnected ? <ConnectionScreen /> : <DashboardScreen />}
        </SafeAreaView>
    );
};

// Comprehensive Styles
const styles = StyleSheet.create({
    safeArea: {
        flex: 1,
    },
    container: {
        flex: 1,
        paddingHorizontal: 16,
    },
    header: {
        paddingVertical: 20,
    },
    headerWithLogo: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 8,
    },
    logo: {
        width: 32,
        height: 32,
        marginRight: 12,
    },
    headerTitle: {
        fontSize: 24,
        fontWeight: 'bold',
    },
    teamAttribution: {
        fontSize: 12,
        fontStyle: 'italic',
    },
    statusCard: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: 16,
        borderRadius: 12,
        borderWidth: 1,
        marginBottom: 20,
    },
    statusContent: {
        flex: 1,
    },
    statusTitle: {
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 4,
    },
    statusText: {
        fontSize: 14,
    },
    statusIcon: {
        fontSize: 20,
        fontWeight: 'bold',
    },
    filterSection: {
        marginBottom: 20,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: '600',
        marginBottom: 12,
    },
    filterButtons: {
        flexDirection: 'row',
        gap: 8,
    },
    filterButton: {
        paddingHorizontal: 16,
        paddingVertical: 8,
        borderRadius: 20,
        borderWidth: 1,
    },
    filterButtonText: {
        fontSize: 14,
        fontWeight: '500',
    },
    devicesSection: {
        marginBottom: 20,
    },
    devicesSectionHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
    },
    scanButton: {
        paddingHorizontal: 16,
        paddingVertical: 8,
        borderRadius: 20,
        minWidth: 80,
        alignItems: 'center',
    },
    scanButtonText: {
        color: '#ffffff',
        fontSize: 14,
        fontWeight: '600',
    },
    emptyState: {
        alignItems: 'center',
        paddingVertical: 40,
    },
    emptyStateIcon: {
        fontSize: 48,
        marginBottom: 16,
    },
    emptyStateTitle: {
        fontSize: 18,
        fontWeight: '600',
        marginBottom: 8,
    },
    emptyStateText: {
        fontSize: 14,
        textAlign: 'center',
    },
    deviceCard: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: 16,
        borderRadius: 12,
        borderWidth: 1,
        marginBottom: 12,
    },
    deviceInfo: {
        flex: 1,
    },
    deviceName: {
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 4,
    },
    deviceMac: {
        fontSize: 12,
        marginBottom: 2,
    },
    deviceType: {
        fontSize: 12,
    },
    deviceActions: {
        alignItems: 'center',
        gap: 8,
    },
    signalStrength: {
        flexDirection: 'row',
        gap: 2,
        alignItems: 'flex-end',
    },
    signalBar: {
        width: 3,
        borderRadius: 1,
    },
    connectButton: {
        paddingHorizontal: 16,
        paddingVertical: 8,
        borderRadius: 16,
    },
    connectButtonText: {
        color: '#ffffff',
        fontSize: 12,
        fontWeight: '600',
    },
    dashboardHeader: {
        padding: 16,
        borderRadius: 12,
        borderWidth: 1,
        marginVertical: 16,
    },
    dashboardHeaderContent: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 12,
    },
    dashboardTitleSection: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    dashboardLogo: {
        width: 24,
        height: 24,
        marginRight: 8,
    },
    dashboardTitle: {
        fontSize: 20,
        fontWeight: 'bold',
    },
    connectionStatus: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    statusDot: {
        width: 8,
        height: 8,
        borderRadius: 4,
    },
    connectionStatusText: {
        fontSize: 14,
        fontWeight: '600',
    },
    streamingControls: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    streamingStatus: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    streamingText: {
        fontSize: 14,
        fontWeight: '500',
    },
    stopButton: {
        paddingHorizontal: 16,
        paddingVertical: 8,
        borderRadius: 16,
    },
    stopButtonText: {
        color: '#ffffff',
        fontSize: 14,
        fontWeight: '600',
    },
    section: {
        marginBottom: 24,
    },
    sensorGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 8,
    },
    sensorCard: {
        width: (width - 48) / 3,
        padding: 12,
        borderRadius: 12,
        borderWidth: 2,
        alignItems: 'center',
        minHeight: 100,
    },
    sensorLabel: {
        fontSize: 12,
        fontWeight: '600',
        marginBottom: 8,
        textAlign: 'center',
    },
    sensorValue: {
        fontSize: 18,
        fontWeight: 'bold',
        marginBottom: 4,
    },
    sensorUnit: {
        fontSize: 10,
    },
    colorDisplay: {
        width: 24,
        height: 24,
        borderRadius: 12,
        marginBottom: 4,
    },
    electrodeGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 8,
    },
    electrodeCard: {
        width: (width - 56) / 4,
        padding: 8,
        borderRadius: 8,
        borderWidth: 1,
        alignItems: 'center',
        minHeight: 80,
    },
    electrodeLabel: {
        fontSize: 10,
        fontWeight: '600',
        marginBottom: 6,
    },
    electrodeValue: {
        fontSize: 12,
        fontWeight: 'bold',
        marginBottom: 2,
    },
    electrodeUnit: {
        fontSize: 8,
    },
    deviceInfoSection: {
        padding: 16,
        borderRadius: 12,
        borderWidth: 1,
        marginBottom: 20,
    },
    deviceInfoRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 4,
    },
    deviceInfoLabel: {
        fontSize: 12,
        fontWeight: '500',
    },
    deviceInfoValue: {
        fontSize: 14,
        fontWeight: '600',
    },
});

export default AyuSureApp;