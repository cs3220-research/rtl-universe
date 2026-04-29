#!/bin/bash
# test.sh — Verifier for the cva6 Harbor task.
#
# Builds the CVA6 Verilator simulation from the agent's /app workspace,
# then runs the full RISC-V ISA test suite and benchmarks.
# Computes reward = passed_tests / total_tests.
#
# Writes reward (float in [0,1]) to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

export RISCV="${RISCV:-/opt/riscv}"
export SPIKE_INSTALL_DIR="${SPIKE_INSTALL_DIR:-/opt/spike}"
export VERILATOR_INSTALL_DIR="${VERILATOR_INSTALL_DIR:-/opt/verilator}"
export CVA6_REPO_DIR="/app"
export TARGET_CFG="${TARGET_CFG:-cv64a6_imafdc_sv39}"
export HPDCACHE_DIR="${CVA6_REPO_DIR}/core/cache_subsystem/hpdcache"
export PATH="${RISCV}/bin:${VERILATOR_INSTALL_DIR}/bin:${PATH}"
export LD_LIBRARY_PATH="${RISCV}/lib:${SPIKE_INSTALL_DIR}/lib:${LD_LIBRARY_PATH:-}"

RISCV_TEST_DIR="${CVA6_REPO_DIR}/tmp/riscv-tests/build/isa"
RISCV_BENCH_DIR="${CVA6_REPO_DIR}/tmp/riscv-tests/build/benchmarks"
VER_LIB="${CVA6_REPO_DIR}/work-ver"
VER_BIN="${VER_LIB}/Variane_testharness"
NUM_JOBS="${NUM_JOBS:-$(nproc)}"
MAX_CYCLES=10000000

# ── Denominator ────────────────────────────────────────────────────────────────
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=228
fi

# ── Ensure riscv-tests symlink ────────────────────────────────────────────────
mkdir -p "${CVA6_REPO_DIR}/tmp"
if [ ! -d "${RISCV_TEST_DIR}" ]; then
    ln -sf /opt/riscv-tests "${CVA6_REPO_DIR}/tmp/riscv-tests"
fi

{
echo "=== CVA6 Verifier ==="
echo "TOTAL tests expected: ${TOTAL}"
echo ""

# ── Build Verilator model ──────────────────────────────────────────────────────
echo "=== Building Verilator model ==="
make verilate \
    CVA6_REPO_DIR="${CVA6_REPO_DIR}" \
    TARGET_CFG="${TARGET_CFG}" \
    HPDCACHE_DIR="${HPDCACHE_DIR}" \
    RISCV="${RISCV}" \
    SPIKE_INSTALL_DIR="${SPIKE_INSTALL_DIR}" \
    VERILATOR_INSTALL_DIR="${VERILATOR_INSTALL_DIR}" \
    VL_INC_DIR="${VERILATOR_INSTALL_DIR}/share/verilator/include" \
    NUM_JOBS="${NUM_JOBS}" \
    2>&1 || echo "WARNING: verilate target failed"

PASSED=0

if [ ! -x "${VER_BIN}" ]; then
    echo "ERROR: Variane_testharness binary not found — reward = 0"
    echo "0.000000" > /logs/verifier/reward.txt
    exit 0
fi

echo "Binary: ${VER_BIN}"
echo ""

# ── Helper: run one test ───────────────────────────────────────────────────────
run_test() {
    local category="$1"
    local test_name="$2"
    local elf_path="$3"
    local log_file="/logs/verifier/${category}_${test_name}.log"

    "${VER_BIN}" "+max-cycles=${MAX_CYCLES}" "${elf_path}" > "${log_file}" 2>&1 || true

    if grep -q "SUCCESS\|PASSED\|tohost = 1\b" "${log_file}" 2>/dev/null; then
        echo "PASS: ${category}::${test_name}"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo "FAIL: ${category}::${test_name}"
        return 1
    fi
}

# ── ASM tests ─────────────────────────────────────────────────────────────────
echo "=== ASM tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    [ -f "${elf}" ] && run_test "asm" "${tname}" "${elf}" || echo "SKIP: asm::${tname}"
done < "${CVA6_REPO_DIR}/ci/riscv-asm-tests.list"

# ── AMO tests ─────────────────────────────────────────────────────────────────
echo "=== AMO tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    [ -f "${elf}" ] && run_test "amo" "${tname}" "${elf}" || echo "SKIP: amo::${tname}"
done < "${CVA6_REPO_DIR}/ci/riscv-amo-tests.list"

# ── MUL tests ─────────────────────────────────────────────────────────────────
echo "=== MUL tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    [ -f "${elf}" ] && run_test "mul" "${tname}" "${elf}" || echo "SKIP: mul::${tname}"
done < "${CVA6_REPO_DIR}/ci/riscv-mul-tests.list"

# ── FP tests ──────────────────────────────────────────────────────────────────
echo "=== FP tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    [ -f "${elf}" ] && run_test "fp" "${tname}" "${elf}" || echo "SKIP: fp::${tname}"
done < "${CVA6_REPO_DIR}/ci/riscv-fp-tests.list"

# ── Benchmark tests ───────────────────────────────────────────────────────────
echo "=== Benchmark tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_BENCH_DIR}/${tname}"
    [ -f "${elf}" ] && run_test "bench" "${tname}" "${elf}" || echo "SKIP: bench::${tname}"
done < "${CVA6_REPO_DIR}/ci/riscv-benchmarks.list"

echo ""
echo "=== Summary ==="
echo "passed=${PASSED} total=${TOTAL}"

} 2>&1 | tee /logs/verifier/test.log

# ── Compute reward ─────────────────────────────────────────────────────────────
python3 -c "
passed = int('${PASSED:-0}')
total  = int('${TOTAL:-228}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
