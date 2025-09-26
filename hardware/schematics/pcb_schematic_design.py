# AyuSure E-Tongue PCB Schematic Design
# Professional PCB design specifications for manufacturing

import math
import json

class AyuSurePCBDesign:
    def __init__(self):
        self.board_dimensions = {"width": 65, "height": 45, "thickness": 1.6}  # mm
        self.layer_count = 4
        self.component_count = 28

    def generate_component_placement(self):
        """Generate component placement coordinates"""
        components = {
            'ESP32-WROOM-32': {
                'position': (32.5, 22.5),
                'rotation': 0,
                'package': 'ESP32-MODULE',
                'description': 'Main microcontroller'
            },
            'ADS1115': {
                'position': (20, 35),
                'rotation': 90,
                'package': 'TSSOP-10',
                'description': '16-bit ADC for sensor readings'
            },
            'DS18B20': {
                'position': (10, 10),
                'rotation': 0,
                'package': 'TO-92',
                'description': 'Temperature sensor'
            },
            'TCS3200': {
                'position': (50, 10),
                'rotation': 0,
                'package': 'DIP-8',
                'description': 'Color sensor'
            },
            'LM3940': {
                'position': (15, 40),
                'rotation': 0,
                'package': 'SOT-23-5',
                'description': '3.3V voltage regulator'
            },
            'CRYSTAL_32MHZ': {
                'position': (25, 15),
                'rotation': 0,
                'package': 'HC49',
                'description': '32MHz crystal oscillator'
            }
        }
        return components

    def generate_electrode_connections(self):
        """Define electrode connection points"""
        electrodes = {
            'SS_ELECTRODE': {'pin': 'A0', 'connector': 'BNC-1', 'position': (5, 30)},
            'CU_ELECTRODE': {'pin': 'A1', 'connector': 'BNC-2', 'position': (5, 25)},
            'ZN_ELECTRODE': {'pin': 'A2', 'connector': 'BNC-3', 'position': (5, 20)},
            'AG_ELECTRODE': {'pin': 'A3', 'connector': 'BNC-4', 'position': (5, 15)},
            'PT_ELECTRODE': {'pin': 'A4', 'connector': 'BNC-5', 'position': (5, 10)}
        }
        return electrodes

    def generate_power_distribution(self):
        """Design power distribution network"""
        power_rails = {
            'VCC_3V3': {'width': 0.5, 'layer': 'TOP', 'current': '1A'},
            'VCC_5V': {'width': 0.3, 'layer': 'TOP', 'current': '500mA'},
            'GND': {'width': 0.6, 'layer': 'BOTTOM', 'current': '1.5A'},
            'AGND': {'width': 0.4, 'layer': 'INTERNAL1', 'current': '200mA'}
        }
        return power_rails

    def generate_eagle_script(self):
        """Generate Eagle CAD script for automated PCB generation"""
        script = """
# AyuSure PCB Generation Script for Eagle CAD
GRID MM;
SET WIRE_BEND 0;
LAYER 1 Top;

# Set board dimensions
RECT (0 0) (65 45);

# Place main components
ADD ESP32-WROOM-32 U1 (32.5 22.5);
ROTATE R0 U1;

ADD ADS1115 U2 (20 35);
ROTATE R90 U2;

ADD DS18B20 U3 (10 10);
ROTATE R0 U3;

ADD TCS3200 U4 (50 10);
ROTATE R0 U4;

ADD LM3940 U5 (15 40);
ROTATE R0 U5;

# Add passive components
ADD C0603 C1 (28 18);  # ESP32 decoupling
ADD C0603 C2 (36 18);  # ESP32 decoupling
ADD R0603 R1 (22 32);  # I2C pull-up SDA
ADD R0603 R2 (18 32);  # I2C pull-up SCL

# Power distribution
WIRE 'VCC' 0.5 (0 42) (65 42);
WIRE 'GND' 0.6 (0 3) (65 3);

# I2C bus routing
WIRE 'SDA' 0.2 (ESP32.GPIO21) (ADS1115.SDA);
WIRE 'SCL' 0.2 (ESP32.GPIO22) (ADS1115.SCL);

# Electrode connections
WIRE 'SS_SENSE' 0.3 (ADS1115.AIN0) (BNC1.CENTER);
WIRE 'CU_SENSE' 0.3 (ADS1115.AIN1) (BNC2.CENTER);
WIRE 'ZN_SENSE' 0.3 (ADS1115.AIN2) (BNC3.CENTER);
WIRE 'AG_SENSE' 0.3 (ADS1115.AIN3) (BNC4.CENTER);

# Add ground vias
VIA (10 10); VIA (20 20); VIA (30 30); VIA (40 20); VIA (50 10);

# Design rule check
DRC;

# Generate output files
CAM gerber.cam;
"""
        return script

    def calculate_impedance_matching(self):
        """Calculate trace impedance for high-speed signals"""
        # 50 ohm impedance calculation for 4-layer board
        trace_width = 0.13  # mm for 50 ohm impedance
        via_size = 0.2      # mm via drill

        return {
            'clock_traces': {'width': trace_width, 'impedance': 50},
            'data_traces': {'width': trace_width, 'impedance': 50},
            'via_specifications': {'drill': via_size, 'pad': via_size + 0.1}
        }

    def export_design_files(self):
        """Export complete design package"""
        design_package = {
            'board_info': self.board_dimensions,
            'components': self.generate_component_placement(),
            'electrodes': self.generate_electrode_connections(),
            'power_rails': self.generate_power_distribution(),
            'impedance': self.calculate_impedance_matching(),
            'eagle_script': self.generate_eagle_script()
        }

        return design_package

# Usage example
if __name__ == "__main__":
    pcb = AyuSurePCBDesign()
    design = pcb.export_design_files()

    print("AyuSure PCB Design Generated")
    print(f"Board: {design['board_info']['width']}x{design['board_info']['height']}mm")
    print(f"Components: {len(design['components'])} major components")
    print(f"Electrodes: {len(design['electrodes'])} sensing electrodes")

    # Save design to JSON for manufacturing
    with open('ayusure_pcb_design.json', 'w') as f:
        json.dump(design, f, indent=2)
