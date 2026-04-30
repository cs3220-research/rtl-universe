#!/bin/bash
# build_spike.sh — Build Spike RISC-V ISS from the pinned commit.
#
# This runs as root in the spike-builder Docker stage.
# Spike is installed to /opt/spike.
#
# Pinned commit from ci/install-spike.sh:
#   5f76a0d1fa68bb80560cb890405c42041f744e89
#
# The resulting libraries are used by the Verilator testbench DPI:
#   /opt/spike/lib/libriscv.so
#   /opt/spike/lib/libfesvr.so
#   /opt/spike/lib/libdisasm.so
#   /opt/spike/lib/libyaml-cpp.so
#   /opt/spike/include/...

set -eux

SPIKE_VERSION="5f76a0d1fa68bb80560cb890405c42041f744e89"
SPIKE_INSTALL_DIR="/opt/spike"
NUM_JOBS="${NUM_JOBS:-$(nproc)}"

apt-get update -qq
apt-get install -y -qq \
    build-essential git curl wget \
    device-tree-compiler \
    libboost-regex-dev libboost-system-dev \
    libyaml-cpp-dev \
    autoconf automake \
    pkg-config

mkdir -p /tmp/spike-build
cd /tmp/spike-build

git clone https://github.com/riscv/riscv-isa-sim.git riscv-isa-sim
cd riscv-isa-sim
git checkout "${SPIKE_VERSION}"

mkdir -p build
cd build
../configure --prefix="${SPIKE_INSTALL_DIR}"
make -j"${NUM_JOBS}"
make install

echo "Spike build complete. Installed to ${SPIKE_INSTALL_DIR}"
ls "${SPIKE_INSTALL_DIR}/lib/"
