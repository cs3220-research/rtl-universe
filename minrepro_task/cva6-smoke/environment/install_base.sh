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
    zlib1g-dev

rm -rf /var/lib/apt/lists/*

# ── Install Verilator 5.008 from source ──────────────────────────────────────
# The project requires Verilator 5.x for --no-timing and other flags.
# Pinned to the tag used in ci/install-verilator.sh.
VERILATOR_VERSION="v5.008"
VERILATOR_INSTALL_DIR="/opt/verilator"

mkdir -p /tmp/verilator-build
cd /tmp/verilator-build
git clone https://github.com/verilator/verilator.git
cd verilator
git checkout "${VERILATOR_VERSION}"
autoconf
./configure --prefix="${VERILATOR_INSTALL_DIR}"
make -j"${NUM_JOBS}"
make install
cd /
rm -rf /tmp/verilator-build

ln -sf "${VERILATOR_INSTALL_DIR}/bin/verilator" /usr/local/bin/verilator
verilator --version

# ── Install RISC-V GNU toolchain (pre-built Embecosm binary) ─────────────────
# Same source as ci/install-toolchain.sh.
EMBECOSM_BASE="https://buildbot.embecosm.com/job/riscv32-gcc-ubuntu2204-release/10/artifact"
RISCV64_ELF_GCC="riscv32-embecosm-ubuntu2204-gcc13.2.0.tar.gz"

mkdir -p "${RISCV_INSTALL_DIR}"
cd /tmp
wget -q "${EMBECOSM_BASE}/${RISCV64_ELF_GCC}" --no-check-certificate
tar -x -f "${RISCV64_ELF_GCC}" --strip-components=1 -C "${RISCV_INSTALL_DIR}"
rm -f "${RISCV64_ELF_GCC}"

# Symlink riscv64-unknown-elf-gcc to standard bin name used by riscv-tests build
export PATH="${RISCV_INSTALL_DIR}/bin:${PATH}"
riscv64-unknown-elf-gcc --version || riscv32-unknown-elf-gcc --version || true

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
