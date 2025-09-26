# AyuSure E-Tongue Hardware Assembly Guide
## Complete Manufacturing and Assembly Documentation

---

## Table of Contents
1. [Pre-Assembly Requirements](#pre-assembly-requirements)
2. [Component Verification](#component-verification) 
3. [PCB Assembly Process](#pcb-assembly-process)
4. [Electrode System Integration](#electrode-system-integration)
5. [Quality Control Testing](#quality-control-testing)
6. [Enclosure Assembly](#enclosure-assembly)
7. [Final Testing and Calibration](#final-testing-and-calibration)
8. [Troubleshooting Guide](#troubleshooting-guide)

---

## Pre-Assembly Requirements

### Required Tools and Equipment
- **Soldering Equipment**
  - Temperature-controlled soldering iron (15-25W)
  - Lead-free solder (SAC305 or equivalent)
  - Flux paste (no-clean type)
  - Solder wick for rework
  - Hot air rework station (optional)

- **Assembly Tools**
  - ESD-safe workstation with wrist strap
  - Microscope or magnifying glass (minimum 10x)
  - Fine-tip tweezers (anti-magnetic, ESD-safe)
  - Small Phillips screwdrivers (PH0, PH1)
  - Precision knife for trace cutting

- **Test Equipment**
  - Digital multimeter with high impedance input
  - Oscilloscope (minimum 100MHz bandwidth)
  - Function generator (optional)
  - pH meter for calibration
  - Standard buffer solutions (pH 4.0, 7.0, 10.0)

### Environmental Requirements
- **Temperature**: 20-25°C ± 2°C
- **Humidity**: 30-60% RH
- **Lighting**: Minimum 1000 lux at work surface
- **Air Quality**: Clean, dust-free environment
- **ESD Protection**: All surfaces and personnel grounded

---

## Component Verification

### Primary Components Checklist
- [ ] ESP32-WROOM-32 module (1x)
- [ ] ADS1115 16-bit ADC (1x)
- [ ] DS18B20 temperature sensor (1x)
- [ ] TCS3200 color sensor (1x)
- [ ] LM3940 3.3V regulator (1x)
- [ ] 32MHz crystal oscillator (1x)
- [ ] Electrode set: SS, Cu, Zn, Ag, Pt (5x)
- [ ] BNC connectors (5x)

### Passive Components
- [ ] 0603 Resistors: 4.7kΩ (2x), 10kΩ (4x), 100Ω (2x)
- [ ] 0603 Capacitors: 100nF (6x), 10µF (2x), 22pF (2x)
- [ ] LED indicators: Red, Green, Blue (3x)
- [ ] Push button switches (2x)

### Component Inspection Protocol
1. **Visual Inspection**
   - Check for physical damage, bent pins, or contamination
   - Verify part numbers match BOM specifications
   - Inspect for moisture sensitivity indicator damage

2. **Electrical Verification**
   - Measure resistor values within ±5% tolerance
   - Test capacitors for shorts or opens
   - Verify IC supply voltage requirements
   - Check crystal frequency accuracy

---

## PCB Assembly Process

### Step 1: Solder Paste Application
1. **Stencil Alignment**
   - Position laser-cut stencil over PCB pads
   - Ensure precise alignment using fiducial markers
   - Secure stencil to prevent movement

2. **Paste Application**
   - Use SAC305 lead-free solder paste
   - Apply with squeegee at 45° angle
   - Maintain consistent pressure and speed
   - Remove stencil carefully to avoid smearing

### Step 2: Component Placement
1. **Placement Sequence**
   - Start with smallest components (0603 passives)
   - Progress to medium components (SOICs, QFNs)
   - Finish with largest components (connectors, modules)

2. **Critical Placement Guidelines**
   - ESP32 module: Verify pin 1 orientation
   - ADS1115: Check TSSOP pin alignment
   - Crystal: Maintain ground clearance
   - Decoupling capacitors: Place close to IC power pins

### Step 3: Reflow Soldering
1. **Oven Profile Settings**
   - Preheat: 150°C for 90 seconds
   - Thermal soak: 150-200°C for 90 seconds  
   - Reflow: 245°C peak for 30 seconds
   - Cool down: <6°C/second cooling rate

2. **Alternative Hand Soldering**
   - Iron temperature: 325°C
   - Contact time: 2-3 seconds maximum
   - Use flux to improve wetting
   - Clean with isopropyl alcohol

### Step 4: Post-Solder Inspection
1. **Visual Inspection**
   - Check for solder bridges between pins
   - Verify all joints are properly wetted
   - Look for tombstoning or component shifts
   - Inspect for cold solder joints

2. **Electrical Testing**
   - Continuity test on power rails
   - Check for shorts between VCC and GND
   - Verify I2C pull-up resistors installed
   - Test crystal oscillation

---

## Electrode System Integration

### Electrode Preparation
1. **Material Verification**
   - Stainless Steel: 316L grade, 99.9% purity
   - Copper: Oxygen-free, 99.99% purity
   - Zinc: 99.95% purity minimum
   - Silver: 99.9% purity, tarnish-resistant
   - Platinum: 99.95% purity

2. **Surface Preparation**
   - Polish electrodes with 1µm diamond paste
   - Clean with acetone followed by distilled water
   - Dry in nitrogen atmosphere
   - Store in clean, dry environment

### Electrode Housing Assembly
1. **Mechanical Assembly**
   - Insert electrodes into PEEK housing
   - Maintain 3mm center-to-center spacing
   - Apply medical-grade sealant around joints
   - Torque electrode connections to 0.5 N⋅m

2. **Electrical Connections**
   - Solder electrode leads to BNC center pins
   - Use silver-bearing solder for low resistance
   - Apply heat shrink tubing over connections
   - Test connection resistance <10mΩ

### Sensor Array Integration
1. **pH Sensor Integration**
   - Mount glass pH electrode in housing
   - Connect to high-impedance buffer amplifier  
   - Apply temperature compensation circuit
   - Calibrate with standard buffer solutions

2. **Supporting Sensors**
   - TDS sensor: Connect to conductivity measurement circuit
   - UV sensor: Mount with appropriate optical window
   - Color sensor: Ensure even illumination geometry
   - Temperature sensor: Place in thermal contact with sample

---

## Quality Control Testing

### Electrical Performance Tests
1. **Power Supply Testing**
   - Measure 3.3V rail: 3.30V ± 50mV
   - Check current consumption: <200mA typical
   - Verify brownout detection functionality
   - Test battery charging circuit

2. **ADC Performance**
   - Linearity test across full range
   - Noise measurement: <1mV RMS
   - Temperature drift: <50ppm/°C
   - Reference voltage stability

3. **Communication Testing**
   - I2C bus functionality at 100kHz
   - WiFi connection establishment
   - Bluetooth pairing capability
   - Data logging to SD card

### Sensor Calibration Tests
1. **Electrode Response**
   - Test with pH 7.0 buffer solution
   - Measure electrode potentials
   - Check for drift over 10-minute period
   - Verify temperature compensation

2. **Environmental Sensors**
   - pH calibration with 3-point curve
   - TDS calibration with known standards
   - Temperature accuracy verification
   - UV sensor spectral response

---

## Enclosure Assembly

### Mechanical Assembly
1. **Housing Preparation**
   - Check IP67 O-ring seals
   - Apply thin layer of silicone grease
   - Verify connector alignment
   - Test mechanical fit before final assembly

2. **PCB Installation**
   - Mount PCB on standoffs
   - Connect flex cables for display
   - Route antenna cable carefully
   - Apply thread locking compound

### Sealing and Protection
1. **Waterproof Sealing**
   - Install all cable glands properly
   - Apply appropriate torque (hand tight + 1/4 turn)
   - Test seal integrity with pressure
   - Mark assembly date and operator

2. **EMI Shielding**
   - Install RF shielding gaskets
   - Ensure proper grounding connections
   - Test for electromagnetic interference
   - Verify FCC/CE compliance

---

## Final Testing and Calibration

### Functional Testing Protocol
1. **Power-On Test**
   - Connect battery and charger
   - Verify LED indicators function
   - Check display operation
   - Test button responsiveness

2. **Sensor Functionality**
   - Place electrodes in test solution
   - Verify readings are stable
   - Test pH measurement accuracy
   - Check temperature compensation

3. **Communication Testing**
   - Connect to WiFi network
   - Upload test data to cloud
   - Verify real-time monitoring
   - Test mobile app connectivity

### Factory Calibration
1. **Multi-Point pH Calibration**
   - Use certified buffer solutions
   - Record calibration coefficients
   - Verify accuracy within ±0.05 pH
   - Store calibration data in EEPROM

2. **Electrode Standardization**
   - Test with reference herb samples
   - Record baseline responses
   - Calculate normalization factors
   - Validate against HPLC data

### Final Quality Inspection
1. **Performance Verification**
   - Complete system test with known samples
   - Verify accuracy meets specifications
   - Test temperature stability
   - Check long-term drift characteristics

2. **Documentation Package**
   - Generate calibration certificate
   - Record serial number and test data
   - Create user manual and quick start guide
   - Prepare shipping documentation

---

## Troubleshooting Guide

### Common Assembly Issues

**Problem: ESP32 not booting**
- Check power supply voltage (3.3V ± 0.1V)
- Verify crystal oscillator operation
- Check EN pin is pulled high
- Ensure GPIO0 is not grounded during boot

**Problem: I2C communication failure**
- Verify SDA/SCL connections
- Check pull-up resistors (4.7kΩ)
- Test for bus conflicts
- Measure signal levels with oscilloscope

**Problem: Electrode readings unstable**
- Check connection resistance <10mΩ
- Verify shielding effectiveness
- Clean electrode surfaces
- Test reference electrode connection

**Problem: WiFi connection issues**
- Check antenna connection and placement
- Verify network credentials
- Test signal strength at location
- Update firmware if necessary

### Diagnostic Procedures
1. **Systematic Fault Isolation**
   - Start with power supply verification
   - Check digital circuits before analog
   - Test communication buses independently
   - Isolate sensor problems from processing

2. **Test Equipment Usage**
   - Use high-impedance voltmeter for electrode measurements
   - Oscilloscope for timing and noise analysis
   - Spectrum analyzer for RF interference
   - Environmental chamber for temperature testing

### Repair Procedures
1. **Component Replacement**
   - Heat sensitive components to 250°C maximum
   - Use controlled heating profile
   - Apply fresh flux for rework
   - Test functionality after replacement

2. **Trace Repair**
   - Use fine wire for trace jumpers
   - Apply conformal coating over repairs
   - Test electrical integrity
   - Document all modifications

---

## Manufacturing Scale-Up Notes

### Production Considerations
- SMT line programming for pick-and-place
- In-circuit test (ICT) fixture design
- Automated optical inspection (AOI) setup
- Statistical process control implementation

### Quality System Integration
- ISO 9001 quality management system
- Traceability requirements for medical devices
- Supplier qualification procedures
- Continuous improvement processes

---

**Document Information**
- Assembly Guide Version: 2.0
- Last Updated: September 2025
- Reviewed By: Hardware Engineering Team
- Approved For: Production Manufacturing
