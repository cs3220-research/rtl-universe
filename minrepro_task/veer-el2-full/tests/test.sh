#!/bin/bash
# Verifier entrypoint for the veer-el2 all-tests Harbor task.
# Runs all block-level cocotb/pyuvm tests via nox and writes a proportional
# reward to /logs/verifier/reward.txt.
#
# Scoring: passed_nox_sessions / total_nox_sessions
# Total is the count captured from the green source during Docker build.

set -u
mkdir -p /logs/verifier
cd /app

# Read total from .harbor directory (written during Docker build).
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    # Fallback: count from the known noxfile (48 block test sessions).
    TOTAL=48
fi

# Copy the result parser to /tmp so it's available during test execution.
cp /tests/parse_block_results.py /tmp/parse_block_results.py
chmod +x /tmp/parse_block_results.py

# Run all block tests.
chmod +x /tests/run_block_tests.sh
/tests/run_block_tests.sh

# Read passing count.
if [ -r /logs/verifier/passed_count ]; then
    PASSED=$(cat /logs/verifier/passed_count)
else
    PASSED=0
fi

# Compute reward as float in [0, 1].
if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "import sys; p=int(sys.argv[1]); t=int(sys.argv[2]); print(f'{p/t:.6f}')" \
        "${PASSED}" "${TOTAL}" > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
