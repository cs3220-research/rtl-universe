#!/bin/bash
# Harden the skeleton workspace for the E2E task.
# Removes Verilator-generated artifacts from the E2E test count run.
set -euo pipefail

cd /app

# Remove Verilator build outputs from top-level pyuvm test.
find /app/verification/top -type d -name "sim-build*" -exec rm -rf {} + 2>/dev/null || true
find /app/verification/top -type d -name "sim" -exec rm -rf {} + 2>/dev/null || true

# Remove generated VeeR config snapshots.
find /app/verification -name "snapshots" -type d -exec rm -rf {} + 2>/dev/null || true

# Also clean block test build artifacts (not scored but present in warm_src).
find /app/verification/block -type d -name "sim-build*" -exec rm -rf {} + 2>/dev/null || true

# Remove nox virtualenvs and cache.
rm -rf /app/.nox 2>/dev/null || true

# Remove test result files and logs from warm run.
find /app/verification -name "*.xml" -delete 2>/dev/null || true
find /app/verification -name "*.log" -delete 2>/dev/null || true
find /app/verification -name "*.html" -delete 2>/dev/null || true
find /app/verification -name "*.dat" -delete 2>/dev/null || true
find /tmp -name "e2e_*.log" -delete 2>/dev/null || true
find /tmp -name "e2e_*.html" -delete 2>/dev/null || true

echo "harden_e2e.sh done — Verilator build artifacts and test logs removed"
