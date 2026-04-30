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
    verilator

rm -rf /var/lib/apt/lists/*

verilator --version

# ── Install RISC-V GNU toolchain (Embecosm multilib) ─────────────────────────
# CVA6 CI uses this exact tarball. Despite the "riscv32" name, it's a multilib
# toolchain that supports both rv32 and rv64 targets. The Debian apt package
# gcc-riscv64-unknown-elf does NOT have the same multilib support and fails
# to build riscv-tests rv32 variants.
EMBECOSM_BASE="https://buildbot.embecosm.com/job/riscv32-gcc-ubuntu2204-release/10/artifact"
RISCV_TARBALL="riscv32-embecosm-ubuntu2204-gcc13.2.0.tar.gz"

mkdir -p "${RISCV_INSTALL_DIR}"
cd /tmp
wget -q "${EMBECOSM_BASE}/${RISCV_TARBALL}" --no-check-certificate
tar -x -f "${RISCV_TARBALL}" --strip-components=1 -C "${RISCV_INSTALL_DIR}"
rm -f "${RISCV_TARBALL}"

export PATH="${RISCV_INSTALL_DIR}/bin:${PATH}"
riscv32-unknown-elf-gcc --version

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
# The Embecosm toolchain uses riscv32-unknown-elf- prefix (it's multilib).
# riscv-tests autoconf defaults to looking for riscv64-unknown-elf-gcc,
# so we must pass --host to tell it the actual target triple.
../configure --prefix="${RISCV_TESTS_INSTALL}" --host=riscv32-unknown-elf
make isa        -j"${NUM_JOBS}" > /dev/null
# Benchmarks use a hardcoded riscv64-unknown-elf- prefix that doesn't match
# the Embecosm riscv32 toolchain. Skip them — CVA6 CI test lists only use
# ISA tests for the smoke/E2E variants. Build benchmarks best-effort.
make benchmarks -j"${NUM_JOBS}" > /dev/null 2>&1 || echo "Warning: benchmarks build failed (non-fatal)"
make install
cd /
rm -rf /tmp/riscv-tests-build

echo "riscv-tests installed to ${RISCV_TESTS_INSTALL}"
ls "${RISCV_TESTS_INSTALL}/share/riscv-tests/isa/" | head -5

echo "Base install complete."
