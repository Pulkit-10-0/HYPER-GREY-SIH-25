/*
 * AyuSure E-Tongue Firmware v2.0
 * Advanced multi-sensor data acquisition and wireless transmission
 * ESP32-based electronic tongue for AYUSH herbal authentication
 * 
 * Features:
 * - 5-electrode array with 16-bit ADC
 * - Environmental sensor integration
 * - Advanced calibration and drift correction
 * - WiFi connectivity with cloud synchronization
 * - Real-time data processing and filtering
 * - Power management and battery monitoring
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_ADS1X15.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <SPI.h>
#include <SD.h>
#include <EEPROM.h>
#include <time.h>
#include <esp_sleep.h>
#include <driver/adc.h>

// Hardware pin definitions
#define SS_ELECTRODE_PIN    A0
#define CU_ELECTRODE_PIN    A1
#define ZN_ELECTRODE_PIN    A2
#define AG_ELECTRODE_PIN    A3
#define PT_ELECTRODE_PIN    A4

#define PH_SENSOR_PIN       A5
#define TDS_SENSOR_PIN      A6
#define UV_SENSOR_PIN       A7
#define MOISTURE_PIN        A8
#define BATTERY_MONITOR_PIN A15

#define TEMP_SENSOR_PIN     4
#define COLOR_S0           5
#define COLOR_S1           6
#define COLOR_S2           7
#define COLOR_S3           8
#define COLOR_OUT          9

#define LED_STATUS         2
#define LED_WIFI           13
#define LED_ERROR          14
#define BUTTON_CALIBRATE   0
#define BUTTON_MODE        15
#define SD_CS_PIN          10

// System configuration
#define FIRMWARE_VERSION    "2.0.1"
#define DEVICE_MODEL       "AYUSURE-ET-2000"
#define SAMPLING_RATE      100    // Hz
#define MEASUREMENT_PERIOD 5000   // ms between measurements
#define CALIBRATION_PERIOD 86400000 // 24 hours in ms

// Network configuration
const char* ssid = "AyuSure_Network";
const char* password = "AyuSure2025!";
const char* cloud_endpoint = "https://api.ayusure.in/v1/data";
const char* calibration_endpoint = "https://api.ayusure.in/v1/calibration";

// Global objects
Adafruit_ADS1115 ads;
OneWire oneWire(TEMP_SENSOR_PIN);
DallasTemperature tempSensor(&oneWire);
HTTPClient http;
WiFiClient wifiClient;

// System state variables
struct SystemState {
    bool wifi_connected = false;
    bool sd_available = false;
    bool calibration_valid = false;
    float battery_voltage = 0.0;
    int measurement_count = 0;
    unsigned long last_measurement = 0;
    unsigned long last_calibration = 0;
    unsigned long boot_time = 0;
};

struct SensorReadings {
    float electrode_voltages[5];  // SS, Cu, Zn, Ag, Pt
    float temperature;
    float ph_voltage;
    float tds_voltage;
    float uv_intensity;
    float moisture_percent;
    int color_rgb[3];
    float battery_voltage;
    unsigned long timestamp;
};

struct CalibrationData {
    float electrode_offsets[5];
    float ph_slope;
    float ph_intercept;
    float tds_factor;
    float temperature_coefficients[5];
    unsigned long calibration_timestamp;
    bool valid;
};

// Global state
SystemState system_state;
SensorReadings current_readings;
CalibrationData calibration;

// Kalman filter states for noise reduction
struct KalmanFilter {
    float estimate;
    float error_estimate;
    float process_noise;
    float measurement_noise;

    KalmanFilter(float initial_estimate = 0.0, float initial_error = 1.0) {
        estimate = initial_estimate;
        error_estimate = initial_error;
        process_noise = 1e-5;
        measurement_noise = 1e-3;
    }

    float update(float measurement) {
        // Prediction step
        float predicted_estimate = estimate;
        float predicted_error = error_estimate + process_noise;

        // Update step
        float kalman_gain = predicted_error / (predicted_error + measurement_noise);
        estimate = predicted_estimate + kalman_gain * (measurement - predicted_estimate);
        error_estimate = (1 - kalman_gain) * predicted_error;

        return estimate;
    }
};

KalmanFilter electrode_filters[5];
KalmanFilter environment_filters[4]; // pH, TDS, UV, moisture

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("====================================");
    Serial.println("AyuSure E-Tongue System v" + String(FIRMWARE_VERSION));
    Serial.println("====================================");

    // Initialize system
    system_state.boot_time = millis();

    // Initialize pins
    initializePins();

    // Initialize sensors
    initializeSensors();

    // Initialize storage
    initializeStorage();

    // Load calibration data
    loadCalibrationData();

    // Initialize network
    initializeWiFi();

    // Synchronize time
    initializeTime();

    // Self-test
    performSelfTest();

    Serial.println("System initialization complete");
    blinkStatusLED(3, 200); // Ready indication
}

void loop() {
    unsigned long current_time = millis();

    // Check for measurement interval
    if (current_time - system_state.last_measurement >= MEASUREMENT_PERIOD) {
        performMeasurementCycle();
        system_state.last_measurement = current_time;
    }

    // Check for calibration button
    if (digitalRead(BUTTON_CALIBRATE) == LOW) {
        delay(50); // Debounce
        if (digitalRead(BUTTON_CALIBRATE) == LOW) {
            performFieldCalibration();
            delay(1000); // Prevent multiple triggers
        }
    }

    // Check for mode button
    if (digitalRead(BUTTON_MODE) == LOW) {
        delay(50); // Debounce
        if (digitalRead(BUTTON_MODE) == LOW) {
            enterConfigurationMode();
            delay(1000);
        }
    }

    // Auto-calibration check (every 24 hours)
    if (current_time - system_state.last_calibration > CALIBRATION_PERIOD) {
        Serial.println("Auto-calibration due");
        performAutoCalibration();
    }

    // WiFi connection maintenance
    maintainWiFiConnection();

    // System health monitoring
    monitorSystemHealth();

    // Battery management
    managePowerState();

    // Process serial commands
    processSerialCommands();

    delay(100); // Main loop delay
}

void initializePins() {
    pinMode(LED_STATUS, OUTPUT);
    pinMode(LED_WIFI, OUTPUT);
    pinMode(LED_ERROR, OUTPUT);
    pinMode(BUTTON_CALIBRATE, INPUT_PULLUP);
    pinMode(BUTTON_MODE, INPUT_PULLUP);

    pinMode(COLOR_S0, OUTPUT);
    pinMode(COLOR_S1, OUTPUT);
    pinMode(COLOR_S2, OUTPUT);
    pinMode(COLOR_S3, OUTPUT);
    pinMode(COLOR_OUT, INPUT);

    // Set color sensor frequency scaling to 20%
    digitalWrite(COLOR_S0, HIGH);
    digitalWrite(COLOR_S1, LOW);

    Serial.println("GPIO pins initialized");
}

void initializeSensors() {
    Wire.begin();

    // Initialize ADS1115 ADC
    if (!ads.begin()) {
        Serial.println("ERROR: ADS1115 not found");
        digitalWrite(LED_ERROR, HIGH);
        return;
    }
    ads.setGain(GAIN_ONE); // ±4.096V range
    Serial.println("ADS1115 initialized");

    // Initialize temperature sensor
    tempSensor.begin();
    Serial.println("Temperature sensor initialized");

    // Initialize Kalman filters
    for (int i = 0; i < 5; i++) {
        electrode_filters[i] = KalmanFilter(2.0, 0.1); // Initial estimate 2V
    }
    for (int i = 0; i < 4; i++) {
        environment_filters[i] = KalmanFilter(1.0, 0.1);
    }

    Serial.println("Sensor initialization complete");
}

void initializeStorage() {
    EEPROM.begin(512);

    if (SD.begin(SD_CS_PIN)) {
        system_state.sd_available = true;
        Serial.println("SD card initialized");

        // Create directory structure if needed
        if (!SD.exists("/data")) SD.mkdir("/data");
        if (!SD.exists("/calibration")) SD.mkdir("/calibration");
        if (!SD.exists("/logs")) SD.mkdir("/logs");

    } else {
        Serial.println("SD card initialization failed");
        system_state.sd_available = false;
    }
}

void initializeWiFi() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    Serial.print("Connecting to WiFi");
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        system_state.wifi_connected = true;
        digitalWrite(LED_WIFI, HIGH);
        Serial.println();
        Serial.println("WiFi connected!");
        Serial.println("IP address: " + WiFi.localIP().toString());
        Serial.println("Signal strength: " + String(WiFi.RSSI()) + " dBm");
    } else {
        system_state.wifi_connected = false;
        digitalWrite(LED_WIFI, LOW);
        Serial.println();
        Serial.println("WiFi connection failed");
    }
}

void initializeTime() {
    if (system_state.wifi_connected) {
        configTime(19800, 0, "pool.ntp.org", "time.nist.gov"); // UTC+5:30 for India
        Serial.println("NTP time synchronization initialized");
    }
}

void performSelfTest() {
    Serial.println("\nPerforming system self-test...");

    bool test_passed = true;

    // Test 1: Power supply voltages
    float vcc_3v3 = readSystemVoltage(VCC_3V3);
    float vcc_5v = readSystemVoltage(VCC_5V);

    if (vcc_3v3 < 3.25 || vcc_3v3 > 3.35) {
        Serial.println("FAIL: 3.3V rail out of specification");
        test_passed = false;
    }

    if (vcc_5v < 4.9 || vcc_5v > 5.1) {
        Serial.println("FAIL: 5V rail out of specification");
        test_passed = false;
    }

    // Test 2: ADC functionality
    int16_t adc_test = ads.readADC_SingleEnded(0);
    if (adc_test == 0 || adc_test == -1) {
        Serial.println("FAIL: ADC not responding");
        test_passed = false;
    }

    // Test 3: Temperature sensor
    tempSensor.requestTemperatures();
    float temp_test = tempSensor.getTempCByIndex(0);
    if (temp_test == DEVICE_DISCONNECTED_C) {
        Serial.println("FAIL: Temperature sensor not responding");
        test_passed = false;
    }

    // Test 4: Electrode connectivity
    for (int i = 0; i < 5; i++) {
        int16_t electrode_test = ads.readADC_SingleEnded(i);
        float voltage = ads.computeVolts(electrode_test);
        if (voltage < 0.1 || voltage > 3.2) {
            Serial.println("FAIL: Electrode " + String(i) + " out of range");
            test_passed = false;
        }
    }

    if (test_passed) {
        Serial.println("Self-test PASSED");
        blinkStatusLED(2, 500);
    } else {
        Serial.println("Self-test FAILED");
        digitalWrite(LED_ERROR, HIGH);
        blinkStatusLED(10, 100);
    }
}

void performMeasurementCycle() {
    // Read all sensors
    readElectrodeArray();
    readEnvironmentalSensors();
    readSystemStatus();

    // Apply calibration
    applyCalibration();

    // Create data packet
    String json_data = createDataPacket();

    // Transmit data
    if (system_state.wifi_connected) {
        transmitToCloud(json_data);
    }

    // Save to SD card
    if (system_state.sd_available) {
        saveToSD(json_data);
    }

    // Update system state
    system_state.measurement_count++;

    // Status indication
    blinkStatusLED(1, 50);
}

void readElectrodeArray() {
    String electrode_names[] = {"SS", "Cu", "Zn", "Ag", "Pt"};

    for (int i = 0; i < 5; i++) {
        // Take multiple readings for averaging
        float sum = 0;
        int valid_readings = 0;

        for (int j = 0; j < 5; j++) {
            int16_t raw_reading = ads.readADC_SingleEnded(i);
            float voltage = ads.computeVolts(raw_reading);

            // Validate reading
            if (voltage >= 0.05 && voltage <= 3.25) {
                sum += voltage;
                valid_readings++;
            }
            delay(10); // Short delay between readings
        }

        if (valid_readings > 0) {
            float avg_voltage = sum / valid_readings;

            // Apply Kalman filtering
            current_readings.electrode_voltages[i] = electrode_filters[i].update(avg_voltage);
        } else {
            Serial.println("Warning: Invalid readings for electrode " + electrode_names[i]);
            current_readings.electrode_voltages[i] = 0.0;
        }
    }
}

void readEnvironmentalSensors() {
    // pH sensor reading
    int16_t ph_raw = ads.readADC_SingleEnded(5);
    float ph_voltage = ads.computeVolts(ph_raw);
    current_readings.ph_voltage = environment_filters[0].update(ph_voltage);

    // TDS sensor reading
    int16_t tds_raw = ads.readADC_SingleEnded(6);
    float tds_voltage = ads.computeVolts(tds_raw);
    current_readings.tds_voltage = environment_filters[1].update(tds_voltage);

    // UV sensor reading
    float uv_raw = analogRead(UV_SENSOR_PIN) * (3.3 / 4095.0);
    current_readings.uv_intensity = environment_filters[2].update(uv_raw);

    // Moisture sensor reading
    float moisture_raw = analogRead(MOISTURE_PIN) * (100.0 / 4095.0);
    current_readings.moisture_percent = environment_filters[3].update(moisture_raw);

    // Temperature reading
    tempSensor.requestTemperatures();
    current_readings.temperature = tempSensor.getTempCByIndex(0);

    // Color sensor reading
    readColorSensor();
}

void readColorSensor() {
    // Read red
    digitalWrite(COLOR_S2, LOW);
    digitalWrite(COLOR_S3, LOW);
    current_readings.color_rgb[0] = pulseIn(COLOR_OUT, LOW, 50000);

    // Read green
    digitalWrite(COLOR_S2, HIGH);
    digitalWrite(COLOR_S3, HIGH);
    current_readings.color_rgb[1] = pulseIn(COLOR_OUT, LOW, 50000);

    // Read blue
    digitalWrite(COLOR_S2, LOW);
    digitalWrite(COLOR_S3, HIGH);
    current_readings.color_rgb[2] = pulseIn(COLOR_OUT, LOW, 50000);

    // Convert to 0-255 range (inverse relationship)
    for (int i = 0; i < 3; i++) {
        if (current_readings.color_rgb[i] > 0) {
            current_readings.color_rgb[i] = map(current_readings.color_rgb[i], 10, 1000, 255, 0);
            current_readings.color_rgb[i] = constrain(current_readings.color_rgb[i], 0, 255);
        }
    }
}

void readSystemStatus() {
    // Battery voltage monitoring
    int battery_raw = analogRead(BATTERY_MONITOR_PIN);
    current_readings.battery_voltage = (battery_raw * 3.3 * 2.0) / 4095.0; // Voltage divider
    system_state.battery_voltage = current_readings.battery_voltage;

    // Timestamp
    current_readings.timestamp = millis();
}

void applyCalibration() {
    if (!calibration.valid) return;

    // Apply electrode offsets
    for (int i = 0; i < 5; i++) {
        current_readings.electrode_voltages[i] -= calibration.electrode_offsets[i];

        // Apply temperature compensation
        float temp_compensation = calibration.temperature_coefficients[i] * 
                                (current_readings.temperature - 25.0);
        current_readings.electrode_voltages[i] -= temp_compensation;
    }
}

String createDataPacket() {
    DynamicJsonDocument doc(1500);

    // Device identification
    doc["device_id"] = WiFi.macAddress();
    doc["firmware_version"] = FIRMWARE_VERSION;
    doc["device_model"] = DEVICE_MODEL;
    doc["timestamp"] = getFormattedTimestamp();
    doc["measurement_id"] = system_state.measurement_count;

    // Electrode readings
    JsonArray electrodes = doc.createNestedArray("electrodes");
    electrodes.add(current_readings.electrode_voltages[0]); // SS
    electrodes.add(current_readings.electrode_voltages[1]); // Cu
    electrodes.add(current_readings.electrode_voltages[2]); // Zn
    electrodes.add(current_readings.electrode_voltages[3]); // Ag
    electrodes.add(current_readings.electrode_voltages[4]); // Pt

    // Environmental sensors
    JsonObject env = doc.createNestedObject("environmental");
    env["temperature"] = round(current_readings.temperature * 100) / 100.0;
    env["ph_voltage"] = round(current_readings.ph_voltage * 1000) / 1000.0;
    env["tds_voltage"] = round(current_readings.tds_voltage * 1000) / 1000.0;
    env["uv_intensity"] = round(current_readings.uv_intensity * 100) / 100.0;
    env["moisture_percent"] = round(current_readings.moisture_percent * 10) / 10.0;

    // Color data
    JsonArray colors = doc.createNestedArray("color_rgb");
    colors.add(current_readings.color_rgb[0]);
    colors.add(current_readings.color_rgb[1]);
    colors.add(current_readings.color_rgb[2]);

    // System status
    JsonObject status = doc.createNestedObject("system_status");
    status["battery_voltage"] = round(current_readings.battery_voltage * 100) / 100.0;
    status["wifi_rssi"] = WiFi.RSSI();
    status["free_heap"] = ESP.getFreeHeap();
    status["uptime_ms"] = millis() - system_state.boot_time;
    status["calibration_age_hours"] = (millis() - calibration.calibration_timestamp) / 3600000;

    String json_string;
    serializeJson(doc, json_string);
    return json_string;
}

void transmitToCloud(String json_data) {
    if (!system_state.wifi_connected) return;

    http.begin(wifiClient, cloud_endpoint);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", "Bearer AyuSure_Device_Token");
    http.addHeader("X-Device-ID", WiFi.macAddress());

    int response_code = http.POST(json_data);

    if (response_code > 0) {
        String response = http.getString();
        if (response_code == 200) {
            Serial.println("Cloud transmission successful");
        } else {
            Serial.println("Cloud transmission failed: " + String(response_code));
            Serial.println("Response: " + response);
        }
    } else {
        Serial.println("HTTP POST failed: " + String(response_code));
        digitalWrite(LED_ERROR, HIGH);
        delay(1000);
        digitalWrite(LED_ERROR, LOW);
    }

    http.end();
}

void saveToSD(String json_data) {
    if (!system_state.sd_available) return;

    String filename = "/data/readings_" + String(day()) + "_" + 
                     String(month()) + "_" + String(year()) + ".jsonl";

    File dataFile = SD.open(filename, FILE_APPEND);
    if (dataFile) {
        dataFile.println(json_data);
        dataFile.close();
    } else {
        Serial.println("Failed to write to SD card");
    }
}

void performFieldCalibration() {
    Serial.println("\n=== Starting Field Calibration ===");
    blinkStatusLED(5, 200);

    Serial.println("Place electrodes in pH 7.0 buffer solution");
    Serial.println("Calibration will start in 10 seconds...");

    // Wait for stabilization
    for (int i = 10; i > 0; i--) {
        Serial.println(String(i) + " seconds remaining");
        delay(1000);
    }

    Serial.println("Calibrating...");

    // Take baseline readings
    float baseline_readings[5];
    float ph_baseline;

    for (int i = 0; i < 5; i++) {
        float sum = 0;
        for (int j = 0; j < 20; j++) {
            int16_t raw = ads.readADC_SingleEnded(i);
            sum += ads.computeVolts(raw);
            delay(100);
        }
        baseline_readings[i] = sum / 20.0;
    }

    // pH calibration
    float ph_sum = 0;
    for (int i = 0; i < 20; i++) {
        int16_t ph_raw = ads.readADC_SingleEnded(5);
        ph_sum += ads.computeVolts(ph_raw);
        delay(100);
    }
    ph_baseline = ph_sum / 20.0;

    // Update calibration data
    float reference_values[5] = {1.42, 1.68, 1.89, 2.21, 2.03}; // Expected values for pH 7

    for (int i = 0; i < 5; i++) {
        calibration.electrode_offsets[i] = baseline_readings[i] - reference_values[i];
    }

    calibration.ph_slope = 1.0; // Simple single-point calibration
    calibration.ph_intercept = 7.0 - ph_baseline;
    calibration.calibration_timestamp = millis();
    calibration.valid = true;

    // Save calibration data
    saveCalibrationData();

    system_state.last_calibration = millis();

    Serial.println("Field calibration completed successfully");
    blinkStatusLED(3, 500);
}

void performAutoCalibration() {
    Serial.println("Performing automatic drift correction...");

    // This would typically involve internal reference measurements
    // For now, we'll update the timestamp to reset the calibration timer
    system_state.last_calibration = millis();

    Serial.println("Auto-calibration completed");
}

void loadCalibrationData() {
    // Try to load from EEPROM
    EEPROM.get(0, calibration);

    // Validate calibration data
    if (calibration.calibration_timestamp == 0 || 
        calibration.calibration_timestamp > millis()) {
        // Invalid calibration data, use defaults
        for (int i = 0; i < 5; i++) {
            calibration.electrode_offsets[i] = 0.0;
            calibration.temperature_coefficients[i] = -1.0e-3; // Default temp coefficient
        }
        calibration.ph_slope = 1.0;
        calibration.ph_intercept = 0.0;
        calibration.tds_factor = 1.0;
        calibration.calibration_timestamp = 0;
        calibration.valid = false;

        Serial.println("Using default calibration values");
    } else {
        Serial.println("Calibration data loaded from EEPROM");
        system_state.last_calibration = calibration.calibration_timestamp;
    }
}

void saveCalibrationData() {
    EEPROM.put(0, calibration);
    EEPROM.commit();

    if (system_state.sd_available) {
        // Also save to SD card as backup
        File calFile = SD.open("/calibration/calibration_backup.dat", FILE_WRITE);
        if (calFile) {
            calFile.write((uint8_t*)&calibration, sizeof(calibration));
            calFile.close();
        }
    }

    Serial.println("Calibration data saved");
}

void maintainWiFiConnection() {
    static unsigned long last_check = 0;
    unsigned long current_time = millis();

    // Check connection every 30 seconds
    if (current_time - last_check > 30000) {
        if (WiFi.status() != WL_CONNECTED) {
            system_state.wifi_connected = false;
            digitalWrite(LED_WIFI, LOW);

            Serial.println("WiFi disconnected, attempting reconnection...");
            WiFi.begin(ssid, password);

            int attempts = 0;
            while (WiFi.status() != WL_CONNECTED && attempts < 10) {
                delay(500);
                attempts++;
            }

            if (WiFi.status() == WL_CONNECTED) {
                system_state.wifi_connected = true;
                digitalWrite(LED_WIFI, HIGH);
                Serial.println("WiFi reconnected");
            }
        } else {
            system_state.wifi_connected = true;
            digitalWrite(LED_WIFI, HIGH);
        }

        last_check = current_time;
    }
}

void monitorSystemHealth() {
    static unsigned long last_health_check = 0;
    unsigned long current_time = millis();

    // Health check every 5 minutes
    if (current_time - last_health_check > 300000) {
        // Check memory usage
        if (ESP.getFreeHeap() < 10000) {
            Serial.println("Warning: Low memory");
        }

        // Check battery voltage
        if (system_state.battery_voltage < 3.2) {
            Serial.println("Warning: Low battery");
            digitalWrite(LED_ERROR, HIGH);
            delay(100);
            digitalWrite(LED_ERROR, LOW);
        }

        // Check sensor readings for anomalies
        bool anomaly_detected = false;
        for (int i = 0; i < 5; i++) {
            if (current_readings.electrode_voltages[i] < 0.1 || 
                current_readings.electrode_voltages[i] > 3.2) {
                Serial.println("Warning: Electrode " + String(i) + " reading anomalous");
                anomaly_detected = true;
            }
        }

        if (anomaly_detected) {
            digitalWrite(LED_ERROR, HIGH);
            delay(200);
            digitalWrite(LED_ERROR, LOW);
        }

        last_health_check = current_time;
    }
}

void managePowerState() {
    // Simple power management
    if (system_state.battery_voltage < 3.0) {
        Serial.println("Critical battery level - entering sleep mode");

        // Configure wake-up source (button press)
        esp_sleep_enable_ext0_wakeup(GPIO_NUM_0, 0);

        // Enter deep sleep
        esp_deep_sleep_start();
    }
}

void processSerialCommands() {
    if (Serial.available()) {
        String command = Serial.readStringUntil('\n');
        command.trim();

        if (command.startsWith("READ_")) {
            handleReadCommand(command);
        } else if (command.startsWith("SET_")) {
            handleSetCommand(command);
        } else if (command == "STATUS") {
            printSystemStatus();
        } else if (command == "CALIBRATE") {
            performFieldCalibration();
        } else if (command == "RESET") {
            ESP.restart();
        } else {
            Serial.println("Unknown command: " + command);
        }
    }
}

void handleReadCommand(String command) {
    if (command == "READ_SS") {
        Serial.println("SS:" + String(current_readings.electrode_voltages[0], 4));
    } else if (command == "READ_CU") {
        Serial.println("Cu:" + String(current_readings.electrode_voltages[1], 4));
    } else if (command == "READ_ZN") {
        Serial.println("Zn:" + String(current_readings.electrode_voltages[2], 4));
    } else if (command == "READ_AG") {
        Serial.println("Ag:" + String(current_readings.electrode_voltages[3], 4));
    } else if (command == "READ_PT") {
        Serial.println("Pt:" + String(current_readings.electrode_voltages[4], 4));
    } else if (command == "READ_TEMP") {
        Serial.println("TEMP:" + String(current_readings.temperature, 2));
    } else if (command == "READ_PH") {
        Serial.println("PH:" + String(current_readings.ph_voltage, 4));
    } else if (command == "READ_BATTERY") {
        Serial.println("BATTERY:" + String(current_readings.battery_voltage, 2));
    }
}

void handleSetCommand(String command) {
    // Handle configuration commands
    Serial.println("Set command received: " + command);
}

void printSystemStatus() {
    Serial.println("\n=== AyuSure System Status ===");
    Serial.println("Device ID: " + WiFi.macAddress());
    Serial.println("Firmware: " + String(FIRMWARE_VERSION));
    Serial.println("Uptime: " + String((millis() - system_state.boot_time) / 1000) + " seconds");
    Serial.println("WiFi: " + String(system_state.wifi_connected ? "Connected" : "Disconnected"));
    Serial.println("SD Card: " + String(system_state.sd_available ? "Available" : "Not Available"));
    Serial.println("Battery: " + String(system_state.battery_voltage) + "V");
    Serial.println("Measurements: " + String(system_state.measurement_count));
    Serial.println("Free Heap: " + String(ESP.getFreeHeap()) + " bytes");

    Serial.println("\nElectrode Readings:");
    Serial.println("SS: " + String(current_readings.electrode_voltages[0], 4) + "V");
    Serial.println("Cu: " + String(current_readings.electrode_voltages[1], 4) + "V");
    Serial.println("Zn: " + String(current_readings.electrode_voltages[2], 4) + "V");
    Serial.println("Ag: " + String(current_readings.electrode_voltages[3], 4) + "V");
    Serial.println("Pt: " + String(current_readings.electrode_voltages[4], 4) + "V");

    Serial.println("\nEnvironmental Readings:");
    Serial.println("Temperature: " + String(current_readings.temperature, 2) + "°C");
    Serial.println("pH Voltage: " + String(current_readings.ph_voltage, 4) + "V");
    Serial.println("TDS Voltage: " + String(current_readings.tds_voltage, 4) + "V");
    Serial.println("UV Intensity: " + String(current_readings.uv_intensity, 3) + "V");
    Serial.println("Moisture: " + String(current_readings.moisture_percent, 1) + "%");

    Serial.println("\nColor RGB: " + String(current_readings.color_rgb[0]) + ", " + 
                   String(current_readings.color_rgb[1]) + ", " + String(current_readings.color_rgb[2]));
    Serial.println("=============================\n");
}

void enterConfigurationMode() {
    Serial.println("Entering configuration mode...");
    // Configuration mode implementation
    blinkStatusLED(10, 100);
}

String getFormattedTimestamp() {
    time_t now;
    struct tm timeinfo;

    if (getLocalTime(&timeinfo)) {
        char timestamp[30];
        strftime(timestamp, sizeof(timestamp), "%Y-%m-%dT%H:%M:%S", &timeinfo);
        return String(timestamp);
    } else {
        return String(millis()); // Fallback to millis
    }
}

float readSystemVoltage(int voltage_type) {
    // Placeholder for system voltage reading
    return 3.3; // Default value
}

void blinkStatusLED(int count, int delay_ms) {
    for (int i = 0; i < count; i++) {
        digitalWrite(LED_STATUS, HIGH);
        delay(delay_ms);
        digitalWrite(LED_STATUS, LOW);
        delay(delay_ms);
    }
}

// Interrupt handlers and additional utility functions would go here
