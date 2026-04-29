#!/bin/bash
# count_tests.sh — Run the L0 regression on green source and capture the pass count.
# Called during Docker build (Stage 2: count).
# Outputs: /tmp/_total, /tmp/_all_tests, /tmp/_failed_tests
#
# NO inline heredocs or inline Python — all Python logic is in separate .py files
# (parse_l0_tests.py, run_l0_tests.py) that are COPY-ed into /tmp/ before this
# script runs.

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

# ── Step 2: Parse the L0 regression YAML for test names ──────────────────
echo "[count_tests] Parsing L0_regression.yml..."
python3 /tmp/parse_l0_tests.py >/tmp/_test_list.json 2>&1

TEST_COUNT=$(python3 -c "import json,sys; print(len(json.load(open('/tmp/_test_list.json'))))")
echo "[count_tests] Found ${TEST_COUNT} tests to run."

# ── Step 3: Run each test and record passes ───────────────────────────────
echo "[count_tests] Running regression tests..."
export BUILD_DIR
export TEST_TIMEOUT=300
python3 /tmp/run_l0_tests.py

echo "[count_tests] Total passing tests: $(cat /tmp/_total)"
echo "[count_tests] Passing tests written to /tmp/_all_tests"
