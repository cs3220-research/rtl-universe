#!/bin/bash
# count_tests.sh — Build NVDLA Verilator simulator and run all trace-player tests.
# Used during Docker image build (count stage).
#
# Outputs:
#   /tmp/_total       — integer: number of tests (denominator)
#   /tmp/_all_tests   — newline-separated list of test names
#   /tmp/_e2e_targets — same list (all nvdla tests are end-to-end trace simulations)
#   /tmp/_passed      — integer: tests that passed on green source

set -eux

cd /app

# ---------------------------------------------------------------------------
# Step 1: Create tree.make so the build system finds tools.
# The Makefile reads this file to discover PROJECTS, VERILATOR, CLANG, etc.
# ---------------------------------------------------------------------------
cat > tree.make << 'TREEMAKE'
PROJECTS  := nv_full
OUTDIR    := outdir
CPP       := cpp
GCC       := g++
PERL      := perl
VERILATOR := verilator
CLANG     := clang
CLANG++   := clang++
TREEMAKE

# ---------------------------------------------------------------------------
# Step 2: Create a symlink outdir/nv_full/vmod pointing to vmod/ so that
# verilator.f's relative paths (../../outdir/nv_full/vmod/...) work without
# running the full tmake build system that generates the outdir.
# ---------------------------------------------------------------------------
mkdir -p outdir/nv_full
ln -sfn /app/vmod outdir/nv_full/vmod

OUTDIR=/app/outdir/nv_full

# ---------------------------------------------------------------------------
# Step 3: Run Verilator elaboration to generate the VNV_nvdla simulator.
# verilator.f references the RTL from $OUTDIR/vmod which we symlinked above.
# ---------------------------------------------------------------------------
mkdir -p "${OUTDIR}/verilator"

echo "=== Running Verilator elaboration ==="
cd /app/verif/verilator

verilator --cc --exe \
    -f verilator.f \
    nvdla.cpp \
    --Mdir "${OUTDIR}/verilator" \
    --output-split 5000000 \
    --output-split-cfuncs 5000000 \
    2>&1 | tee /tmp/verilator_elab.log || true

# ---------------------------------------------------------------------------
# Step 4: Compile the generated C++ into VNV_nvdla binary.
# ---------------------------------------------------------------------------
echo "=== Compiling Verilated binary ==="
make -j"$(nproc)" -C "${OUTDIR}/verilator" -f VNV_nvdla.mk \
    CC=gcc CXX=g++ \
    VM_PARALLEL_BUILDS=1 \
    2>&1 | tee /tmp/verilator_compile.log || true

SIMBIN="${OUTDIR}/verilator/VNV_nvdla"

# ---------------------------------------------------------------------------
# Step 5: Convert each trace (input.txn) to binary (trace.bin) and run it.
# ---------------------------------------------------------------------------
TESTS=(
    sanity0
    sanity1
    sanity1_cvsram
    sanity2
    sanity2_cvsram
    sanity3
    sanity3_cvsram
    conv_8x8_fc_int16
    googlenet_conv2_3x3_int16
    pdp_max_pooling_int16
    sdp_relu_int16
    cc_alexnet_conv5_relu5_int16_dtest_cvsram
)

TOTAL=0
PASSED=0

for test in "${TESTS[@]}"; do
    TRACE_DIR="/app/verif/traces/traceplayer/${test}"
    if [ ! -d "${TRACE_DIR}" ]; then
        echo "SKIP: trace directory not found: ${TRACE_DIR}"
        continue
    fi

    TOTAL=$((TOTAL + 1))
    TEST_OUTDIR="${OUTDIR}/verilator/test/${test}"
    mkdir -p "${TEST_OUTDIR}"

    echo "=== Converting trace: ${test} ==="
    perl /app/verif/verilator/input_txn_to_verilator.pl "${TRACE_DIR}" "${TEST_OUTDIR}/trace.bin" \
        2>&1 | tee "/tmp/trace_conv_${test}.log" || true

    if [ ! -f "${TEST_OUTDIR}/trace.bin" ]; then
        echo "FAIL: trace conversion failed for ${test}"
        continue
    fi

    if [ ! -f "${SIMBIN}" ]; then
        echo "FAIL: simulator binary not found, cannot run ${test}"
        continue
    fi

    echo "=== Running simulation: ${test} ==="
    cd "${TEST_OUTDIR}"
    if timeout 600 "${SIMBIN}" trace.bin \
            > "/tmp/sim_${test}.log" 2>&1; then
        SIM_EXIT=0
    else
        SIM_EXIT=$?
    fi

    if grep -q "^\*\*\* PASS" "/tmp/sim_${test}.log"; then
        echo "PASS: ${test}"
        PASSED=$((PASSED + 1))
    else
        echo "FAIL: ${test} (exit=${SIM_EXIT})"
    fi
    cd /app/verif/verilator
done

# ---------------------------------------------------------------------------
# Step 6: Write outputs.
# ---------------------------------------------------------------------------
echo "${TOTAL}"  > /tmp/_total
echo "${PASSED}" > /tmp/_passed

printf '%s\n' "${TESTS[@]}" > /tmp/_all_tests

# All tests are E2E (full-chip Verilator trace simulations)
cp /tmp/_all_tests /tmp/_e2e_targets

echo "count_tests.sh complete: total=${TOTAL} passed=${PASSED}"
