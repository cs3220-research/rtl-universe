#!/bin/bash
# Verifier entrypoint for the secworks-aes Harbor task.
# Runs all five FuseSoC/iverilog testbench targets against the agent's /app
# workspace and computes a proportional reward based on passing test cases.
# Writes the reward (float in [0, 1]) to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# ---------------------------------------------------------------------------
# Denominator: total individual test cases across all testbenches.
# Each testbench prints:
#   "*** All NN test cases completed successfully"   (all pass)
# or
#   "*** NN tests completed - MM test cases did not complete successfully."
# Fall back to known baseline (65) if the .harbor file is missing.
# ---------------------------------------------------------------------------
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=65
fi

# ---------------------------------------------------------------------------
# Run each testbench and tally test cases.
# ---------------------------------------------------------------------------
TOTAL_PASS=0

TARGETS="tb_aes tb_aes_core tb_aes_key_mem tb_aes_encipher_block tb_aes_decipher_block"

for target in $TARGETS; do
    logfile="/logs/verifier/${target}.log"
    echo "=== Running FuseSoC target: $target ===" | tee -a /logs/verifier/all.log

    fusesoc run --target="$target" secworks:crypto:aes \
        2>&1 | tee "$logfile" | tail -5 || true

    # Parse "*** All NN test cases completed successfully"
    all_passed=$(grep -oE "All [0-9]+ test cases completed" "$logfile" \
        | grep -oE "[0-9]+" | head -1 || true)

    # Parse "*** NN tests completed - MM test cases did not complete successfully."
    total_tc=$(grep -oE "\*\*\* [0-9]+ tests completed" "$logfile" \
        | grep -oE "[0-9]+" | head -1 || true)
    failed_tc=$(grep -oE "[0-9]+ test cases did not complete successfully" "$logfile" \
        | grep -oE "^[0-9]+" | head -1 || true)

    if [ -n "$all_passed" ]; then
        tc="$all_passed"
        passed="$all_passed"
    elif [ -n "$total_tc" ] && [ -n "$failed_tc" ]; then
        tc="$total_tc"
        passed=$((total_tc - failed_tc))
    else
        # Simulation compile/link failure — 0 passed
        tc=0
        passed=0
    fi

    echo "  target=$target  tc=$tc  passed=$passed" | tee -a /logs/verifier/all.log
    TOTAL_PASS=$((TOTAL_PASS + passed))
done

echo "" | tee -a /logs/verifier/all.log
echo "Total: passed=${TOTAL_PASS} / denominator=${TOTAL}" | tee -a /logs/verifier/all.log

# ---------------------------------------------------------------------------
# Compute reward as passed / total (capped at 1.0).
# ---------------------------------------------------------------------------
if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "
passed = int('${TOTAL_PASS}')
total  = int('${TOTAL}')
reward = min(1.0, passed / total)
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${TOTAL_PASS}, total=${TOTAL})"
