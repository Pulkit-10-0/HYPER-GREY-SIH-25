# Multi-stage Docker build for AyuSure Backend API

# Stage 1: Base Python environment
FROM python:3.11-slim as base

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set work directory
WORKDIR /app

# Stage 2: Dependencies
FROM base as dependencies

# Copy requirements first for better caching
COPY backend/requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Stage 3: Application
FROM dependencies as application

# Copy application code
COPY backend/ ./backend/
COPY ai-models/ ./ai-models/

# Create necessary directories
RUN mkdir -p /app/instance/reports /app/logs

# Change ownership to app user
RUN chown -R appuser:appuser /app

# Switch to app user
USER appuser

# Expose port
EXPOSE 5000

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

# Default command
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "4", "--worker-class", "eventlet", "--timeout", "120", "backend.app:create_app()[0]"]

# Stage 4: Development
FROM application as development

# Install development dependencies
USER root
RUN pip install --no-cache-dir pytest pytest-flask pytest-cov black flake8

# Switch back to app user
USER appuser

# Override command for development
CMD ["python", "backend/run.py"]

# Stage 5: Production
FROM application as production

# Production optimizations
ENV FLASK_ENV=production

# Use production WSGI server
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "4", "--worker-class", "eventlet", "--timeout", "120", "--access-logfile", "-", "--error-logfile", "-", "backend.app:create_app()[0]"]