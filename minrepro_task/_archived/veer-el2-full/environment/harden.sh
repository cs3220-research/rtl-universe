#!/bin/bash
# Harden the skeleton workspace by removing Verilator-generated artifacts
# that would reveal the green implementation to the agent.
#
# Keeps: pre-installed tools, Python packages, test source files.
# Removes:
#   Category 1 - Generated/elaborated sources (Verilator C++ from each block)
#   Category 2 - Compiled sim binaries (sim-build* directories)
#   Category 3 - Test logs and coverage data from the warm run
set -euo pipefail

cd /app

# Remove all Verilator build outputs from block tests.
# Each block test builds into verification/block/<block>/sim-build*/
find /app/verification/block -type d -name "sim-build*" -exec rm -rf {} + 2>/dev/null || true
find /app/verification/block -type d -name "sim-build-*" -exec rm -rf {} + 2>/dev/null || true

# Remove Verilator build outputs from top-level pyuvm test.
find /app/verification/top -type d -name "sim-build*" -exec rm -rf {} + 2>/dev/null || true
find /app/verification/top -type d -name "sim" -exec rm -rf {} + 2>/dev/null || true

# Remove generated VeeR config snapshots (produced by veer.config during warm).
# The agent must regenerate these by calling veer.config.
find /app/verification -name "snapshots" -type d -exec rm -rf {} + 2>/dev/null || true

# Remove nox virtualenvs and nox cache (contain build artifacts).
rm -rf /app/.nox 2>/dev/null || true

# Remove test result XML files and logs from warm run.
find /app/verification -name "*.xml" -delete 2>/dev/null || true
find /app/verification -name "*.log" -delete 2>/dev/null || true
find /app/verification -name "*.dat" -delete 2>/dev/null || true
find /app/verification -name "parseResultsXML.log" -delete 2>/dev/null || true

# Remove coverage data.
find /app/verification -name "coverage*.dat" -delete 2>/dev/null || true
find /app/verification -name "*.lcov" -delete 2>/dev/null || true

echo "harden.sh done — sim-build dirs, snapshots, nox cache, and logs removed"
