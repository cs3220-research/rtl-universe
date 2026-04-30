#!/bin/bash
# count_tests.sh for nvdla-e2e — same build as nvdla-full but only scores the
# 6 E2E (non-sanity network inference) traces:
#   conv_8x8_fc_int16, googlenet_conv2_3x3_int16, pdp_max_pooling_int16,
#   sdp_relu_int16, sanity3, sanity3_cvsram
#
# These tests exercise the complete NVDLA inference pipeline end-to-end with
# real (though small) neural network inputs and check output feature maps
# against golden reference data embedded in the trace.
#
# Outputs:
#   /tmp/_total       — number of E2E tests
#   /tmp/_all_tests   — all 11 test names (for the full task)
#   /tmp/_e2e_targets — E2E test subset only
#   /tmp/_passed      — E2E tests that passed

set -eux

cd /app

# Create tree.make
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

# Symlink so verilator.f paths resolve
mkdir -p outdir/nv_full
ln -sfn /app/vmod outdir/nv_full/vmod

OUTDIR=/app/outdir/nv_full

# ---------------------------------------------------------------------------
# Build Verilator simulator
# ---------------------------------------------------------------------------
mkdir -p "${OUTDIR}/verilator"
cd /app/verif/verilator

echo "=== Verilator elaboration ==="
verilator --cc --exe \
    -f verilator.f \
    nvdla.cpp \
    --Mdir "${OUTDIR}/verilator" \
    --output-split 5000000 \
    --output-split-cfuncs 5000000 \
    2>&1 | tee /tmp/verilator_elab.log || true

echo "=== Compiling VNV_nvdla ==="
make -j"$(nproc)" -C "${OUTDIR}/verilator" -f VNV_nvdla.mk \
    CC=gcc CXX=g++ VM_PARALLEL_BUILDS=1 \
    2>&1 | tee /tmp/verilator_compile.log || true

SIMBIN="${OUTDIR}/verilator/VNV_nvdla"

# ---------------------------------------------------------------------------
# E2E test subset: real network inference tests that check output data.
# These require a working full-chip implementation (not just register access).
# ---------------------------------------------------------------------------
E2E_TESTS=(
    sanity3
    sanity3_cvsram
    conv_8x8_fc_int16
    googlenet_conv2_3x3_int16
    pdp_max_pooling_int16
    sdp_relu_int16
    cc_alexnet_conv5_relu5_int16_dtest_cvsram
)

# All 12 tests (for reference)
ALL_TESTS=(
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

for test in "${E2E_TESTS[@]}"; do
    TRACE_DIR="/app/verif/traces/traceplayer/${test}"
    if [ ! -d "${TRACE_DIR}" ]; then
        echo "SKIP: ${test} (trace directory missing)"
        continue
    fi

    TOTAL=$((TOTAL + 1))
    TEST_OUTDIR="${OUTDIR}/verilator/test/${test}"
    mkdir -p "${TEST_OUTDIR}"

    echo "=== Converting trace: ${test} ==="
    perl /app/verif/verilator/input_txn_to_verilator.pl \
        "${TRACE_DIR}" "${TEST_OUTDIR}/trace.bin" \
        2>&1 | tee "/tmp/trace_conv_${test}.log" || true

    if [ ! -f "${TEST_OUTDIR}/trace.bin" ]; then
        echo "FAIL: trace conversion failed for ${test}"
        continue
    fi

    if [ ! -f "${SIMBIN}" ]; then
        echo "FAIL: simulator binary not built"
        continue
    fi

    echo "=== Running simulation: ${test} ==="
    cd "${TEST_OUTDIR}"
    if timeout 600 "${SIMBIN}" trace.bin > "/tmp/sim_${test}.log" 2>&1; then
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

echo "${TOTAL}"  > /tmp/_total
echo "${PASSED}" > /tmp/_passed
printf '%s\n' "${ALL_TESTS[@]}" > /tmp/_all_tests
printf '%s\n' "${E2E_TESTS[@]}" > /tmp/_e2e_targets

echo "count_tests.sh (e2e) complete: e2e_total=${TOTAL} e2e_passed=${PASSED}"
