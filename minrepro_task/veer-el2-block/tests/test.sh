#!/bin/bash
# Verifier entrypoint for the veer-el2 E2E Harbor task.
# Runs the top-level PyUVM IRQ test and writes a proportional reward to
# /logs/verifier/reward.txt.
#
# Scoring: 1.0 if the IRQ test passes, 0.0 otherwise.
# Total is always 1 (one E2E test scored in this variant).

set -u
mkdir -p /logs/verifier
cd /app

# Read total from .harbor directory (written during Docker build).
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=1
fi

# Run the E2E test.
chmod +x /tests/run_e2e_test.sh
/tests/run_e2e_test.sh

# Read pass result.
if [ -r /logs/verifier/e2e_passed ]; then
    PASSED=$(cat /logs/verifier/e2e_passed)
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
