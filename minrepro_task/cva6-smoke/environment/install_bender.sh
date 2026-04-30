#!/bin/bash
# install_bender.sh — Install Bender PULP dependency manager.
# Runs as root in the Docker base stage.

set -eux

BENDER_VERSION="0.31.0"
BENDER_URL="https://github.com/pulp-platform/bender/releases/download/v${BENDER_VERSION}/bender-${BENDER_VERSION}-x86_64-linux-gnu-debian12.tar.gz"

curl -fsSL "${BENDER_URL}" -o /tmp/bender.tar.gz
tar xzf /tmp/bender.tar.gz -C /usr/local/bin bender
rm /tmp/bender.tar.gz
chmod +x /usr/local/bin/bender
bender --version
echo "Bender ${BENDER_VERSION} installed."
