#!/bin/bash
# Run the VeeR EL2 E2E PyUVM test on the green source and capture the count.
#
# The E2E test is the top-level PyUVM IRQ test (test_irq.test_irq) which
# exercises the full VeeR EL2 core pipeline with cocotb/Verilator.
# There is 1 E2E test in the green source.
#
# This script writes:
#   /tmp/_e2e_total     -- number of E2E tests (1)
#   /tmp/_e2e_tests     -- list of E2E test names
#   /tmp/_total         -- same as e2e_total (this task only scores E2E)
#   /tmp/_all_tests     -- same as e2e_tests
set -euo pipefail

export RV_ROOT=/app

cd /app/verification/top

# Install requirements for the top-level test (uses custom cocotb from pip).
pip3 install --break-system-packages -r requirements.txt 2>/dev/null || true

# Run the PyUVM IRQ test via pytest.
# UVM_TEST=test_irq.test_irq is the parametrize value from test_pyuvm.py.
python3 -m pytest -sv test_pyuvm/test_pyuvm.py \
    --timeout=1800 \
    -k "test_irq" \
    --html=/tmp/e2e_report.html \
    --self-contained-html \
    2>&1 | tee /tmp/e2e_run.log || true

# Count passing tests from pytest output.
PASSED=0
if grep -q "1 passed" /tmp/e2e_run.log; then
    PASSED=1
fi

# The E2E task has 1 scored test.
E2E_TOTAL=1

printf "test_irq.test_irq\n" > /tmp/_e2e_tests
printf "test_irq.test_irq\n" > /tmp/_all_tests
echo "$E2E_TOTAL" > /tmp/_e2e_total
echo "$E2E_TOTAL" > /tmp/_total

echo "E2E count: passed=$PASSED, total=$E2E_TOTAL"
