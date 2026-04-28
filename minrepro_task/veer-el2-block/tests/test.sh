#!/bin/bash
# Verifier entrypoint for the veer-el2-block Harbor task.
#
# Runs all block-level cocotb tests via nox, counts passing test functions,
# and writes a proportional reward in [0, 1] to /logs/verifier/reward.txt.
#
# Scoring denominator: test functions that passed on the green source,
# stored at /app/.harbor/total_tests at image build time.

set -u
mkdir -p /logs/verifier
cd /app

export RV_ROOT=/app
export PATH="${HOME}/.local/bin:${PATH}"

# Denominator
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=0
fi

echo "=== VeeR EL2 block verifier: total_tests=${TOTAL} ==="

# Install requirements (idempotent; uses cached packages from image)
pip3 install --user --quiet -r /app/verification/block/requirements.txt 2>&1 | tail -3 || true

# Run all block-level tests via nox; keep going through failures
cd /app/verification/block
nox -t tests 2>&1 | tee /logs/verifier/nox.log || true

# Count passing test functions from cocotb results.xml files
PASSED=$(python3 - <<'PYEOF' 2>/dev/null
import os, glob
from xml.etree import ElementTree as ET
passed = 0
for xml_file in glob.glob('/app/verification/block/**/*.xml', recursive=True):
    try:
        tree = ET.parse(xml_file)
        for ts in tree.iter('testsuite'):
            for tc in ts.iter('testcase'):
                failures = list(tc.iter('failure')) + list(tc.iter('error'))
                if not failures:
                    passed += 1
    except Exception:
        pass
print(passed)
PYEOF
)
PASSED="${PASSED:-0}"

# Emit per-block summary
echo "=== Per-block results ==="
for xml_file in /app/verification/block/*/*.xml; do
    block=$(basename "$(dirname "$xml_file")")
    block_pass=$(python3 - "$xml_file" <<'PYEOF' 2>/dev/null
import sys
from xml.etree import ElementTree as ET
p = 0
try:
    tree = ET.parse(sys.argv[1])
    for ts in tree.iter('testsuite'):
        for tc in ts.iter('testcase'):
            if not list(tc.iter('failure')) and not list(tc.iter('error')):
                p += 1
except Exception:
    pass
print(p)
PYEOF
    )
    echo "  ${block}: ${block_pass:-0} passed"
done

cd /app
echo "=== Results: passed=${PASSED}, total=${TOTAL} ==="

if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "print(f'{min(${PASSED}, ${TOTAL}) / ${TOTAL}:.6f}')" \
        > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"

# Structured pytest report (non-fatal)
if command -v uvx >/dev/null 2>&1; then
    uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
        pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
