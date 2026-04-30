#!/bin/bash
# install_base.sh — Install base system toolchain dependencies for CVA6.
#
# Runs as root in the Docker base stage.
# Installs: build tools, Verilator 5.x (from source), RISC-V GNU toolchain
#           (pre-built binary from Embecosm), and riscv-tests binaries.

set -eux

RISCV_INSTALL_DIR="/opt/riscv"
NUM_JOBS="${NUM_JOBS:-$(nproc)}"

ln -snf /usr/share/zoneinfo/UTC /etc/localtime
echo "UTC" > /etc/timezone

apt-get update -qq
apt-get install -y -qq \
    build-essential ca-certificates curl git wget rsync \
    python3 python3-pip \
    autoconf automake flex bison \
    device-tree-compiler \
    libfl-dev help2man \
    libboost-regex-dev libboost-system-dev \
    libyaml-cpp-dev \
    pkg-config \
    libglib2.0-dev \
    zlib1g-dev \
    verilator \
    gcc-riscv64-unknown-elf

rm -rf /var/lib/apt/lists/*

verilator --version
riscv64-unknown-elf-gcc --version

# Set RISCV for downstream scripts
export PATH="${RISCV_INSTALL_DIR}/bin:${PATH}"

# ── Build riscv-tests binaries ────────────────────────────────────────────────
# Pinned to commit referenced in ci/build-riscv-tests.sh.
RISCV_TESTS_VERSION="eeacd5507db7a0f50ca8c4f27aff220fcbb60bdf"
RISCV_TESTS_INSTALL="/opt/riscv-tests"

mkdir -p /tmp/riscv-tests-build
cd /tmp/riscv-tests-build
git clone https://github.com/riscv/riscv-tests.git
cd riscv-tests
git checkout "${RISCV_TESTS_VERSION}"
git submodule update --init --recursive
autoconf
mkdir -p build
cd build
../configure --prefix="${RISCV_TESTS_INSTALL}"
make isa        -j"${NUM_JOBS}" > /dev/null
make benchmarks -j"${NUM_JOBS}" > /dev/null
make install
cd /
rm -rf /tmp/riscv-tests-build

echo "riscv-tests installed to ${RISCV_TESTS_INSTALL}"
ls "${RISCV_TESTS_INSTALL}/share/riscv-tests/isa/" | head -5

echo "Base install complete."
