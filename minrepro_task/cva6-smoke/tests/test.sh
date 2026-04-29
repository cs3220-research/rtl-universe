#!/bin/bash
# test.sh — Verifier for the cva6-e2e Harbor task (E2E subset only).
#
# Runs the E2E subset: bare-metal ISA tests (rv64ui-p-*, rv64mi-p-*, rv64si-p-*,
# rv64uc-p-*) and bare-metal AMO tests (rv64ua-p-*). These exercise the full
# CVA6 pipeline in physical/supervisor mode without paging — ~79 tests total.
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
VER_LIB="${CVA6_REPO_DIR}/work-ver"
VER_BIN="${VER_LIB}/Variane_testharness"
NUM_JOBS="${NUM_JOBS:-$(nproc)}"
MAX_CYCLES=10000000

# ── Denominator: use E2E-specific count ───────────────────────────────────────
if [ -r /app/.harbor/e2e_total_tests ]; then
    TOTAL=$(cat /app/.harbor/e2e_total_tests)
else
    TOTAL=79
fi

mkdir -p "${CVA6_REPO_DIR}/tmp"
if [ ! -d "${RISCV_TEST_DIR}" ]; then
    ln -sf /opt/riscv-tests "${CVA6_REPO_DIR}/tmp/riscv-tests"
fi

{
echo "=== CVA6-E2E Verifier ==="
echo "E2E TOTAL tests expected: ${TOTAL}"
echo ""

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

# ── E2E subset from the asm list: all bare-metal (p-) tests ──────────────────
echo "=== E2E ASM tests (bare-metal: rv64ui-p-*, rv64mi-p-*, rv64si-p-*, rv64uc-p-*) ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    case "${tname}" in
        rv64ui-p-*|rv64mi-p-*|rv64si-p-*|rv64uc-p-*)
            elf="${RISCV_TEST_DIR}/${tname}"
            if [ -f "${elf}" ]; then
                run_test "asm" "${tname}" "${elf}"
            else
                echo "SKIP: asm::${tname} (elf not found)"
            fi
            ;;
    esac
done < "${CVA6_REPO_DIR}/ci/riscv-asm-tests.list"

# ── E2E subset from the AMO list: bare-metal (p-) tests only ─────────────────
echo "=== E2E AMO tests (bare-metal: rv64ua-p-*) ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    case "${tname}" in
        rv64ua-p-*)
            elf="${RISCV_TEST_DIR}/${tname}"
            if [ -f "${elf}" ]; then
                run_test "amo" "${tname}" "${elf}"
            else
                echo "SKIP: amo::${tname} (elf not found)"
            fi
            ;;
    esac
done < "${CVA6_REPO_DIR}/ci/riscv-amo-tests.list"

echo ""
echo "=== Summary ==="
echo "passed=${PASSED} total=${TOTAL}"

} 2>&1 | tee /logs/verifier/test.log

python3 -c "
passed = int('${PASSED:-0}')
total  = int('${TOTAL:-79}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
