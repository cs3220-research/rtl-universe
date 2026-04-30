#!/bin/bash
# Run the VeeR EL2 block tests on the green source and capture the pass count.
#
# This script:
#   1. Generates VeeR config (required before any block test can build)
#   2. Runs every block test via nox -t tests
#   3. Parses the nox status.json to count passing sessions
#   4. Writes /tmp/_total and /tmp/_all_tests for later stages
#
# Must be run from /app (the workspace root with RV_ROOT set).
set -euo pipefail

export RV_ROOT=/app

# Generate VeeR config headers (common_defines.vh, el2_pdef.vh).
# Block tests expect these in verification/block/snapshots/default/.
# veer.config creates snapshots/default/ relative to cwd, so run from
# the verification/block/ directory.
cd /app/verification/block
$RV_ROOT/configs/veer.config -set build_axi4
echo "Config generated: $(ls snapshots/default/*.vh 2>/dev/null | wc -l) header files"

# Run all tagged test sessions. nox writes status.json due to nox.options.report.
# The noxfile uses session.install() to populate per-session venvs from
# requirements.txt. We let nox manage its own virtualenvs (the default).
# Continue even if some sessions fail; we want to count passes.
nox -t tests 2>&1 | tee /tmp/nox_run.log || true

# Parse status.json produced by nox (always in cwd where nox was invoked)
python3 /tmp/parse_nox_results.py

echo "count_tests.sh done. Total=$(cat /tmp/_total)"
