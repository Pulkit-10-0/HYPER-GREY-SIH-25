# AyuSure Deployment

## Overview

AyuSure is an E-Tongue backend system with AI-powered analysis, REST API, background task processing, and optional monitoring.
This repository provides a Docker-based deployment setup with `frontend/`, `backend/`, and supporting services (MongoDB, Redis, Celery, Nginx).

---

## Repository Structure

```
.
├── backend/             # Flask API, AI pipeline, Celery tasks
├── frontend/            # React/Next.js or other frontend code
├── ai-models/           # Trained ML models
├── nginx/               # Reverse proxy configuration
├── scripts/             # Init scripts (e.g. MongoDB seed)
├── logs/                # Runtime logs
├── Dockerfile           # Multi-stage backend Docker build
├── docker-compose.yml   # Service orchestration
├── .env                 # Environment configuration
└── README.md            # This file
```

---

## Prerequisites

* Docker ≥ 20.10
* Docker Compose ≥ 1.29
* `.env` file configured with secure credentials

---

## Configuration

1. Copy `.env.example` to `.env`.
2. Edit `.env` with production values for:

   * MongoDB credentials
   * Redis password
   * Secret keys
   * CORS origins
   * Email/monitoring options if required

---

## Building and Running

### Development

Run backend with MongoDB and Redis only:

```bash
docker compose up --build backend mongodb redis
```

### Full Production Stack

Includes Celery workers, Nginx reverse proxy, and monitoring:

```bash
docker compose --profile with-nginx --profile monitoring up --build -d
```

### Useful Commands

* Start all services:

  ```bash
  docker compose up -d
  ```
* Stop all services:

  ```bash
  docker compose down
  ```
* View logs:

  ```bash
  docker compose logs -f backend
  ```

---

## Services

* **Backend API**: Flask + Gunicorn ([http://localhost:5000](http://localhost:5000))
* **MongoDB**: Database for structured storage
* **Redis**: Cache and Celery broker
* **Celery Worker**: Background AI tasks
* **Celery Beat**: Periodic scheduled tasks
* **Flower**: Celery monitoring dashboard ([http://localhost:5555](http://localhost:5555))
* **Nginx**: Reverse proxy and SSL termination ([http://localhost](http://localhost), [https://localhost](https://localhost))

---

## Deployment Notes

* Always change `SECRET_KEY`, `JWT_SECRET_KEY`, and database credentials in production.
* For HTTPS, place SSL certificates in `nginx/ssl` and update `nginx.conf`.
* Mount persistent volumes for MongoDB and Redis for production safety.
* Configure monitoring with Sentry DSN if required.

---

## Frontend

The `frontend/` folder is included for UI deployment. Build separately or containerize with its own Dockerfile and connect it to the same `ayusure_network`.

---

## Backup and Monitoring

* MongoDB and Redis volumes are defined for persistence.
* Optional backup schedules can be enabled via environment variables.
* Logs are written to `/logs` inside containers and mounted locally.

---
