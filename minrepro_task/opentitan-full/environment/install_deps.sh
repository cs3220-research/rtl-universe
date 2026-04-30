#!/bin/bash
# install_deps.sh — Install system dependencies for the OpenTitan Harbor task.
#
# OpenTitan is a Bazel-based project. Bazel manages most of its own toolchain
# deps (Clang via toolchains_llvm, Python via rules_python), but it still
# needs system-level packages to bootstrap.
#
# This script runs as root in the base stage.

set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt-get update -q && apt-get install -y --no-install-recommends \
    # Core build utilities
    build-essential \
    curl \
    git \
    wget \
    ca-certificates \
    # Python runtime (Bazel rules_python still needs system Python for bootstrap)
    python3 \
    python3-pip \
    python3-dev \
    python-is-python3 \
    # Java runtime for Bazel server
    default-jre-headless \
    # Required by OpenTitan apt-requirements.txt
    autoconf \
    brotli \
    cmake \
    file \
    g++ \
    lcov \
    libelf1 \
    libelf-dev \
    libssl-dev \
    libtool \
    lsb-release \
    make \
    openssl \
    perl \
    pkgconf \
    python3-setuptools \
    python3-wheel \
    srecord \
    tree \
    xmlstarlet \
    xsltproc \
    xxd \
    xz-utils \
    zip \
    zlib1g-dev \
    # For Bazel itself
    patch \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install pip packages needed for OpenTitan's Python scripts
# (Bazel rules_python manages most but some scripts run directly)
pip3 install --break-system-packages \
    mako \
    hjson \
    pyyaml \
    pyelftools \
    tabulate \
    pycryptodome \
    rich \
    pytest

echo "install_deps.sh complete."
