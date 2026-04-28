#!/bin/bash
# Verifier entrypoint for the cva6-smoke Harbor task.
#
# Runs a curated set of 10 RISC-V integer ASM tests against the Verilator
# model to give faster agent feedback than the full 228-test suite.
#
# Scoring denominator: 10 (stored in /app/.harbor/total_tests).

set -u
mkdir -p /logs/verifier
cd /app

# ---------------------------------------------------------------------------
# Environment
# ---------------------------------------------------------------------------
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
export PATH="${VERILATOR_INSTALL_DIR}/bin:${RISCV}/bin:${PATH}"
export NUM_JOBS=8

# Denominator
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=10
fi

echo "=== CVA6 smoke verifier: total_tests=${TOTAL} ==="

PASSED=0

# ---------------------------------------------------------------------------
# 1. Build Verilator model
# ---------------------------------------------------------------------------
echo "--- Building Verilator model ---"
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=${NUM_JOBS} \
    2>&1 | tee /logs/verifier/verilate_build.log | tail -20 || {
    echo "ERROR: verilate build failed"
    echo "0.000000" > /logs/verifier/reward.txt
    exit 0
}

if [ ! -x /app/work-ver/Variane_testharness ]; then
    echo "ERROR: Variane_testharness not found after build"
    echo "0.000000" > /logs/verifier/reward.txt
    exit 0
fi

HARNESS=/app/work-ver/Variane_testharness
RISCV_TEST_DIR=/app/tmp/riscv-tests/build/isa

# ---------------------------------------------------------------------------
# 2. Smoke test suite — 10 representative integer tests
# ---------------------------------------------------------------------------
SMOKE_TESTS=(
    rv64ui-p-add
    rv64ui-p-addi
    rv64ui-p-and
    rv64ui-p-or
    rv64ui-p-sub
    rv64ui-p-jal
    rv64ui-p-jalr
    rv64ui-p-beq
    rv64ui-p-lw
    rv64ui-p-sw
)

echo "--- Running smoke tests ---"
for testname in "${SMOKE_TESTS[@]}"; do
    elf="${RISCV_TEST_DIR}/${testname}"
    if [ ! -f "${elf}" ]; then
        echo "MISSING: ${testname}"
        continue
    fi
    if "${HARNESS}" "${elf}" \
        > /logs/verifier/test_${testname}.log 2>&1; then
        echo "PASS: ${testname}"
        PASSED=$((PASSED + 1))
    else
        echo "FAIL: ${testname}"
    fi
done

# ---------------------------------------------------------------------------
# Compute reward
# ---------------------------------------------------------------------------
echo "=== Results: passed=${PASSED}, total=${TOTAL} ==="

if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "print(f'{min(${PASSED}, ${TOTAL}) / ${TOTAL}:.6f}')" \
        > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
