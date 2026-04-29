#!/bin/bash
# Install Python dependencies for VeeR EL2 verification (E2E variant).
# Uses --break-system-packages because Debian bookworm treats the system
# Python as externally managed and refuses bare pip3 install.
set -euo pipefail

pip3 install --break-system-packages \
    cocotb==1.8.0 \
    cocotb-bus==0.2.1 \
    cocotb-coverage==1.1.0 \
    cocotb-test==0.2.4 \
    pytest==7.4.1 \
    pytest-html==3.2.0 \
    pytest-timeout==2.1.0 \
    pytest-md==0.2.0 \
    pyuvm==2.9.1 \
    scipy==1.13.1 \
    nox

echo "Python deps installed OK"
