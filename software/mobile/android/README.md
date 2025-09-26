# AyuSure - Android App

A native Android implementation of the AyuSure E-Tongue Dashboard, built with Jetpack Compose for modern UI and seamless performance on Android devices.

**Team:** Hyper Grey - MSIT

---

## Features

* **Device Connection Screen** – Scan and connect to ESP32 devices
* **Bluetooth LE & WiFi Support** – Multiple connection types with Android permissions
* **Real-time Sensor Dashboard** – pH, TDS, Temperature, UV, Moisture, Color readings
* **Electrode Readings** – Pt, Ag, AgCl, SS, Cu, C, Zn electrodes with live data
* **Material Design 3** – Modern Android UI with dynamic theming
* **Android Native Features** – Haptic feedback, system notifications, edge-to-edge display
* **Mock Data Generation** – Built-in test data for immediate development and testing
* **Offline Support** – Local data persistence and caching

---

## Quick Start

### Prerequisites

* Android Studio (Arctic Fox or newer)
* Android SDK (API 24+)
* Kotlin 2.0.21 or higher
* Gradle 8.12.3 or higher
* Android device or emulator (API 24+)

### Installation & Setup

1. Clone and open the project in Android Studio:
```bash
git clone <repository-url>
cd ayusure-android
```

2. Sync Gradle dependencies:
```bash
./gradlew build
```

3. Run on Android device/emulator:
```bash
./gradlew installDebug
```

Or use Android Studio's "Run" button (Shift+F10).

---

## Testing the App

### Connection Screen
* Launch the app to view the "Device Connection" screen
* Test Material You theming (adapts to system wallpaper colors)
* Use filter chips to switch between "All", "Bluetooth LE", and "WiFi"
* Tap "Scan" to discover mock ESP32 devices with loading animation
* Grant Bluetooth and location permissions when prompted
* Select "Connect" to establish device connection with haptic feedback

### Dashboard Screen
* After connection, navigate to the E-Tongue Dashboard
* Sensor values auto-update every 2 seconds with smooth animations
* Verify sensor cards with Material Design 3 styling:
  * pH (Blue accent)
  * Moisture (Green accent) 
  * Temperature (Orange accent)
  * TDS/UV (Purple accent)
* Check electrode readings grid with real-time updates
* Use "Stop" button to disconnect and return to connection screen

### Android-Specific Features
* **Permissions:** Bluetooth, Location (for BLE scanning)
* **Haptic Feedback:** Button presses and connection events
* **Material You:** Dynamic color theming based on wallpaper
* **Edge-to-Edge:** Full screen with proper insets handling
* **System Integration:** Status bar, navigation bar theming

### Mock Data Verification
* **Sensor ranges:**
  * pH: 0–14 (with 0.1 precision)
  * TDS: 0–1000 ppm
  * Temperature: 20–35 °C
  * UV Absorbance: 0–2 AU
  * Moisture: 0–100%
  * Color: Random hex values with live preview
* **Electrode ranges:** –500 to +500 mV (7 electrodes)

---

## Project Structure

```
app/
├── src/main/java/com/example/etongue/
│   ├── MainActivity.kt
│   ├── ui/
│   │   ├── theme/           # Material Design 3 theming
│   │   ├── screens/         # Connection & Dashboard screens
│   │   ├── components/      # Reusable UI components
│   │   └── navigation/      # Navigation setup
│   ├── data/
│   │   ├── models/          # Data classes
│   │   ├── repository/      # Data layer
│   │   └── mock/           # Mock data generators
│   ├── domain/
│   │   └── usecases/       # Business logic
│   └── utils/              # Utilities and extensions
├── src/main/res/
│   ├── values/             # Colors, strings, themes
│   ├── drawable/           # Vector drawables
│   └── mipmap-*/          # App icons
└── build.gradle.kts        # Dependencies and build config
```

---

## Key Components

### Connection Screen
* **Material Design 3** header with connection status
* **Filter Chips** for connection type selection
* **Floating Action Button** for device scanning
* **LazyColumn** with device list and signal strength indicators
* **Permission handling** with rationale dialogs
* **Empty state** with helpful messaging

### Dashboard Screen
* **Top App Bar** with connection status and controls
* **Sensor Grid** with Material Design 3 cards and animations
* **Electrode Grid** with real-time value updates
* **Device Info Card** with connection details and timestamp
* **Stop FAB** for disconnection with confirmation dialog

---

## Styling & Theming

### Material Design 3 Colors
```kotlin
// Dynamic theming based on system
ColorScheme.fromSeed(Color(0xFF6750A4))

// Light Theme
surface = Color(0xFFFFFBFE)
onSurface = Color(0xFF1C1B1F)
primary = Color(0xFF6750A4)

// Dark Theme  
surface = Color(0xFF1C1B1F)
onSurface = Color(0xFFE6E1E5)
primary = Color(0xFFD0BCFF)
```

### Sensor Card Styling
* **pH:** Primary color accent with blue tint
* **Moisture:** Success color (Green)
* **Temperature:** Warning color (Orange) 
* **UV/TDS:** Secondary color (Purple)
* **Rounded corners:** 16dp with Material elevation
* **Typography:** Material Design 3 type scale

---

## Build Configuration

### Gradle Setup
* **Compile SDK:** 36 (Android 15)
* **Min SDK:** 24 (Android 7.0)
* **Target SDK:** 36
* **Kotlin:** 2.0.21 with Compose Compiler
* **Compose BOM:** 2024.09.00

### Dependencies
* **Jetpack Compose:** Modern declarative UI
* **Navigation Compose:** Type-safe navigation
* **Material 3:** Latest Material Design components
* **Coroutines:** Asynchronous programming
* **Gson:** JSON serialization
* **MockK:** Unit testing framework

---

## Development Tips

### Performance
* Use Android Studio's Layout Inspector for UI debugging
* Enable GPU rendering profiler for animation performance
* Test on various screen sizes and densities
* Use Compose Preview for rapid UI iteration

### Testing
* Run unit tests: `./gradlew test`
* Run instrumented tests: `./gradlew connectedAndroidTest`
* Use Compose Test Rule for UI testing
* Mock Bluetooth connections for consistent testing

### Debugging
* Enable developer options on device for USB debugging
* Use Logcat for runtime debugging
* Clear app data if experiencing state issues
* Check permissions in device settings if BLE fails

---

## Permissions Required

```xml
<!-- Bluetooth permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Location for BLE scanning -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Network for WiFi connections -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

