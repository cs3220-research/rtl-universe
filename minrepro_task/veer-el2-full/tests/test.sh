#!/bin/bash
# Verifier entrypoint for the veer-el2 all-tests Harbor task.
#
# Runs all block-level cocotb tests via nox, counts passing test functions,
# and writes a proportional reward in [0, 1] to /logs/verifier/reward.txt.
#
# Scoring denominator: the number of test functions that passed on the
# green source, stored in /app/.harbor/total_tests at image build time.

set -u
mkdir -p /logs/verifier
cd /app

export RV_ROOT=/app
export PATH="${HOME}/.local/bin:${PATH}"

# Denominator
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=0
fi

echo "=== VeeR EL2 verifier: total_tests=${TOTAL} ==="

PASSED=0

# ---------------------------------------------------------------------------
# 1. Block-level cocotb tests via nox
# ---------------------------------------------------------------------------
echo "--- Running block-level cocotb tests via nox ---"
cd /app/verification/block

pip3 install --user --quiet -r requirements.txt 2>&1 | tail -5 || true

nox -t tests 2>&1 | tee /logs/verifier/nox.log || true

# Parse cocotb results.xml files to count passing test functions
BLOCK_PASSED=$(python3 - <<'PYEOF' 2>/dev/null
import os, glob
from xml.etree import ElementTree as ET
passed = 0
for xml_file in glob.glob('/app/verification/block/**/*.xml', recursive=True):
    try:
        tree = ET.parse(xml_file)
        for ts in tree.iter('testsuite'):
            for tc in ts.iter('testcase'):
                failures = list(tc.iter('failure')) + list(tc.iter('error'))
                if not failures:
                    passed += 1
    except Exception:
        pass
print(passed)
PYEOF
)
BLOCK_PASSED="${BLOCK_PASSED:-0}"
echo "Block-level cocotb passed: ${BLOCK_PASSED}"
PASSED=$((PASSED + BLOCK_PASSED))

# ---------------------------------------------------------------------------
# 2. Top-level Verilator simulation tests
# ---------------------------------------------------------------------------
echo "--- Running top-level Verilator simulation tests ---"
mkdir -p /logs/verifier/top_sim
cd /logs/verifier/top_sim

TOP_PASSED=0

# Build the verilated model first
make -f $RV_ROOT/tools/Makefile verilator-build \
    2>&1 | tee /logs/verifier/verilator_build.log | tail -10 || {
    echo "Verilator build failed — skipping top-level sim tests"
}

# Run individual named tests if model built successfully
if [ -x /logs/verifier/top_sim/obj_dir/Vtb_top ]; then
    for TEST in hello_world dhry insns irq pmp ecc csr_access csr_misa \
                csr_mstatus csr_mseccfg clk_override core_pause modesw \
                perf_counters write_unaligned; do
        echo "  Testing: ${TEST}"
        make -f $RV_ROOT/tools/Makefile TEST="${TEST}" verilator \
            2>&1 >> /logs/verifier/top_sim_tests.log && {
            echo "  PASS: ${TEST}"
            TOP_PASSED=$((TOP_PASSED + 1))
        } || {
            echo "  FAIL: ${TEST}"
        }
    done
fi

echo "Top-level Verilator sim tests passed: ${TOP_PASSED}"
PASSED=$((PASSED + TOP_PASSED))

# ---------------------------------------------------------------------------
# 3. Top-level pyuvm test
# ---------------------------------------------------------------------------
echo "--- Running top-level pyuvm test ---"
PYUVM_PASSED=0
cd /app/verification/top/test_pyuvm

if python3 -m pytest -sv test_pyuvm.py \
    --timeout=300 \
    2>&1 | tee /logs/verifier/pyuvm.log; then
    PYUVM_PASSED=1
    echo "pyuvm test PASSED"
else
    echo "pyuvm test FAILED"
fi

PASSED=$((PASSED + PYUVM_PASSED))

# ---------------------------------------------------------------------------
# Compute reward
# ---------------------------------------------------------------------------
cd /app

echo "=== Results: passed=${PASSED}, total=${TOTAL} ==="

if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "print(f'{min(${PASSED}, ${TOTAL}) / ${TOTAL}:.6f}')" \
        > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"

# Structured pytest report (non-fatal)
if command -v uvx >/dev/null 2>&1; then
    uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
        pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
