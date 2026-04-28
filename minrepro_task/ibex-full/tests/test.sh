#!/bin/bash
# Verifier for the ibex Harbor task (all-tests mode).
# Runs tb_cs_registers + 4 simple-system SW tests against the agent's /app.
# Writes a proportional reward in [0, 1] to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

VERILATOR_VERSION="${VERILATOR_VERSION:-v4.210}"
export PATH="/tools/riscv/bin:/tools/verilator/${VERILATOR_VERSION}/bin:${PATH:-}"

# ---------------------------------------------------------------------------
# Denominator: total tests captured at image build time.
# ---------------------------------------------------------------------------
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=5
fi

PASS=0

#######################################################################
# Test 1: CS Registers testbench
#######################################################################
echo "=== [1/5] CS Registers testbench ===" | tee /logs/verifier/all.log

# Re-register the library (agent may have modified files)
mkdir -p "${HOME}/.cache/fusesoc" 2>/dev/null || true
fusesoc library add ibex /app 2>/dev/null || true

fusesoc --cores-root=. run --target=sim --tool=verilator lowrisc:ibex:tb_cs_registers \
    2>&1 | tee /logs/verifier/tb_csr.log || true

if grep -q "TEST PASSED" /logs/verifier/tb_csr.log; then
    PASS=$((PASS + 1))
    echo "tb_cs_registers: PASSED" | tee -a /logs/verifier/all.log
else
    echo "tb_cs_registers: FAILED" | tee -a /logs/verifier/all.log
fi

#######################################################################
# Tests 2–5: Simple-system software tests
#######################################################################

# Build the simulator (fresh, from agent's source)
echo "=== Building simple-system simulator ===" | tee -a /logs/verifier/all.log
fusesoc --cores-root=. run --target=sim --setup --build lowrisc:ibex:ibex_simple_system \
    2>&1 | tee /logs/verifier/build_simple_system.log || true

SIM_BIN="build/lowrisc_ibex_ibex_simple_system_0/sim-verilator/Vibex_simple_system"

SW_TESTS="hello_test dit_test dummy_instr_test pmp_smoke_test"
IDX=2

for test in $SW_TESTS; do
    echo "=== [$IDX/5] SW test: $test ===" | tee -a /logs/verifier/all.log
    IDX=$((IDX + 1))

    # Compile the firmware test
    (cd "examples/sw/simple_system/$test" && make) \
        2>&1 | tee "/logs/verifier/sw_build_${test}.log" || true

    vmem="examples/sw/simple_system/$test/${test}.vmem"

    if [ ! -f "$vmem" ]; then
        echo "$test: SKIP (vmem not built — firmware compile failed)" \
            | tee -a /logs/verifier/all.log
        continue
    fi

    if [ ! -f "$SIM_BIN" ]; then
        echo "$test: SKIP (simulator binary missing — RTL compile failed)" \
            | tee -a /logs/verifier/all.log
        continue
    fi

    # Run the simulation
    if timeout 120 "$SIM_BIN" --raminit="$vmem" \
            2>&1 | tee "/logs/verifier/sw_run_${test}.log"; then
        PASS=$((PASS + 1))
        echo "$test: PASSED" | tee -a /logs/verifier/all.log
    else
        echo "$test: FAILED (exit code non-zero or timeout)" \
            | tee -a /logs/verifier/all.log
    fi
done

#######################################################################
# Compute and write reward
#######################################################################
echo "" | tee -a /logs/verifier/all.log
echo "Total: passed=$PASS / total=$TOTAL" | tee -a /logs/verifier/all.log

if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "
passed = int('${PASS}')
total  = int('${TOTAL}')
reward = min(1.0, passed / total)
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASS}, total=${TOTAL})"
