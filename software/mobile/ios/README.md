
---

# AyuSure - iOS App

A React Native iOS implementation of the AyuSure E-Tongue Dashboard, designed with iOS-specific optimizations for seamless performance and native experience.

**Team:** Hyper Grey - MSIT

---

## Features

* **Device Connection Screen** – Scan and connect to ESP32 devices
* **Bluetooth LE & WiFi Support** – Multiple connection types
* **Real-time Sensor Dashboard** – pH, TDS, Temperature, UV, Moisture, Color
* **Electrode Readings** – Pt, Ag, AgCl, SS, Cu, C, Zn electrodes
* **Dynamic Theming** – Automatic light/dark mode switching
* **iOS Native Features** – Haptic feedback, native alerts, Safe Area insets
* **Mock Data Generation** – Built-in test data for immediate use

---

## Quick Start

### Prerequisites

* Node.js (v16 or higher)
* Expo CLI (`npm install -g expo-cli`)
* iOS Simulator (Xcode) or physical iOS device
* Expo Go app (for testing on device)

### Installation & Setup

1. Install dependencies:

   ```bash
   npm install
   ```

2. Start the development server:

   ```bash
   npm start
   ```

3. Run on iOS:

   ```bash
   npm run ios
   ```

   Or scan the QR code with the Expo Go app on your iOS device.

---

## Testing the App

### Connection Screen

* Launch the app to view the "Device Connection" screen
* Test theme switching via Settings → Display & Brightness
* Use filters to switch between "All", "Bluetooth LE", and "WiFi"
* Tap "Scan" to discover mock ESP32 devices
* Select "Connect" to establish a device connection

### Dashboard Screen

* After connection, the app navigates to the E-Tongue Dashboard
* Sensor values update automatically every 2 seconds
* Verify sensor cards: pH (blue), Moisture (green), Temperature (green)
* Check electrode readings across all 7 electrodes
* Use "Stop" to disconnect and return to the connection screen

### iOS-Specific Features

* Haptic feedback on button presses
* Adaptive theming based on system settings
* Safe Area handling for devices with notches
* Permission handling for Bluetooth and location

### Mock Data Verification

* **Sensor ranges:**

  * pH: 0–14
  * TDS: 0–1000 ppm
  * Temperature: 20–35 °C
  * UV Absorbance: 0–2 AU
  * Moisture: 0–100%
  * Color: Random hex values
* **Electrode ranges:** –500 to +500 mV

---

## App Structure

```
App.js
├── State Management (useState hooks)
├── Theme Detection & Colors
├── Mock Data Generation
├── Permission Handling
├── Device Scanning & Connection
├── Connection Screen
├── Dashboard Screen
└── Styling & Theming
```

---

## Key Components

### Connection Screen

* Header with connection status
* Connection type filters
* Device scanning with loading indicators
* Device list with signal strength and connect options
* Empty state messaging

### Dashboard Screen

* Header with connection status and streaming controls
* Three-column sensor readings grid with distinct colors
* Four-column electrode readings grid
* Device information with ID and timestamp
* Stop button for disconnection

---

## Styling & Theming

### Theme Colors

```javascript
// Dark Theme
background: '#1a1a1a'
cardBackground: '#2d2d2d'
textPrimary: '#ffffff'

// Light Theme
background: '#f5f5f5'
cardBackground: '#ffffff'
textPrimary: '#000000'
```

### Sensor Border Colors

* pH: Blue (#2196F3)
* Moisture: Green (#4CAF50)
* Temperature: Green (#4CAF50)
* UV/TDS: Gray (#9E9E9E)
* Color: Black (#000000)

---

### Performance Tips

* Use iOS Simulator for rapid iteration
* Test on physical device to validate haptic feedback
* Clear Metro cache if experiencing runtime issues
* Restart Expo Go app if connection fails

---
