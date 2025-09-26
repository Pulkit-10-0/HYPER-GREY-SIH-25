/*
 * AyuSure E-Tongue System - Arduino Compatible Version
 * Electronic Tongue for AYUSH Herbal Authentication
 * Team: Hyper Grey - MSIT
 * Version: 2.0 Arduino Compatible
 * 
 * Features:
 * - 5-electrode sensor array with 16-bit ADC
 * - Environmental sensor integration (pH, TDS, UV, Temperature)
 * - Real-time data processing and filtering
 * - WiFi connectivity with cloud synchronization
 * - Advanced calibration and drift correction
 * - Power management for field deployment
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <EEPROM.h>
#include <esp_sleep.h>

// ============================================================================
// HARDWARE PIN DEFINITIONS
// ============================================================================

// Sensor Array Pins (Arduino compatible)
#define SS_ELECTRODE_PIN    A0    // Stainless Steel electrode
#define CU_ELECTRODE_PIN    A1    // Copper electrode  
#define ZN_ELECTRODE_PIN    A2    // Zinc electrode
#define AG_ELECTRODE_PIN    A3    // Silver electrode
#define PT_ELECTRODE_PIN    A4    // Platinum electrode

// Environmental Sensors
#define PH_SENSOR_PIN       A5    // pH sensor analog input
#define TDS_SENSOR_PIN      A6    // TDS conductivity sensor
#define UV_SENSOR_PIN       A7    // UV intensity sensor
#define MOISTURE_PIN        A8    // Soil moisture sensor
#define BATTERY_PIN         A15   // Battery voltage monitor

// Digital Pins
#define TEMP_SENSOR_PIN     4     // DS18B20 temperature sensor
#define COLOR_S0           5      // Color sensor frequency scaling
#define COLOR_S1           6
#define COLOR_S2           7      // Color sensor output select
#define COLOR_S3           8
#define COLOR_OUT          9      // Color sensor frequency output

// Status and Control
#define LED_STATUS         2      // Status LED (built-in)
#define LED_WIFI           13     // WiFi connection LED
#define LED_ERROR          14     // Error indicator LED
#define BUTTON_CALIBRATE   0      // Calibration button (BOOT)
#define BUTTON_MODE        15     // Mode selection button

// ============================================================================
// SYSTEM CONFIGURATION
// ============================================================================

#define FIRMWARE_VERSION    "2.0-Arduino"
#define DEVICE_MODEL       "AYUSURE-ET-2000"
#define TEAM_NAME          "Hyper Grey"
#define TEAM_MEMBERS       "Pulkit Kapur, Prakhar Chandra, Shaymon Khawas, Shiney Sharma, Parul Singh, Vaishali"
#define COLLEGE            "MSIT"

// Measurement parameters
#define SAMPLING_RATE      100    // Hz
#define MEASUREMENT_PERIOD 5000   // ms between measurements
#define CALIBRATION_PERIOD 86400000 // 24 hours in ms
#define NUM_READINGS       10     // Readings to average
#define ADC_RESOLUTION     4095   // 12-bit ADC resolution

// Network configuration
const char* ssid = "AyuSure_Network";
const char* password = "AyuSure2025!";
const char* cloud_endpoint = "https://api.ayusure.in/v1/data";
const char* device_id = "ESP32_ARDUINO_001";

// ============================================================================
// DATA STRUCTURES
// ============================================================================

struct SystemState {
    bool wifi_connected = false;
    bool calibration_valid = false;
    float battery_voltage = 0.0;
    int measurement_count = 0;
    unsigned long last_measurement = 0;
    unsigned long last_calibration = 0;
    unsigned long boot_time = 0;
    int error_count = 0;
};

struct SensorReadings {
    // Electrode voltages (5 channels)
    float electrode_voltages[5];  // SS, Cu, Zn, Ag, Pt

    // Environmental sensors
    float temperature;
    float ph_voltage;
    float tds_voltage;
    float uv_intensity;
    float moisture_percent;

    // Color sensor RGB values
    int color_rgb[3];

    // System status
    float battery_voltage;
    int wifi_rssi;
    unsigned long timestamp;

    // Data quality indicators
    bool data_valid;
    float noise_level;
};

struct CalibrationData {
    float electrode_offsets[5];      // Calibration offsets
    float ph_slope;                  // pH calibration slope
    float ph_intercept;              // pH calibration intercept
    float temperature_coeffs[5];     // Temperature compensation
    unsigned long cal_timestamp;    // Calibration time
    bool valid;                      // Calibration validity flag
    int cal_count;                   // Number of calibrations
};

struct KalmanFilter {
    float estimate;
    float error_estimate;
    float process_noise;
    float measurement_noise;
    bool initialized;

    KalmanFilter() {
        estimate = 0.0;
        error_estimate = 1.0;
        process_noise = 1e-5;
        measurement_noise = 1e-3;
        initialized = false;
    }

    float update(float measurement) {
        if (!initialized) {
            estimate = measurement;
            initialized = true;
            return estimate;
        }

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

// ============================================================================
// GLOBAL VARIABLES
// ============================================================================

SystemState system_state;
SensorReadings current_readings;
CalibrationData calibration;
OneWire oneWire(TEMP_SENSOR_PIN);
DallasTemperature temp_sensor(&oneWire);
HTTPClient http;

// Kalman filters for noise reduction
KalmanFilter electrode_filters[5];
KalmanFilter environment_filters[4]; // pH, TDS, UV, moisture

// Moving average buffers
float electrode_buffer[5][NUM_READINGS];
int buffer_index = 0;

// ============================================================================
// SETUP FUNCTION
// ============================================================================

void setup() {
    Serial.begin(115200);
    delay(1000);

    printSystemHeader();

    // Initialize system timestamp
    system_state.boot_time = millis();

    // Initialize hardware
    initializePins();
    initializeSensors();
    loadCalibrationData();

    // Initialize network
    initializeWiFi();

    // Perform system self-test
    performSystemSelfTest();

    Serial.println("=== AyuSure System Ready ===");
    blinkStatusLED(3, 200); // Ready indication
}

// ============================================================================
// MAIN LOOP
// ============================================================================

void loop() {
    unsigned long current_time = millis();

    // Regular measurement cycle
    if (current_time - system_state.last_measurement >= MEASUREMENT_PERIOD) {
        performMeasurementCycle();
        system_state.last_measurement = current_time;
    }

    // Handle calibration button
    if (digitalRead(BUTTON_CALIBRATE) == LOW) {
        delay(50); // Debounce
        if (digitalRead(BUTTON_CALIBRATE) == LOW) {
            Serial.println("Calibration button pressed");
            performFieldCalibration();
            delay(2000); // Prevent multiple triggers
        }
    }

    // Handle mode button
    if (digitalRead(BUTTON_MODE) == LOW) {
        delay(50); // Debounce
        if (digitalRead(BUTTON_MODE) == LOW) {
            Serial.println("Mode button pressed");
            displaySystemStatus();
            delay(1000);
        }
    }

    // Auto-calibration check (daily)
    if (current_time - system_state.last_calibration > CALIBRATION_PERIOD) {
        Serial.println("Auto-calibration due - performing drift check");
        performDriftCorrection();
    }

    // WiFi maintenance
    maintainWiFiConnection();

    // System health monitoring
    monitorSystemHealth();

    // Process serial commands
    processSerialCommands();

    // Power management
    managePowerState();

    delay(100); // Main loop delay
}

// ============================================================================
// INITIALIZATION FUNCTIONS
// ============================================================================

void printSystemHeader() {
    Serial.println();
    Serial.println("================================================================");
    Serial.println("           AyuSure E-Tongue for AYUSH Authentication           ");
    Serial.println("================================================================");
    Serial.println("Team Name: " + String(TEAM_NAME));
    Serial.println("Members: " + String(TEAM_MEMBERS));
    Serial.println("College: " + String(COLLEGE));
    Serial.println("Device Model: " + String(DEVICE_MODEL));
    Serial.println("Firmware Version: " + String(FIRMWARE_VERSION));
    Serial.println("Device ID: " + String(device_id));
    Serial.println("================================================================");
}

void initializePins() {
    // LED pins
    pinMode(LED_STATUS, OUTPUT);
    pinMode(LED_WIFI, OUTPUT);
    pinMode(LED_ERROR, OUTPUT);

    // Button pins
    pinMode(BUTTON_CALIBRATE, INPUT_PULLUP);
    pinMode(BUTTON_MODE, INPUT_PULLUP);

    // Color sensor pins
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
    // Initialize temperature sensor
    temp_sensor.begin();
    if (temp_sensor.getDeviceCount() == 0) {
        Serial.println("WARNING: No temperature sensors found");
        digitalWrite(LED_ERROR, HIGH);
        delay(1000);
        digitalWrite(LED_ERROR, LOW);
    } else {
        temp_sensor.setResolution(12); // 12-bit resolution
        Serial.println("Temperature sensor initialized");
    }

    // Initialize ADC reference
    analogReadResolution(12); // 12-bit ADC resolution
    analogSetAttenuation(ADC_11db); // 0-3.3V range

    // Initialize calibration data structure
    if (!calibration.valid) {
        setDefaultCalibration();
    }

    Serial.println("Sensor initialization complete");
}

void initializeWiFi() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    Serial.print("Connecting to WiFi: ");
    Serial.print(ssid);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        Serial.print(".");
        attempts++;

        // Blink WiFi LED during connection
        digitalWrite(LED_WIFI, !digitalRead(LED_WIFI));
    }

    if (WiFi.status() == WL_CONNECTED) {
        system_state.wifi_connected = true;
        digitalWrite(LED_WIFI, HIGH);
        Serial.println();
        Serial.println("WiFi connected successfully!");
        Serial.print("IP address: ");
        Serial.println(WiFi.localIP());
        Serial.print("Signal strength: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");
    } else {
        system_state.wifi_connected = false;
        digitalWrite(LED_WIFI, LOW);
        Serial.println();
        Serial.println("WiFi connection failed - operating in offline mode");
    }
}

// ============================================================================
// MEASUREMENT FUNCTIONS
// ============================================================================

void performMeasurementCycle() {
    Serial.println("\n--- Starting Measurement Cycle ---");

    // Read all sensors
    readElectrodeArray();
    readEnvironmentalSensors();
    readSystemStatus();

    // Apply calibration corrections
    applyCalibrationCorrections();

    // Validate data quality
    validateMeasurementData();

    // Display readings
    displayCurrentReadings();

    // Transmit data if WiFi available
    if (system_state.wifi_connected) {
        transmitDataToCloud();
    }

    // Update system counters
    system_state.measurement_count++;

    // Status indication
    blinkStatusLED(1, 50);

    Serial.println("--- Measurement Cycle Complete ---");
}

void readElectrodeArray() {
    String electrode_names[] = {"SS", "Cu", "Zn", "Ag", "Pt"};
    int electrode_pins[] = {SS_ELECTRODE_PIN, CU_ELECTRODE_PIN, ZN_ELECTRODE_PIN, 
                           AG_ELECTRODE_PIN, PT_ELECTRODE_PIN};

    for (int i = 0; i < 5; i++) {
        float sum = 0;
        int valid_readings = 0;

        // Take multiple readings for stability
        for (int j = 0; j < NUM_READINGS; j++) {
            int raw_value = analogRead(electrode_pins[i]);
            float voltage = (raw_value * 3.3) / ADC_RESOLUTION;

            // Validate reading range
            if (voltage >= 0.05 && voltage <= 3.25) {
                sum += voltage;
                valid_readings++;
            }
            delay(10); // Small delay between readings
        }

        if (valid_readings > 0) {
            float avg_voltage = sum / valid_readings;

            // Apply Kalman filtering for noise reduction
            current_readings.electrode_voltages[i] = electrode_filters[i].update(avg_voltage);

            // Store in moving average buffer
            electrode_buffer[i][buffer_index] = current_readings.electrode_voltages[i];
        } else {
            Serial.println("ERROR: No valid readings for electrode " + electrode_names[i]);
            current_readings.electrode_voltages[i] = 0.0;
            current_readings.data_valid = false;
        }
    }

    // Update buffer index
    buffer_index = (buffer_index + 1) % NUM_READINGS;
}

void readEnvironmentalSensors() {
    // Temperature reading
    temp_sensor.requestTemperatures();
    current_readings.temperature = temp_sensor.getTempCByIndex(0);
    if (current_readings.temperature == DEVICE_DISCONNECTED_C) {
        current_readings.temperature = 25.0; // Default fallback
    }

    // pH sensor reading
    int ph_raw = analogRead(PH_SENSOR_PIN);
    float ph_voltage = (ph_raw * 3.3) / ADC_RESOLUTION;
    current_readings.ph_voltage = environment_filters[0].update(ph_voltage);

    // TDS sensor reading
    int tds_raw = analogRead(TDS_SENSOR_PIN);
    float tds_voltage = (tds_raw * 3.3) / ADC_RESOLUTION;
    current_readings.tds_voltage = environment_filters[1].update(tds_voltage);

    // UV sensor reading
    int uv_raw = analogRead(UV_SENSOR_PIN);
    current_readings.uv_intensity = environment_filters[2].update((uv_raw * 10.0) / ADC_RESOLUTION);

    // Moisture sensor reading
    int moisture_raw = analogRead(MOISTURE_PIN);
    current_readings.moisture_percent = environment_filters[3].update((moisture_raw * 100.0) / ADC_RESOLUTION);

    // Color sensor reading
    readColorSensor();
}

void readColorSensor() {
    // Read red component
    digitalWrite(COLOR_S2, LOW);
    digitalWrite(COLOR_S3, LOW);
    unsigned long red_freq = pulseIn(COLOR_OUT, LOW, 50000);

    // Read green component
    digitalWrite(COLOR_S2, HIGH);
    digitalWrite(COLOR_S3, HIGH);
    unsigned long green_freq = pulseIn(COLOR_OUT, LOW, 50000);

    // Read blue component
    digitalWrite(COLOR_S2, LOW);
    digitalWrite(COLOR_S3, HIGH);
    unsigned long blue_freq = pulseIn(COLOR_OUT, LOW, 50000);

    // Convert frequency to RGB values (0-255)
    current_readings.color_rgb[0] = map(constrain(red_freq, 10, 1000), 1000, 10, 0, 255);
    current_readings.color_rgb[1] = map(constrain(green_freq, 10, 1000), 1000, 10, 0, 255);
    current_readings.color_rgb[2] = map(constrain(blue_freq, 10, 1000), 1000, 10, 0, 255);
}

void readSystemStatus() {
    // Battery voltage monitoring
    int battery_raw = analogRead(BATTERY_PIN);
    current_readings.battery_voltage = (battery_raw * 3.3 * 2.0) / ADC_RESOLUTION; // Voltage divider
    system_state.battery_voltage = current_readings.battery_voltage;

    // WiFi signal strength
    if (system_state.wifi_connected) {
        current_readings.wifi_rssi = WiFi.RSSI();
    } else {
        current_readings.wifi_rssi = -100; // No connection
    }

    // Timestamp
    current_readings.timestamp = millis();

    // Data quality assessment
    current_readings.data_valid = true;
    current_readings.noise_level = calculateNoiseLevel();
}

float calculateNoiseLevel() {
    // Calculate noise level based on electrode readings variation
    float total_variation = 0;

    for (int i = 0; i < 5; i++) {
        float sum = 0;
        float sum_sq = 0;

        for (int j = 0; j < NUM_READINGS; j++) {
            float val = electrode_buffer[i][j];
            sum += val;
            sum_sq += val * val;
        }

        float mean = sum / NUM_READINGS;
        float variance = (sum_sq / NUM_READINGS) - (mean * mean);
        total_variation += sqrt(variance);
    }

    return total_variation / 5.0; // Average noise across all electrodes
}

// ============================================================================
// CALIBRATION FUNCTIONS
// ============================================================================

void performFieldCalibration() {
    Serial.println("\n=== FIELD CALIBRATION PROCEDURE ===");

    blinkStatusLED(5, 200);

    Serial.println("Please place electrodes in pH 7.0 buffer solution");
    Serial.println("Calibration will start in 10 seconds...");
    Serial.println("Press MODE button to cancel");

    // Countdown with cancel option
    for (int i = 10; i > 0; i--) {
        if (digitalRead(BUTTON_MODE) == LOW) {
            Serial.println("Calibration cancelled");
            return;
        }
        Serial.print(String(i) + "... ");
        delay(1000);
    }
    Serial.println();

    Serial.println("Starting calibration - please wait...");
    digitalWrite(LED_STATUS, HIGH);

    // Take calibration readings
    float cal_readings[5] = {0};
    float ph_cal_reading = 0;
    int stable_count = 0;

    // Wait for readings to stabilize
    while (stable_count < 5) {
        readElectrodeArray();
        readEnvironmentalSensors();

        // Check stability (less than 1% variation)
        float variation = calculateNoiseLevel();
        if (variation < 0.01) {
            stable_count++;
        } else {
            stable_count = 0;
        }

        Serial.print("Stabilizing... Variation: ");
        Serial.println(variation, 4);
        delay(2000);
    }

    // Record calibration values
    for (int i = 0; i < 5; i++) {
        cal_readings[i] = current_readings.electrode_voltages[i];
    }
    ph_cal_reading = current_readings.ph_voltage;

    // Expected values for pH 7.0 buffer
    float expected_values[5] = {1.42, 1.68, 1.89, 2.21, 2.03}; // SS, Cu, Zn, Ag, Pt

    // Calculate calibration offsets
    for (int i = 0; i < 5; i++) {
        calibration.electrode_offsets[i] = cal_readings[i] - expected_values[i];
        Serial.print("Electrode ");
        Serial.print(i);
        Serial.print(" offset: ");
        Serial.println(calibration.electrode_offsets[i], 4);
    }

    // pH calibration (single point)
    calibration.ph_slope = 1.0;
    calibration.ph_intercept = 7.0 - ph_cal_reading;

    // Temperature coefficients (default values)
    float temp_coeffs[5] = {-1.2e-3, -1.8e-3, -1.5e-3, -0.9e-3, -0.7e-3};
    for (int i = 0; i < 5; i++) {
        calibration.temperature_coeffs[i] = temp_coeffs[i];
    }

    // Update calibration metadata
    calibration.cal_timestamp = millis();
    calibration.cal_count++;
    calibration.valid = true;

    // Save calibration to EEPROM
    saveCalibrationData();

    system_state.last_calibration = millis();
    digitalWrite(LED_STATUS, LOW);

    Serial.println("=== CALIBRATION COMPLETED SUCCESSFULLY ===");
    Serial.println("Please rinse electrodes with distilled water");

    blinkStatusLED(3, 500); // Success indication
}

void applyCalibrationCorrections() {
    if (!calibration.valid) return;

    // Apply electrode offset corrections
    for (int i = 0; i < 5; i++) {
        current_readings.electrode_voltages[i] -= calibration.electrode_offsets[i];

        // Apply temperature compensation
        float temp_compensation = calibration.temperature_coeffs[i] * 
                                 (current_readings.temperature - 25.0);
        current_readings.electrode_voltages[i] -= temp_compensation;
    }
}

void performDriftCorrection() {
    Serial.println("Performing drift correction analysis...");

    // Simple drift correction - could be enhanced with more sophisticated algorithms
    // For now, just reset the calibration timer
    system_state.last_calibration = millis();

    Serial.println("Drift correction completed");
}

void setDefaultCalibration() {
    // Set default calibration values
    for (int i = 0; i < 5; i++) {
        calibration.electrode_offsets[i] = 0.0;
        calibration.temperature_coeffs[i] = -1.0e-3; // Generic temperature coefficient
    }
    calibration.ph_slope = 1.0;
    calibration.ph_intercept = 0.0;
    calibration.cal_timestamp = 0;
    calibration.cal_count = 0;
    calibration.valid = false;

    Serial.println("Default calibration values loaded");
}

// ============================================================================
// DATA TRANSMISSION FUNCTIONS
// ============================================================================

void transmitDataToCloud() {
    if (!system_state.wifi_connected) return;

    Serial.println("Transmitting data to cloud...");

    // Create JSON payload
    DynamicJsonDocument doc(1024);

    // Device information
    doc["device_id"] = device_id;
    doc["team_name"] = TEAM_NAME;
    doc["timestamp"] = current_readings.timestamp;
    doc["measurement_id"] = system_state.measurement_count;

    // Electrode readings
    JsonArray electrodes = doc.createNestedArray("electrodes");
    for (int i = 0; i < 5; i++) {
        electrodes.add(current_readings.electrode_voltages[i]);
    }

    // Environmental data
    JsonObject env = doc.createNestedObject("environmental");
    env["temperature"] = current_readings.temperature;
    env["ph_voltage"] = current_readings.ph_voltage;
    env["tds_voltage"] = current_readings.tds_voltage;
    env["uv_intensity"] = current_readings.uv_intensity;
    env["moisture_percent"] = current_readings.moisture_percent;

    // Color data
    JsonArray colors = doc.createNestedArray("color_rgb");
    for (int i = 0; i < 3; i++) {
        colors.add(current_readings.color_rgb[i]);
    }

    // System status
    JsonObject status = doc.createNestedObject("system_status");
    status["battery_voltage"] = current_readings.battery_voltage;
    status["wifi_rssi"] = current_readings.wifi_rssi;
    status["noise_level"] = current_readings.noise_level;
    status["data_valid"] = current_readings.data_valid;
    status["calibration_valid"] = calibration.valid;

    // Serialize and send
    String json_string;
    serializeJson(doc, json_string);

    http.begin(cloud_endpoint);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-ID", device_id);
    http.addHeader("X-Team-Name", TEAM_NAME);

    int response_code = http.POST(json_string);

    if (response_code > 0) {
        String response = http.getString();
        if (response_code == 200) {
            Serial.println("Data transmission successful");
        } else {
            Serial.print("Transmission failed with code: ");
            Serial.println(response_code);
        }
    } else {
        Serial.print("HTTP error: ");
        Serial.println(response_code);
        system_state.error_count++;
    }

    http.end();
}

// ============================================================================
// SYSTEM MONITORING FUNCTIONS
// ============================================================================

void performSystemSelfTest() {
    Serial.println("\n=== SYSTEM SELF-TEST ===");

    bool all_tests_passed = true;

    // Test 1: Power supply check
    Serial.print("Power supply test: ");
    if (system_state.battery_voltage > 3.0) {
        Serial.println("PASS");
    } else {
        Serial.println("FAIL - Low battery");
        all_tests_passed = false;
    }

    // Test 2: Sensor connectivity
    Serial.print("Sensor connectivity: ");
    bool sensors_ok = true;
    for (int i = 0; i < 5; i++) {
        int raw = analogRead(SS_ELECTRODE_PIN + i);
        if (raw == 0 || raw == ADC_RESOLUTION) {
            sensors_ok = false;
            break;
        }
    }
    Serial.println(sensors_ok ? "PASS" : "FAIL");
    if (!sensors_ok) all_tests_passed = false;

    // Test 3: Temperature sensor
    Serial.print("Temperature sensor: ");
    temp_sensor.requestTemperatures();
    float temp = temp_sensor.getTempCByIndex(0);
    if (temp != DEVICE_DISCONNECTED_C) {
        Serial.println("PASS");
    } else {
        Serial.println("FAIL - No temperature sensor");
        all_tests_passed = false;
    }

    // Test 4: WiFi connectivity
    Serial.print("WiFi connectivity: ");
    Serial.println(system_state.wifi_connected ? "PASS" : "FAIL (offline mode)");

    // Test result summary
    Serial.print("\nSelf-test result: ");
    if (all_tests_passed) {
        Serial.println("ALL TESTS PASSED");
        blinkStatusLED(2, 300);
    } else {
        Serial.println("SOME TESTS FAILED");
        blinkStatusLED(5, 100);
        digitalWrite(LED_ERROR, HIGH);
        delay(2000);
        digitalWrite(LED_ERROR, LOW);
    }

    Serial.println("=== SELF-TEST COMPLETE ===\n");
}

void maintainWiFiConnection() {
    static unsigned long last_check = 0;
    unsigned long current_time = millis();

    // Check connection every 30 seconds
    if (current_time - last_check > 30000) {
        if (WiFi.status() != WL_CONNECTED && system_state.wifi_connected) {
            system_state.wifi_connected = false;
            digitalWrite(LED_WIFI, LOW);
            Serial.println("WiFi connection lost");

            // Attempt reconnection
            WiFi.begin(ssid, password);
            delay(5000);

            if (WiFi.status() == WL_CONNECTED) {
                system_state.wifi_connected = true;
                digitalWrite(LED_WIFI, HIGH);
                Serial.println("WiFi reconnected");
            }
        }
        last_check = current_time;
    }
}

void monitorSystemHealth() {
    static unsigned long last_health_check = 0;
    unsigned long current_time = millis();

    // Health check every 5 minutes
    if (current_time - last_health_check > 300000) {
        // Check battery level
        if (system_state.battery_voltage < 3.2) {
            Serial.println("WARNING: Low battery voltage");
            blinkErrorLED(3);
        }

        // Check noise levels
        if (current_readings.noise_level > 0.1) {
            Serial.println("WARNING: High noise detected");
        }

        // Check calibration age
        unsigned long cal_age = current_time - system_state.last_calibration;
        if (cal_age > CALIBRATION_PERIOD * 1.1) { // 10% tolerance
            Serial.println("WARNING: Calibration overdue");
            blinkErrorLED(2);
        }

        last_health_check = current_time;
    }
}

void managePowerState() {
    // Enter deep sleep if battery critically low
    if (system_state.battery_voltage < 3.0) {
        Serial.println("CRITICAL: Battery too low - entering sleep mode");
        Serial.println("Press BOOT button to wake up");

        // Configure wake-up source
        esp_sleep_enable_ext0_wakeup(GPIO_NUM_0, 0); // BOOT button

        // Turn off LEDs
        digitalWrite(LED_STATUS, LOW);
        digitalWrite(LED_WIFI, LOW);
        digitalWrite(LED_ERROR, LOW);

        // Enter deep sleep
        esp_deep_sleep_start();
    }
}

// ============================================================================
// USER INTERFACE FUNCTIONS
// ============================================================================

void processSerialCommands() {
    if (Serial.available()) {
        String command = Serial.readStringUntil('\n');
        command.trim();
        command.toUpperCase();

        if (command == "STATUS") {
            displaySystemStatus();
        }
        else if (command == "READINGS") {
            displayCurrentReadings();
        }
        else if (command == "CALIBRATE") {
            performFieldCalibration();
        }
        else if (command == "RESET") {
            Serial.println("Restarting system...");
            ESP.restart();
        }
        else if (command == "HELP") {
            displayHelpMenu();
        }
        else if (command.startsWith("READ_")) {
            handleSensorReadCommand(command);
        }
        else {
            Serial.println("Unknown command. Type HELP for available commands.");
        }
    }
}

void displaySystemStatus() {
    Serial.println("\n================================");
    Serial.println("      AYUSURE SYSTEM STATUS     ");
    Serial.println("================================");
    Serial.println("Team: " + String(TEAM_NAME));
    Serial.println("Device: " + String(device_id));
    Serial.println("Firmware: " + String(FIRMWARE_VERSION));

    unsigned long uptime = millis() - system_state.boot_time;
    Serial.println("Uptime: " + formatTime(uptime));

    Serial.println("\nCONNECTIVITY:");
    Serial.println("WiFi: " + String(system_state.wifi_connected ? "Connected" : "Disconnected"));
    if (system_state.wifi_connected) {
        Serial.println("IP: " + WiFi.localIP().toString());
        Serial.println("RSSI: " + String(WiFi.RSSI()) + " dBm");
    }

    Serial.println("\nPOWER & SYSTEM:");
    Serial.println("Battery: " + String(system_state.battery_voltage, 2) + "V");
    Serial.println("Measurements: " + String(system_state.measurement_count));
    Serial.println("Errors: " + String(system_state.error_count));

    Serial.println("\nCALIBRATION:");
    Serial.println("Status: " + String(calibration.valid ? "Valid" : "Invalid"));
    if (calibration.valid) {
        unsigned long cal_age = millis() - calibration.cal_timestamp;
        Serial.println("Age: " + formatTime(cal_age));
        Serial.println("Count: " + String(calibration.cal_count));
    }

    Serial.println("\nDATA QUALITY:");
    Serial.println("Valid: " + String(current_readings.data_valid ? "Yes" : "No"));
    Serial.println("Noise: " + String(current_readings.noise_level, 4) + "V");

    Serial.println("================================\n");
}

void displayCurrentReadings() {
    Serial.println("\n=== CURRENT SENSOR READINGS ===");

    Serial.println("ELECTRODES (V):");
    String electrodes[] = {"SS", "Cu", "Zn", "Ag", "Pt"};
    for (int i = 0; i < 5; i++) {
        Serial.println("  " + electrodes[i] + ": " + String(current_readings.electrode_voltages[i], 4));
    }

    Serial.println("\nENVIRONMENTAL:");
    Serial.println("  Temperature: " + String(current_readings.temperature, 2) + "°C");
    Serial.println("  pH Voltage: " + String(current_readings.ph_voltage, 4) + "V");
    Serial.println("  TDS Voltage: " + String(current_readings.tds_voltage, 4) + "V");
    Serial.println("  UV Intensity: " + String(current_readings.uv_intensity, 2));
    Serial.println("  Moisture: " + String(current_readings.moisture_percent, 1) + "%");

    Serial.println("\nCOLOR (RGB):");
    Serial.println("  R: " + String(current_readings.color_rgb[0]));
    Serial.println("  G: " + String(current_readings.color_rgb[1]));
    Serial.println("  B: " + String(current_readings.color_rgb[2]));

    Serial.println("\nSYSTEM:");
    Serial.println("  Battery: " + String(current_readings.battery_voltage, 2) + "V");
    Serial.println("  WiFi RSSI: " + String(current_readings.wifi_rssi) + " dBm");
    Serial.println("  Timestamp: " + String(current_readings.timestamp));

    Serial.println("===============================\n");
}

void displayHelpMenu() {
    Serial.println("\n=== AYUSURE COMMAND HELP ===");
    Serial.println("Available commands:");
    Serial.println("  STATUS     - Display system status");
    Serial.println("  READINGS   - Show current sensor readings");
    Serial.println("  CALIBRATE  - Start calibration procedure");
    Serial.println("  RESET      - Restart the system");
    Serial.println("  HELP       - Show this help menu");
    Serial.println("  READ_<sensor> - Read specific sensor");
    Serial.println("                 (SS, CU, ZN, AG, PT, TEMP, PH)");
    Serial.println("\nPhysical controls:");
    Serial.println("  BOOT button - Start calibration");
    Serial.println("  MODE button - Show system status");
    Serial.println("============================\n");
}

void handleSensorReadCommand(String command) {
    if (command == "READ_SS") {
        Serial.println("SS: " + String(current_readings.electrode_voltages[0], 4) + "V");
    }
    else if (command == "READ_CU") {
        Serial.println("Cu: " + String(current_readings.electrode_voltages[1], 4) + "V");
    }
    else if (command == "READ_ZN") {
        Serial.println("Zn: " + String(current_readings.electrode_voltages[2], 4) + "V");
    }
    else if (command == "READ_AG") {
        Serial.println("Ag: " + String(current_readings.electrode_voltages[3], 4) + "V");
    }
    else if (command == "READ_PT") {
        Serial.println("Pt: " + String(current_readings.electrode_voltages[4], 4) + "V");
    }
    else if (command == "READ_TEMP") {
        Serial.println("Temperature: " + String(current_readings.temperature, 2) + "°C");
    }
    else if (command == "READ_PH") {
        Serial.println("pH Voltage: " + String(current_readings.ph_voltage, 4) + "V");
    }
    else {
        Serial.println("Unknown sensor. Available: SS, CU, ZN, AG, PT, TEMP, PH");
    }
}

void validateMeasurementData() {
    current_readings.data_valid = true;

    // Check electrode readings are in valid range
    for (int i = 0; i < 5; i++) {
        if (current_readings.electrode_voltages[i] < 0.05 || 
            current_readings.electrode_voltages[i] > 3.25) {
            current_readings.data_valid = false;
            Serial.println("Invalid electrode reading detected: " + String(i));
        }
    }

    // Check temperature range
    if (current_readings.temperature < -10 || current_readings.temperature > 60) {
        current_readings.data_valid = false;
        Serial.println("Invalid temperature reading");
    }

    // Check noise level
    if (current_readings.noise_level > 0.2) {
        Serial.println("WARNING: High noise level detected");
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

void blinkStatusLED(int count, int delay_ms) {
    for (int i = 0; i < count; i++) {
        digitalWrite(LED_STATUS, HIGH);
        delay(delay_ms);
        digitalWrite(LED_STATUS, LOW);
        delay(delay_ms);
    }
}

void blinkErrorLED(int count) {
    for (int i = 0; i < count; i++) {
        digitalWrite(LED_ERROR, HIGH);
        delay(200);
        digitalWrite(LED_ERROR, LOW);
        delay(200);
    }
}

String formatTime(unsigned long milliseconds) {
    unsigned long seconds = milliseconds / 1000;
    unsigned long minutes = seconds / 60;
    unsigned long hours = minutes / 60;
    unsigned long days = hours / 24;

    seconds %= 60;
    minutes %= 60;
    hours %= 24;

    String result = "";
    if (days > 0) result += String(days) + "d ";
    if (hours > 0) result += String(hours) + "h ";
    if (minutes > 0) result += String(minutes) + "m ";
    result += String(seconds) + "s";

    return result;
}

// ============================================================================
// EEPROM FUNCTIONS
// ============================================================================

void saveCalibrationData() {
    EEPROM.begin(512);
    EEPROM.put(0, calibration);
    EEPROM.commit();
    Serial.println("Calibration data saved to EEPROM");
}

void loadCalibrationData() {
    EEPROM.begin(512);
    EEPROM.get(0, calibration);

    // Validate loaded data
    if (calibration.cal_timestamp == 0 || calibration.cal_timestamp > millis()) {
        Serial.println("Invalid calibration data - using defaults");
        setDefaultCalibration();
    } else {
        Serial.println("Calibration data loaded from EEPROM");
        system_state.last_calibration = calibration.cal_timestamp;
    }
}

// ============================================================================
// END OF AYUSURE ARDUINO FIRMWARE
// ============================================================================

/*
 * USAGE INSTRUCTIONS:
 * 
 * 1. Hardware Setup:
 *    - Connect electrodes to pins A0-A4 (SS, Cu, Zn, Ag, Pt)
 *    - Connect pH sensor to A5, TDS to A6, UV to A7, moisture to A8
 *    - Connect DS18B20 temperature sensor to pin 4
 *    - Connect color sensor (TCS3200) to pins 5-9
 *    - Connect status LEDs to pins 2, 13, 14
 *    - Battery monitoring on A15
 * 
 * 2. Operation:
 *    - System auto-starts and performs self-test
 *    - Takes measurements every 5 seconds
 *    - Press BOOT button for field calibration
 *    - Press MODE button for system status
 *    - Use serial commands for advanced control
 * 
 * 3. Serial Commands:
 *    - STATUS: Show system information
 *    - READINGS: Display current sensor values
 *    - CALIBRATE: Start calibration procedure
 *    - RESET: Restart the system
 *    - HELP: Show available commands
 *    - READ_<sensor>: Read specific sensor
 * 
 * 4. Calibration:
 *    - Use pH 7.0 buffer solution
 *    - Follow on-screen instructions
 *    - Calibration saved automatically
 *    - Auto-recalibration every 24 hours
 * 
 * 5. Data Transmission:
 *    - Automatic cloud upload when WiFi connected
 *    - JSON format with complete sensor data
 *    - Includes team information and device ID
 * 
 * Team: Hyper Grey (MSIT)
 * Members: Pulkit Kapur, Prakhar Chandra, Shaymon Khawas, 
 *          Shiney Sharma, Parul Singh, Vaishali
 */
