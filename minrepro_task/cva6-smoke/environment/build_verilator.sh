#!/bin/bash
# build_verilator.sh — Build Verilator 5.008 from source.
# Runs in an isolated Docker builder stage.
set -eux

apt-get update -qq
apt-get install -y -qq \
    build-essential ca-certificates git \
    autoconf flex bison \
    libfl-dev help2man \
    python3

VERILATOR_VERSION="v5.008"
VERILATOR_INSTALL="/opt/verilator"

git clone https://github.com/verilator/verilator.git /tmp/verilator
cd /tmp/verilator
git checkout "${VERILATOR_VERSION}"
autoconf
./configure --prefix="${VERILATOR_INSTALL}"
make -j"$(nproc)"
make install
rm -rf /tmp/verilator

echo "Verilator installed: $($VERILATOR_INSTALL/bin/verilator --version)"
