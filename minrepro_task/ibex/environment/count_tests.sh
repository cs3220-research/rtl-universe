#!/bin/bash
# count_tests.sh — Run all Ibex tests from the green source and record counts.
# Used during Docker image build (stage 2: count).
# Outputs:
#   /tmp/_total       — integer: total test count (denominator)
#   /tmp/_all_tests   — newline-separated list of test names
#   /tmp/_e2e_targets — newline-separated list of e2e (SW sim) test names

set -eux

VERILATOR_VERSION="${VERILATOR_VERSION:-v4.210}"
export PATH="/tools/riscv/bin:/tools/verilator/${VERILATOR_VERSION}/bin:${PATH}"

cd /app

# Register the ibex core library with FuseSoC
mkdir -p "${HOME}/.cache/fusesoc"
fusesoc library add ibex /app

#######################################################################
# Test 1: CS Registers testbench
# Tests ibex_cs_registers.sv with a self-checking Verilator simulation.
# Reports "// TEST PASSED //" or "// TEST FAILED //" on stdout.
#######################################################################
echo "=== Building and running tb_cs_registers ==="
CSR_PASS=0
fusesoc --cores-root=. run --target=sim --tool=verilator lowrisc:ibex:tb_cs_registers \
    2>&1 | tee /tmp/tb_csr.log || true

if grep -q "TEST PASSED" /tmp/tb_csr.log; then
    CSR_PASS=1
    echo "tb_cs_registers: PASSED"
else
    echo "tb_cs_registers: FAILED"
fi

#######################################################################
# Tests 2–5: Simple-system software tests
# Build the Verilator simulator, compile each SW test, run each test.
# Each test exits with 0 on success, non-zero on failure.
#######################################################################

# Build the simple-system Verilator simulator
echo "=== Building simple-system simulator ==="
fusesoc --cores-root=. run --target=sim --setup --build lowrisc:ibex:ibex_simple_system \
    2>&1 | tee /tmp/build_simple_system.log || true

SIM_BIN="build/lowrisc_ibex_ibex_simple_system_0/sim-verilator/Vibex_simple_system"

SW_TESTS="hello_test dit_test dummy_instr_test pmp_smoke_test"
declare -A SW_PASS

for test in $SW_TESTS; do
    echo "=== Compiling SW test: $test ==="
    SW_PASS[$test]=0

    (cd "examples/sw/simple_system/$test" && make) \
        2>&1 | tee "/tmp/sw_build_${test}.log" || true

    vmem="examples/sw/simple_system/$test/${test}.vmem"
    if [ ! -f "$vmem" ]; then
        echo "$test: SKIP (vmem not built)"
        continue
    fi

    echo "=== Running SW test: $test ==="
    if [ -f "$SIM_BIN" ]; then
        timeout 120 "$SIM_BIN" --raminit="$vmem" \
            2>&1 | tee "/tmp/sw_run_${test}.log" && SW_PASS[$test]=1 || true
        if [ "${SW_PASS[$test]}" -eq 1 ]; then
            echo "$test: PASSED"
        else
            echo "$test: FAILED or timed out"
        fi
    else
        echo "$test: SKIP (simulator not built)"
    fi
done

#######################################################################
# Compute totals and write outputs
#######################################################################
TOTAL=0
PASS=0

# CSR test
TOTAL=$((TOTAL + 1))
PASS=$((PASS + CSR_PASS))

# SW tests
for test in $SW_TESTS; do
    TOTAL=$((TOTAL + 1))
    PASS=$((PASS + ${SW_PASS[$test]:-0}))
done

echo "Count stage complete: total=$TOTAL pass=$PASS"

echo "$TOTAL" > /tmp/_total
echo "$PASS"  > /tmp/_passed

# Write all test names (one per line)
{
    echo "tb_cs_registers"
    for test in $SW_TESTS; do
        echo "$test"
    done
} > /tmp/_all_tests

# E2E targets are the 4 SW simulation tests (not the unit-level CSR testbench)
{
    for test in $SW_TESTS; do
        echo "$test"
    done
} > /tmp/_e2e_targets

echo "Outputs written to /tmp/_total, /tmp/_all_tests, /tmp/_e2e_targets"
