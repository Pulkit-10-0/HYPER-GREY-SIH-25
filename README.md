# AyuSure - AI-Powered Herb Authentication System

An advanced electronic tongue system for Ayurvedic herb authentication using AI-powered taste profile analysis, adulteration detection, and phytochemical quantification.

## Project Overview

AyuSure combines hardware sensors, AI models, and software applications to provide comprehensive herb authentication and quality analysis for Ayurvedic medicine practitioners, researchers, and manufacturers.

## Project Structure

### Dataset
Contains all data used for training, testing, and validation of the AI models.

- **`benchmarks/`** - Performance benchmark data and test results
- **`processed/`** - Cleaned and preprocessed sensor data ready for model training
- **`raw-data/`** - Original sensor readings from electronic tongue hardware
- **`synthetic-data/`** - Generated training data to augment real sensor readings
- **`validation/`** - Validation datasets and test results for model evaluation

### Documentation
Comprehensive project documentation covering all aspects of the system.

- **`api-docs/`** - REST API documentation and endpoint specifications
- **`research/`** - Research papers, studies, and scientific documentation
- **`technical/`** - Technical specifications and system architecture docs
- **`user-guides/`** - End-user manuals and tutorials

### Hardware
Physical electronic tongue system components and firmware.

- **`assembly/`** - Hardware assembly instructions and component lists
- **`calibration/`** - Sensor calibration systems and procedures
  - **Tech Stack**: Python, NumPy, SciPy
- **`firmware/`** - Microcontroller firmware for sensor data collection
  - **Tech Stack**: Arduino IDE, C++, ESP32/Arduino platforms
- **`schematics/`** - PCB designs and electrical schematics
  - **Tech Stack**: Python (PCB design automation)

### Software
Complete software ecosystem for data processing, AI inference, and user interfaces.

#### AI Models
Machine learning pipeline for herb authentication and analysis.
- **Tech Stack**: Python, scikit-learn, pandas, NumPy, joblib
- **Models**: Neural Networks (MLP), Isolation Forest, Random Forest
- **Features**: Taste profiling, adulteration detection, phytochemical analysis

#### Backend
RESTful API server with real-time data processing capabilities.
- **Tech Stack**: Python, Flask, Flask-RESTful, Flask-SocketIO
- **Database**: MongoDB, Redis
- **Features**: JWT authentication, WebSocket support, background tasks (Celery)
- **Testing**: pytest, pytest-flask
- **Production**: Gunicorn, Docker

#### Frontend
Modern web application for system control and data visualization.
- **Tech Stack**: Next.js 15, React 19, TypeScript
- **UI Framework**: Radix UI, Tailwind CSS
- **Features**: Real-time dashboards, data visualization (Recharts), responsive design
- **Development**: ESLint, PostCSS

#### 📱 Mobile
Cross-platform mobile applications for field use.
- **Platforms**: Android, iOS
- **Tech Stack**: React Native / Flutter (to be determined from further inspection)

#### Deployment
Containerized deployment and orchestration.
- **Tech Stack**: Docker, Docker Compose
- **Features**: Multi-service orchestration, environment configuration

#### Scripts
Automation and utility scripts for system management.
- **Tech Stack**: Bash, JavaScript (Node.js)
- **Features**: Deployment automation, database initialization

## Key Features

### Advanced Sensor Analysis
- Multi-electrode electronic tongue system
- Environmental parameter monitoring (temperature, humidity, pH, TDS)
- UV spectroscopy integration
- Color analysis capabilities

### AI-Powered Authentication
- **Taste Profile Analysis**: 6-parameter taste profiling (sweet, sour, salty, pungent, bitter, astringent)
- **Adulteration Detection**: Anomaly detection using Isolation Forest
- **Phytochemical Quantification**: Bioactive compound analysis using Random Forest

### Full-Stack Solution
- Real-time sensor data processing
- WebSocket-based live updates
- RESTful API for integration
- Modern responsive web interface
- Mobile applications for field use

### Data Management
- Comprehensive data pipeline from raw sensors to insights
- Synthetic data generation for model training
- Performance benchmarking and validation
- Export capabilities for research and compliance

## Tech Stack Summary

| Component | Primary Technologies |
|-----------|---------------------|
| **AI/ML** | Python, scikit-learn, pandas, NumPy |
| **Backend** | Flask, MongoDB, Redis, Celery, WebSockets |
| **Frontend** | Next.js, React, TypeScript, Tailwind CSS |
| **Mobile** | React Native/Flutter |
| **Hardware** | Arduino, ESP32, C++ |
| **Deployment** | Docker, Docker Compose |
| **Database** | MongoDB (primary), Redis (caching) |
| **Testing** | pytest, Jest, React Testing Library |

## Getting Started

1. **Hardware Setup**: Follow assembly instructions in `hardware/assembly/`
2. **Firmware Installation**: Flash firmware from `hardware/firmware/` to your microcontrollers
3. **Backend Setup**: Install dependencies and configure the Flask API in `software/backend/`
4. **Frontend Setup**: Install Node.js dependencies and start the Next.js app in `software/frontend/`
5. **AI Models**: Train models using the pipeline in `software/ai-models/`

## Use Cases

- **Ayurvedic Medicine**: Authenticate herbs and detect adulterants
- **Quality Control**: Ensure herb purity in manufacturing
- **Research**: Analyze phytochemical profiles for studies
- **Supply Chain**: Verify herb authenticity throughout distribution
- **Regulatory Compliance**: Meet quality standards and documentation requirements


---

*AyuSure represents the intersection of traditional Ayurvedic knowledge and modern AI technology, providing scientific validation for ancient herbal wisdom.*