#!/bin/bash
# Verifier entrypoint for the caliptra-rtl Harbor task (caliptra-full).
# Runs the L0 Verilator regression against the agent's /app and writes a
# proportional reward in [0, 1] to /logs/verifier/reward.txt.
#
# NO inline heredocs or complex inline Python. All Python logic is in
# separate scripts (parse_tests.py, run_tests.py, compute_reward.py).

set -uo pipefail
mkdir -p /logs/verifier

export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic
export CALIPTRA_WORKSPACE=/tmp/caliptra_verifier_workspace
mkdir -p "${CALIPTRA_WORKSPACE}/scratch"

# ── Denominator ───────────────────────────────────────────────────────────
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=53
fi
echo "[verifier] Total tests (denominator): ${TOTAL}"

# ── Step 1: Build Verilator simulation binary ─────────────────────────────
BUILD_DIR=/tmp/verifier_build
mkdir -p "${BUILD_DIR}"

echo "[verifier] Building Verilator simulation binary..."
if ! make -C "${BUILD_DIR}" \
        -f "${CALIPTRA_ROOT}/tools/scripts/Makefile" \
        verilator-build \
        2>&1 | tee /logs/verifier/verilator_build.log; then
    echo "[verifier] ERROR: Verilator build failed"
    echo "0" > /logs/verifier/reward.txt
    echo "reward: 0.000000  (verilator build failed)"
    exit 0
fi
echo "[verifier] Verilator build complete."

# ── Step 2: Parse test list ───────────────────────────────────────────────
echo "[verifier] Parsing test list..."
python3 /tests/parse_tests.py > /tmp/_test_list.json 2>/logs/verifier/parse.log

# ── Step 3: Run tests ─────────────────────────────────────────────────────
echo "[verifier] Running regression tests..."
python3 /tests/run_tests.py 2>&1 | tee /logs/verifier/test.log

# ── Step 4: Compute reward ────────────────────────────────────────────────
python3 /tests/compute_reward.py

echo "[verifier] Done. Reward: $(cat /logs/verifier/reward.txt)"

# ── Optional: pytest structured report ────────────────────────────────────
if command -v uvx >/dev/null 2>&1; then
    uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
        pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
