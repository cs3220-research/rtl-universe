#!/bin/bash
# Run the VeeR EL2 E2E PyUVM IRQ test and write results.
#
# Called from test.sh. Runs verification/top/test_pyuvm/test_pyuvm.py via
# pytest which in turn invokes `make all` in the test_pyuvm directory.
#
# Outputs:
#   /logs/verifier/e2e_run.log   -- pytest output
#   /logs/verifier/e2e_passed    -- "1" if passed, "0" if failed
set -euo pipefail

export RV_ROOT=/app

cd /app/verification/top

# Run the PyUVM IRQ test.
python3 -m pytest -sv test_pyuvm/test_pyuvm.py \
    --timeout=1800 \
    -k "test_irq" \
    2>&1 | tee /logs/verifier/e2e_run.log || true

# Determine pass/fail from output.
if grep -qE "1 passed" /logs/verifier/e2e_run.log; then
    echo "1" > /logs/verifier/e2e_passed
else
    echo "0" > /logs/verifier/e2e_passed
fi

echo "E2E test done. passed=$(cat /logs/verifier/e2e_passed)"
