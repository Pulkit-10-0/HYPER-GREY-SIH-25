# AyuSure API Documentation(Work in Progress)

**Team:** Hyper Grey - MSIT  
**Project:** AyuSure E-Tongue System API  
**Version:** v1.0  
**Base URL:** `https://api.ayusure.in/v1`  

---

## Quick Start

### Authentication
All API endpoints require JWT authentication. Obtain a token through the login endpoint:

```bash
curl -X POST https://api.ayusure.in/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "your_username", "password": "your_password"}'
```

### Base Request Format
```bash
curl -X GET https://api.ayusure.in/v1/endpoint \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

---

## Core Endpoints

### Authentication
- `POST /auth/login` - User login and token generation
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Invalidate token

### Device Management
- `GET /devices` - List all devices
- `POST /devices/register` - Register new device
- `GET /devices/{id}` - Get device details
- `PUT /devices/{id}/calibration` - Update calibration

### Data Collection
- `POST /data/collect` - Receive sensor readings from devices
- `POST /data/analyze` - Trigger AI analysis
- `GET /data/readings/{id}` - Get specific reading

### Analysis Results
- `GET /analysis/results/{id}` - Get analysis results
- `GET /analysis/taste-profile/{id}` - Get taste analysis
- `GET /analysis/authenticity/{id}` - Get authenticity assessment

### Reports
- `POST /reports/generate` - Generate quality reports
- `GET /reports/{id}/download` - Download generated reports

---

## Response Format

### Success Response
```json
{
  "status": "success",
  "data": {
    // Response data
  },
  "timestamp": "2025-09-26T14:30:00Z",
  "request_id": "req_123456"
}
```

### Error Response
```json
{
  "status": "error",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Request validation failed",
    "details": {}
  },
  "timestamp": "2025-09-26T14:30:00Z",
  "request_id": "req_123456"
}
```

---

## 🔌 Real-time Features

### WebSocket Connection
```javascript
const socket = io('https://api.ayusure.in', {
  auth: {
    token: 'YOUR_JWT_TOKEN'
  }
});

socket.on('new_reading', (data) => {
  console.log('New sensor reading:', data);
});
```

### Server-Sent Events
```javascript
const eventSource = new EventSource(
  'https://api.ayusure.in/v1/stream/readings/device_123'
);

eventSource.onmessage = function(event) {
  const data = JSON.parse(event.data);
  console.log('Real-time data:', data);
};
```

---

## Data Schemas

### Sensor Reading Schema
```json
{
  "device_id": "ESP32_MAC_ADDRESS",
  "timestamp": "2025-09-26T14:30:00Z",
  "measurement_id": 12345,
  "electrodes": [1.420, 1.680, 1.890, 2.210, 2.030],
  "environmental": {
    "temperature": 25.3,
    "humidity": 68.5,
    "ph_voltage": 2.045,
    "tds_voltage": 1.234,
    "uv_intensity": 2.5,
    "moisture_percent": 15.2
  },
  "color_rgb": [120, 180, 90],
  "system_status": {
    "battery_voltage": 4.1,
    "wifi_rssi": -45,
    "free_heap": 245760
  }
}
```

### Analysis Result Schema
```json
{
  "reading_id": "reading_object_id",
  "analysis_timestamp": "2025-09-26T14:35:00Z",
  "predictions": {
    "taste_profile": {
      "sweet": 38.5,
      "sour": 18.2,
      "bitter": 28.7,
      "pungent": 72.3,
      "astringent": 52.4
    },
    "authenticity": {
      "is_authentic": true,
      "confidence_score": 0.92,
      "authenticity_percentage": 94.2
    },
    "phytochemicals": {
      "alkaloids": 10.2,
      "flavonoids": 9.8,
      "saponins": 13.2,
      "tannins": 5.4,
      "glycosides": 4.6
    }
  },
  "quality_metrics": {
    "overall_score": 91.5,
    "grade": "A",
    "compliance_status": "passed"
  }
}
```

---

## Rate Limits

- **Authentication:** 10 requests/minute per IP
- **Data Collection:** 1000 requests/hour per device
- **Analysis Requests:** 100 requests/hour per user
- **Report Generation:** 10 requests/hour per user

---

## 🔧 SDKs and Libraries

### Python SDK
```bash
pip install ayusure-python-sdk
```

```python
from ayusure import AyuSureClient

client = AyuSureClient(
    base_url="https://api.ayusure.in/v1",
    api_key="your_api_key"
)

# Submit sensor reading
result = client.data.collect({
    "device_id": "ESP32_001",
    "electrodes": [1.42, 1.68, 1.89, 2.21, 2.03],
    # ... additional data
})
```

### JavaScript SDK
```bash
npm install @ayusure/js-sdk
```

```javascript
import { AyuSureClient } from '@ayusure/js-sdk';

const client = new AyuSureClient({
  baseURL: 'https://api.ayusure.in/v1',
  apiKey: 'your_api_key'
});

// Get analysis results
const results = await client.analysis.getResults('reading_id');
```

---

## Additional Resources

- **OpenAPI Specification:** [openapi_specification.yaml](./openapi_specification.yaml)
- **Postman Collection:** [postman_collection.json](./postman_collection.json)
- **Integration Examples:** [integration_examples/](./integration_examples/)
- **SDK Documentation:** [sdk_documentation.md](./sdk_documentation.md)



**Last Updated:** September 26, 2025  
