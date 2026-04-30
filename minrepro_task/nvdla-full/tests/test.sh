#!/bin/bash
# Verifier for the nvdla Harbor task (all-tests mode).
#
# Builds the Verilator simulation binary from the agent's RTL source,
# runs all 11 trace-player tests, and computes a proportional reward.
#
# Scoring: passed_tests / total_tests
# Each test passes when VNV_nvdla outputs "*** PASS" on stdout.

set -u
mkdir -p /logs/verifier
cd /app

export PATH="/tools/verilator/bin:${PATH:-}"

# ---------------------------------------------------------------------------
# Denominator: total tests captured at image build time.
# ---------------------------------------------------------------------------
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=11
fi

PASSED=0

# ---------------------------------------------------------------------------
# Step 1: Set up tree.make and outdir symlink so the build paths resolve.
# ---------------------------------------------------------------------------
if [ ! -f tree.make ]; then
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
fi

mkdir -p outdir/nv_full
ln -sfn /app/vmod outdir/nv_full/vmod

OUTDIR=/app/outdir/nv_full

# ---------------------------------------------------------------------------
# Step 2: Run Verilator elaboration on the agent's RTL.
# ---------------------------------------------------------------------------
echo "=== [1/2] Verilator elaboration ===" | tee /logs/verifier/build.log

mkdir -p "${OUTDIR}/verilator"

cd /app/verif/verilator
verilator --cc --exe \
    -f verilator.f \
    nvdla.cpp \
    --Mdir "${OUTDIR}/verilator" \
    --output-split 5000000 \
    --output-split-cfuncs 5000000 \
    2>&1 | tee -a /logs/verifier/build.log || true

# ---------------------------------------------------------------------------
# Step 3: Compile VNV_nvdla binary.
# ---------------------------------------------------------------------------
echo "=== [2/2] Compiling VNV_nvdla ===" | tee -a /logs/verifier/build.log

make -j"$(nproc)" -C "${OUTDIR}/verilator" -f VNV_nvdla.mk \
    CC=gcc CXX=g++ \
    VM_PARALLEL_BUILDS=1 \
    2>&1 | tee -a /logs/verifier/build.log || true

SIMBIN="${OUTDIR}/verilator/VNV_nvdla"

if [ ! -f "${SIMBIN}" ]; then
    echo "ERROR: VNV_nvdla binary not built — all tests fail" | tee -a /logs/verifier/build.log
    echo "0.000000" > /logs/verifier/reward.txt
    exit 0
fi

# ---------------------------------------------------------------------------
# Step 4: Run trace-player tests.
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

for test in "${TESTS[@]}"; do
    TRACE_DIR="/app/verif/traces/traceplayer/${test}"
    LOG="/logs/verifier/sim_${test}.log"

    if [ ! -d "${TRACE_DIR}" ]; then
        echo "SKIP: ${test} (trace directory missing)" | tee -a /logs/verifier/build.log
        continue
    fi

    TEST_OUTDIR="${OUTDIR}/verilator/test/${test}"
    mkdir -p "${TEST_OUTDIR}"

    # Convert input.txn to binary trace format
    perl /app/verif/verilator/input_txn_to_verilator.pl \
        "${TRACE_DIR}" "${TEST_OUTDIR}/trace.bin" \
        > "${LOG}" 2>&1 || true

    if [ ! -f "${TEST_OUTDIR}/trace.bin" ]; then
        echo "FAIL: ${test} (trace conversion failed)" | tee -a /logs/verifier/build.log
        echo "FAIL: ${test}" >> "${LOG}"
        continue
    fi

    # Run the simulation with a 10-minute timeout per test
    cd "${TEST_OUTDIR}"
    if timeout 600 "${SIMBIN}" trace.bin >> "${LOG}" 2>&1; then
        SIM_EXIT=0
    else
        SIM_EXIT=$?
    fi
    cd /app/verif/verilator

    if grep -q "^\*\*\* PASS" "${LOG}"; then
        echo "PASS: ${test}" | tee -a /logs/verifier/build.log
        PASSED=$((PASSED + 1))
    else
        echo "FAIL: ${test} (exit=${SIM_EXIT})" | tee -a /logs/verifier/build.log
    fi
done

# ---------------------------------------------------------------------------
# Compute and write reward.
# ---------------------------------------------------------------------------
echo "" | tee -a /logs/verifier/build.log
echo "Results: passed=${PASSED} / total=${TOTAL}" | tee -a /logs/verifier/build.log

python3 -c "
passed = int('${PASSED}')
total  = int('${TOTAL}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)"
