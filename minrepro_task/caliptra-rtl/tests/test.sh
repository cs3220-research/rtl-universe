#!/bin/bash
# Verifier entrypoint for the caliptra-rtl Harbor task.
# Runs the L0 Verilator regression against the agent's /app and writes a
# proportional reward in [0, 1] to /logs/verifier/reward.txt.

set -uo pipefail
mkdir -p /logs/verifier

export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic
export CALIPTRA_WORKSPACE=/tmp/caliptra_verifier_workspace
mkdir -p "${CALIPTRA_WORKSPACE}/scratch"

# ── Denominator ────────────────────────────────────────────────────────────
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=53
fi

echo "[verifier] Total tests to score against: ${TOTAL}"

# ── Step 1: Build Verilator simulation binary ──────────────────────────────
BUILD_DIR=/tmp/verifier_build
mkdir -p "${BUILD_DIR}"

echo "[verifier] Building Verilator simulation binary..."
if ! make -C "${BUILD_DIR}" -f "${CALIPTRA_ROOT}/tools/scripts/Makefile" \
    verilator-build 2>&1 | tee /logs/verifier/verilator_build.log; then
    echo "[verifier] ERROR: Verilator build failed"
    echo "0" > /logs/verifier/reward.txt
    echo "reward: 0.000000  (verilator build failed)"
    exit 0
fi
echo "[verifier] Verilator build complete"

# ── Step 2: Parse test list ────────────────────────────────────────────────
python3 /tests/parse_tests.py > /tmp/_test_list.json 2>/logs/verifier/parse.log

TOTAL_TESTS=$(python3 -c "import json; print(len(json.load(open('/tmp/_test_list.json'))))" 2>/dev/null || echo "${TOTAL}")

# ── Step 3: Run tests and count passes ────────────────────────────────────
echo "[verifier] Running regression tests (${TOTAL_TESTS} tests)..."
python3 /tests/run_tests.py 2>&1 | tee /logs/verifier/test.log

PASSED=$(cat /tmp/_passed_count 2>/dev/null || echo "0")

# ── Compute reward ─────────────────────────────────────────────────────────
python3 -c "
passed = int('${PASSED}')
total = int('${TOTAL}')
reward = passed / total if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"

# ── Optional: pytest for structured reporting ─────────────────────────────
if command -v uvx >/dev/null 2>&1; then
    uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
        pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
