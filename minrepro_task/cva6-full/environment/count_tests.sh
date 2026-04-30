#!/bin/bash
# count_tests.sh — Build the CVA6 Verilator model on green source and run
# all RISC-V ISA tests + benchmarks to establish the passing test count.
#
# Called during the Docker "count" build stage (runs as builder user).
#
# Environment (set at image build time):
#   RISCV          = /opt/riscv
#   SPIKE_INSTALL_DIR = /opt/spike
#   VERILATOR_INSTALL_DIR = /opt/verilator
#
# Writes:
#   /tmp/_total       — total passing test count (integer)
#   /tmp/_all_tests   — newline-separated list of all passing test names
#   /tmp/_asm_tests   — passing asm test names (subset of _all_tests)
#   /tmp/_amo_tests   — passing amo test names
#   /tmp/_mul_tests   — passing mul test names
#   /tmp/_fp_tests    — passing fp test names
#   /tmp/_bench_tests — passing benchmark test names

set -eux

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
NUM_JOBS="${NUM_JOBS:-$(nproc)}"

# ── Step 1: Symlink riscv-tests into expected location ────────────────────────
mkdir -p "${CVA6_REPO_DIR}/tmp"
if [ ! -d "${RISCV_TEST_DIR}" ]; then
    ln -s /opt/riscv-tests "${CVA6_REPO_DIR}/tmp/riscv-tests"
fi

# ── Step 2: Initialise git workspace (needed by some Makefile targets) ────────
cd "${CVA6_REPO_DIR}"
git init -q
git add -A
git -c user.email=x@x -c user.name=x commit -q -m init

# ── Step 3: Run Bender to check dependencies ──────────────────────────────────
bender checkout

# ── Step 4: Build Verilator model ────────────────────────────────────────────
# Use the Makefile's verilate target which constructs the correct verilator
# command with all required source files, includes, and LDFLAGS.
NUM_JOBS="${NUM_JOBS}" make verilate \
    CVA6_REPO_DIR="${CVA6_REPO_DIR}" \
    TARGET_CFG="${TARGET_CFG}" \
    HPDCACHE_DIR="${HPDCACHE_DIR}" \
    RISCV="${RISCV}" \
    SPIKE_INSTALL_DIR="${SPIKE_INSTALL_DIR}" \
    VERILATOR_INSTALL_DIR="${VERILATOR_INSTALL_DIR}" \
    VL_INC_DIR="${VERILATOR_INSTALL_DIR}/share/verilator/include" \
    NUM_JOBS="${NUM_JOBS}"

# Confirm binary was built
VER_BIN="${VER_LIB}/Variane_testharness"
if [ ! -x "${VER_BIN}" ]; then
    echo "ERROR: Verilator binary not found at ${VER_BIN}" >&2
    exit 1
fi

# ── Step 5: Run all tests and record results ───────────────────────────────────
MAX_CYCLES=10000000
PASS=0
ALL_TESTS=()
ASM_TESTS=()
AMO_TESTS=()
MUL_TESTS=()
FP_TESTS=()
BENCH_TESTS=()

run_test() {
    local category="$1"
    local test_name="$2"
    local elf_path="$3"
    local log_file="/tmp/cva6_${category}_${test_name}.log"

    "${VER_BIN}" "+max-cycles=${MAX_CYCLES}" "${elf_path}" > "${log_file}" 2>&1 || true

    if grep -q "SUCCESS\|PASSED\|tohost = 1\b" "${log_file}" 2>/dev/null; then
        echo "PASS: ${category}::${test_name}"
        PASS=$((PASS + 1))
        ALL_TESTS+=("${category}::${test_name}")
        return 0
    else
        echo "FAIL: ${category}::${test_name}"
        return 1
    fi
}

echo "=== Running ASM tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    if [ -f "${elf}" ]; then
        if run_test "asm" "${tname}" "${elf}"; then
            ASM_TESTS+=("asm::${tname}")
        fi
    else
        echo "SKIP: asm::${tname} (elf not found at ${elf})"
    fi
done < "${CVA6_REPO_DIR}/ci/riscv-asm-tests.list"

echo "=== Running AMO tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    if [ -f "${elf}" ]; then
        if run_test "amo" "${tname}" "${elf}"; then
            AMO_TESTS+=("amo::${tname}")
        fi
    else
        echo "SKIP: amo::${tname} (elf not found at ${elf})"
    fi
done < "${CVA6_REPO_DIR}/ci/riscv-amo-tests.list"

echo "=== Running MUL tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    if [ -f "${elf}" ]; then
        if run_test "mul" "${tname}" "${elf}"; then
            MUL_TESTS+=("mul::${tname}")
        fi
    else
        echo "SKIP: mul::${tname} (elf not found at ${elf})"
    fi
done < "${CVA6_REPO_DIR}/ci/riscv-mul-tests.list"

echo "=== Running FP tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_TEST_DIR}/${tname}"
    if [ -f "${elf}" ]; then
        if run_test "fp" "${tname}" "${elf}"; then
            FP_TESTS+=("fp::${tname}")
        fi
    else
        echo "SKIP: fp::${tname} (elf not found at ${elf})"
    fi
done < "${CVA6_REPO_DIR}/ci/riscv-fp-tests.list"

echo "=== Running benchmark tests ==="
while IFS= read -r tname; do
    [ -z "${tname}" ] && continue
    elf="${RISCV_BENCH_DIR}/${tname}"
    if [ -f "${elf}" ]; then
        if run_test "bench" "${tname}" "${elf}"; then
            BENCH_TESTS+=("bench::${tname}")
        fi
    else
        echo "SKIP: bench::${tname} (elf not found at ${elf})"
    fi
done < "${CVA6_REPO_DIR}/ci/riscv-benchmarks.list"

# ── Step 6: Write results ─────────────────────────────────────────────────────
TOTAL=${#ALL_TESTS[@]}
echo ""
echo "=== Summary ==="
echo "Total passing tests on green source: ${TOTAL}"
echo "${TOTAL}" > /tmp/_total
printf '%s\n' "${ALL_TESTS[@]}"  > /tmp/_all_tests
printf '%s\n' "${ASM_TESTS[@]}"  > /tmp/_asm_tests
printf '%s\n' "${AMO_TESTS[@]}"  > /tmp/_amo_tests
printf '%s\n' "${MUL_TESTS[@]}"  > /tmp/_mul_tests
printf '%s\n' "${FP_TESTS[@]}"   > /tmp/_fp_tests
printf '%s\n' "${BENCH_TESTS[@]}" > /tmp/_bench_tests

echo "count_tests.sh complete. TOTAL=${TOTAL}"
