#!/bin/bash
# Verifier entrypoint for the cva6 all-tests Harbor task.
#
# Builds the Verilator model of CVA6 (if not already built), then runs the
# full CI regression test suite: ASM + AMO + MUL + FP + benchmarks.
#
# Each test passes if Variane_testharness exits with code 0 (the RISC-V test
# binary wrote 1 to tohost before hitting the max-cycle limit).
#
# Scoring denominator: stored in /app/.harbor/total_tests at image build time.

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
export TARGET_CFG=cv64a6_imafdc_sv39

# Denominator
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=228
fi

echo "=== CVA6 verifier: total_tests=${TOTAL} ==="

PASSED=0

# ---------------------------------------------------------------------------
# 1. Build Verilator model
# ---------------------------------------------------------------------------
echo "--- Building Verilator model (target=cv64a6_imafdc_sv39) ---"
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=${NUM_JOBS} \
    2>&1 | tee /logs/verifier/verilate_build.log | tail -20 || {
    echo "ERROR: verilate build failed — cannot run tests"
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
RISCV_BENCH_DIR=/app/tmp/riscv-tests/build/benchmarks

# ---------------------------------------------------------------------------
# Helper: run one test binary, echo PASS/FAIL
# ---------------------------------------------------------------------------
run_test() {
    local name="$1"
    local elf="$2"
    if [ ! -f "$elf" ]; then
        echo "MISSING: ${name} (${elf})"
        return 1
    fi
    if "${HARNESS}" "${elf}" \
        2>/dev/null \
        > /logs/verifier/test_${name}.log 2>&1; then
        echo "PASS: ${name}"
        return 0
    else
        echo "FAIL: ${name}"
        return 1
    fi
}

# ---------------------------------------------------------------------------
# 2. ASM tests (rv64ui-p-*, rv64ua-p-*, etc.) — 110 tests
# ---------------------------------------------------------------------------
echo "--- Running ASM tests ---"
while IFS= read -r testname || [ -n "$testname" ]; do
    [ -z "$testname" ] && continue
    [[ "$testname" == \#* ]] && continue
    elf="${RISCV_TEST_DIR}/${testname}"
    run_test "${testname}" "${elf}" && PASSED=$((PASSED + 1)) || true
done < /app/ci/riscv-asm-tests.list
echo "ASM tests done. Running total: ${PASSED}/${TOTAL}"

# ---------------------------------------------------------------------------
# 3. AMO tests (rv64ua-p-*) — 38 tests
# ---------------------------------------------------------------------------
echo "--- Running AMO tests ---"
while IFS= read -r testname || [ -n "$testname" ]; do
    [ -z "$testname" ] && continue
    [[ "$testname" == \#* ]] && continue
    elf="${RISCV_TEST_DIR}/${testname}"
    run_test "${testname}" "${elf}" && PASSED=$((PASSED + 1)) || true
done < /app/ci/riscv-amo-tests.list
echo "AMO tests done. Running total: ${PASSED}/${TOTAL}"

# ---------------------------------------------------------------------------
# 4. MUL tests (rv64um-p-*) — 26 tests
# ---------------------------------------------------------------------------
echo "--- Running MUL tests ---"
while IFS= read -r testname || [ -n "$testname" ]; do
    [ -z "$testname" ] && continue
    [[ "$testname" == \#* ]] && continue
    elf="${RISCV_TEST_DIR}/${testname}"
    run_test "${testname}" "${elf}" && PASSED=$((PASSED + 1)) || true
done < /app/ci/riscv-mul-tests.list
echo "MUL tests done. Running total: ${PASSED}/${TOTAL}"

# ---------------------------------------------------------------------------
# 5. FP tests (rv64uf-*, rv64ud-*) — 46 tests
# ---------------------------------------------------------------------------
echo "--- Running FP tests ---"
while IFS= read -r testname || [ -n "$testname" ]; do
    [ -z "$testname" ] && continue
    [[ "$testname" == \#* ]] && continue
    elf="${RISCV_TEST_DIR}/${testname}"
    run_test "${testname}" "${elf}" && PASSED=$((PASSED + 1)) || true
done < /app/ci/riscv-fp-tests.list
echo "FP tests done. Running total: ${PASSED}/${TOTAL}"

# ---------------------------------------------------------------------------
# 6. Benchmarks (dhrystone, coremark, etc.) — 8 tests
# ---------------------------------------------------------------------------
echo "--- Running benchmark tests ---"
while IFS= read -r testname || [ -n "$testname" ]; do
    [ -z "$testname" ] && continue
    [[ "$testname" == \#* ]] && continue
    elf="${RISCV_BENCH_DIR}/${testname}"
    run_test "${testname}" "${elf}" && PASSED=$((PASSED + 1)) || true
done < /app/ci/riscv-benchmarks.list
echo "Benchmark tests done. Running total: ${PASSED}/${TOTAL}"

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
