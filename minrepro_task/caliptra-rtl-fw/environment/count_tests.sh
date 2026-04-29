#!/bin/bash
# count_tests.sh — Run the E2E test subset on green source and capture pass count.
# Called during Docker build (Stage 2: count).
# Outputs: /tmp/_total, /tmp/_all_tests, /tmp/_e2e_tests
#
# NO inline heredocs or inline Python — all Python logic is in run_e2e_tests.py.

set -euo pipefail

export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic
export CALIPTRA_WORKSPACE=/tmp/caliptra_workspace

mkdir -p "${CALIPTRA_WORKSPACE}/scratch"

# ── Step 1: Build the Verilator simulation binary ─────────────────────────
BUILD_DIR=/tmp/verilated_image
mkdir -p "${BUILD_DIR}"

echo "[count_tests] Building Verilator simulation binary..."
make -C "${BUILD_DIR}" \
    -f "${CALIPTRA_ROOT}/tools/scripts/Makefile" \
    verilator-build \
    2>&1 | tail -30

echo "[count_tests] Verilator build complete."

# ── Step 2: Run E2E tests ─────────────────────────────────────────────────
echo "[count_tests] Running E2E subset..."
export BUILD_DIR
python3 /tmp/run_e2e_tests.py

echo "[count_tests] Total passing E2E tests: $(cat /tmp/_total)"
echo "[count_tests] Passing tests written to /tmp/_all_tests"
