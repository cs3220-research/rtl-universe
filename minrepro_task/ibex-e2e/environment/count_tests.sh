#!/bin/bash
# count_tests.sh — Run the 4 ibex-e2e software tests from the green source.
# Used during Docker image build (stage 2: count).
# Outputs:
#   /tmp/_total      — integer: total test count (denominator = 4)
#   /tmp/_all_tests  — newline-separated list of test names

set -eux

VERILATOR_VERSION="${VERILATOR_VERSION:-v4.210}"
export PATH="/tools/riscv/bin:/tools/verilator/${VERILATOR_VERSION}/bin:${PATH}"

cd /app

# Register the ibex core library with FuseSoC
mkdir -p "${HOME}/.cache/fusesoc"
fusesoc library add ibex /app

SW_TESTS="hello_test dit_test dummy_instr_test pmp_smoke_test"

#######################################################################
# Build the Verilator simple-system simulator
#######################################################################
echo "=== Building simple-system simulator ==="
fusesoc --cores-root=. run --target=sim --setup --build lowrisc:ibex:ibex_simple_system \
    2>&1 | tee /tmp/build_simple_system.log || true

SIM_BIN="build/lowrisc_ibex_ibex_simple_system_0/sim-verilator/Vibex_simple_system"

#######################################################################
# Compile and run each SW test
#######################################################################
TOTAL=0
PASS=0

declare -A SW_PASS

for test in $SW_TESTS; do
    TOTAL=$((TOTAL + 1))
    SW_PASS[$test]=0

    echo "=== Compiling SW test: $test ==="
    (cd "examples/sw/simple_system/$test" && make) \
        2>&1 | tee "/tmp/sw_build_${test}.log" || true

    vmem="examples/sw/simple_system/$test/${test}.vmem"
    if [ ! -f "$vmem" ]; then
        echo "$test: SKIP (vmem not built)"
        continue
    fi

    echo "=== Running SW test: $test ==="
    if [ -f "$SIM_BIN" ]; then
        if timeout 120 "$SIM_BIN" --raminit="$vmem" \
                2>&1 | tee "/tmp/sw_run_${test}.log"; then
            SW_PASS[$test]=1
            PASS=$((PASS + 1))
            echo "$test: PASSED"
        else
            echo "$test: FAILED or timed out"
        fi
    else
        echo "$test: SKIP (simulator not built)"
    fi
done

echo "Count stage complete: total=$TOTAL pass=$PASS"

echo "$TOTAL" > /tmp/_total
echo "$PASS"  > /tmp/_passed

{
    for test in $SW_TESTS; do
        echo "$test"
    done
} > /tmp/_all_tests

echo "Outputs written to /tmp/_total, /tmp/_all_tests"
