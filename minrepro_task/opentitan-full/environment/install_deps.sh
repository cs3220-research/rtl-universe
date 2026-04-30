#!/bin/bash
# install_deps.sh — Install system dependencies for the OpenTitan Harbor task.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

apt-get update -q && apt-get install -y --no-install-recommends \
    build-essential curl git wget ca-certificates \
    python3 python3-pip python3-dev python-is-python3 \
    default-jre-headless \
    autoconf brotli cmake file g++ lcov \
    libelf1 libelf-dev libssl-dev libtool \
    lsb-release make openssl perl pkgconf \
    python3-setuptools python3-wheel \
    srecord tree xmlstarlet xsltproc xxd xz-utils \
    zip zlib1g-dev patch unzip \
    && rm -rf /var/lib/apt/lists/*

pip3 install --break-system-packages \
    mako hjson pyyaml pyelftools tabulate pycryptodome rich pytest

echo "install_deps.sh complete."
