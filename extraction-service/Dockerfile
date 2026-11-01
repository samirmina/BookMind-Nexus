# Pix2Text Extraction Service - Multi-arch Dockerfile
# Supports NVIDIA (CUDA), AMD (ROCm), Intel (IPEX/XPU), and CPU-only builds

# =============================================================================
# BASE STAGE - Common dependencies
# =============================================================================
FROM python:3.12-slim AS base

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    cmake \
    git \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    wget \
    ca-certificates \
    intel-gpu-tools \
    && rm -rf /var/lib/apt/lists/*

# Set Python path
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# =============================================================================
# CUDA BUILD (NVIDIA) - Default for GPU
# =============================================================================
FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04 AS cuda-builder

# Install Python and system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.12 \
    python3.12-venv \
    python3.12-dev \
    build-essential \
    cmake \
    git \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create venv
RUN python3.12 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Upgrade pip
RUN pip install --upgrade pip setuptools wheel

# Install PyTorch with CUDA 12.4
RUN pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124

# Install Pix2Text and dependencies
RUN pip install pix2text onnxruntime-gpu opencv-python-headless

# =============================================================================
# ROCm BUILD (AMD) - For AMD GPUs
# =============================================================================
FROM rocm/pytorch:latest AS rocm-builder

# Install system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.12 \
    python3.12-venv \
    python3.12-dev \
    build-essential \
    cmake \
    git \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create venv
RUN python3.12 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Upgrade pip
RUN pip install --upgrade pip setuptools wheel

# PyTorch with ROCm already in base image
# Install Pix2Text and dependencies
RUN pip install pix2text onnxruntime-gpu opencv-python-headless

# =============================================================================
# INTEL GPU (IPEX/XPU) BUILD - For Intel Arc/Integrated GPUs
# =============================================================================
FROM intel/intel-extension-for-pytorch:2.4.0-xpu AS intel-builder

# Install system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.12 \
    python3.12-venv \
    python3.12-dev \
    build-essential \
    cmake \
    git \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    wget \
    ca-certificates \
    intel-gpu-tools \
    && rm -rf /var/lib/apt/lists/*

# Create venv
RUN python3.12 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Upgrade pip
RUN pip install --upgrade pip setuptools wheel

# Intel Extension for PyTorch (IPEX) already in base image
# Install Pix2Text and dependencies
RUN pip install pix2text onnxruntime opencv-python-headless

# =============================================================================
# CPU-ONLY BUILD
# =============================================================================
FROM python:3.12-slim AS cpu-builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    cmake \
    git \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/venv/bin:$PATH"
RUN python3.12 -m venv /opt/venv

RUN pip install --upgrade pip setuptools wheel

# Install PyTorch CPU
RUN pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu

# Install Pix2Text and dependencies
RUN pip install pix2text onnxruntime opencv-python-headless

# =============================================================================
# FINAL RUNTIME STAGE - Select appropriate builder via BUILD_TARGET arg
# =============================================================================
FROM python:3.12-slim AS runtime

# Install runtime dependencies only
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgl1-mesa-glx \
    libgomp1 \
    ca-certificates \
    intel-gpu-tools \
    && rm -rf /var/lib/apt/lists/*

# Copy venv from selected builder
ARG BUILD_TARGET=cpu
COPY --from=${BUILD_TARGET}-builder /opt/venv /opt/venv

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# Copy application code
COPY main.py requirements.txt ./

# Create cache and tmp directories
RUN mkdir -p /app/cache /app/tmp /app/storage/books /app/external/pdfs

# Health check endpoint dependencies
RUN pip install --no-cache-dir fastapi uvicorn

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the service
CMD ["python", "main.py"]