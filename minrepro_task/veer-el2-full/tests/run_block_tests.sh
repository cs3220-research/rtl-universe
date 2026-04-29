#!/bin/bash
# Run all VeeR EL2 block tests via nox and write results.
#
# Called from test.sh. Outputs:
#   /logs/verifier/nox_run.log  -- full nox output
#   /logs/verifier/status.json  -- nox session status
#   /logs/verifier/passed.txt   -- list of passing session names
#   /logs/verifier/passed_count -- integer count of passing sessions
set -euo pipefail

export RV_ROOT=/app

cd /app/verification/block

# Run all tagged test sessions. nox manages per-session virtualenvs and
# installs requirements from requirements.txt into them via session.install().
# Continue even if sessions fail; we count what passes.
nox -t tests 2>&1 | tee /logs/verifier/nox_run.log || true

# Copy status.json to logs for inspection.
if [ -f status.json ]; then
    cp status.json /logs/verifier/status.json
else
    echo "[]" > /logs/verifier/status.json
fi

# Parse results.
python3 /tmp/parse_block_results.py

echo "Block tests done. Passed=$(cat /logs/verifier/passed_count)"
