#!/bin/bash
# audit_hardening.sh — Check a Docker image for leaked implementation artifacts
#
# Usage:
#   ./audit_hardening.sh <image-name>
#
# Checks the final Docker image for artifacts that could leak the green
# implementation to an agent.

set -euo pipefail

IMAGE="${1:?Usage: audit_hardening.sh <image-name>}"

echo "=== Hardening Audit: $IMAGE ==="
echo ""

CONTAINER=$(docker run -d "$IMAGE" sh -c "sleep infinity")

echo "1. Checking for sandbox stash (warm-build generated files)..."
STASH=$(docker exec "$CONTAINER" bash -c '
  find ~/.cache/bazel -path "*/sandbox/sandbox_stash" -type d 2>/dev/null | head -3
')
if [ -n "$STASH" ]; then
  echo "  FAIL: sandbox_stash found:"
  echo "  $STASH"
else
  echo "  PASS: No sandbox_stash"
fi
echo ""

echo "2. Checking for warm-build test logs..."
LOGS=$(docker exec "$CONTAINER" bash -c '
  find ~/.cache/bazel -path "*/testlogs" -type d 2>/dev/null | head -3
')
if [ -n "$LOGS" ]; then
  echo "  FAIL: testlogs found:"
  echo "  $LOGS"
else
  echo "  PASS: No testlogs"
fi
echo ""

echo "3. Checking for project build outputs in bin/..."
BINS=$(docker exec "$CONTAINER" bash -c '
  for d in hdl tests sw examples fpga build; do
    find ~/.cache/bazel -path "*/bin/$d" -type d 2>/dev/null
  done | head -5
')
if [ -n "$BINS" ]; then
  echo "  FAIL: Project build outputs found:"
  echo "  $BINS"
else
  echo "  PASS: No project build outputs in bin/"
fi
echo ""

echo "4. Checking /app for implementation files (should be empty/skeleton)..."
IMPL=$(docker exec "$CONTAINER" bash -c '
  # Check for non-test Scala files
  find /app -name "*.scala" ! -name "*Test*" ! -name "*Spec*" ! -name "*TestUtils*" \
    -path "*/src/*" 2>/dev/null | head -5
')
if [ -n "$IMPL" ]; then
  echo "  FAIL: Implementation .scala files found in /app:"
  echo "  $IMPL"
else
  echo "  PASS: No implementation Scala in /app"
fi
echo ""

echo "5. Checking .harbor/ directory..."
docker exec "$CONTAINER" bash -c '
  echo "  Files in .harbor/:"
  ls -la /app/.harbor/ 2>/dev/null || echo "  .harbor/ not found"
  echo "  Total tests:"
  cat /app/.harbor/total_tests 2>/dev/null || echo "  N/A"
'
echo ""

echo "6. Checking external deps (should be third-party only)..."
EXT_COUNT=$(docker exec "$CONTAINER" bash -c '
  ls ~/.cache/bazel/_bazel_*/*/external/ 2>/dev/null | grep -v ".marker" | wc -l
')
echo "  External dependencies: $EXT_COUNT"
echo "  (These should be third-party: Verilator, TFLite, cvfpu, etc.)"
echo ""

docker stop "$CONTAINER" > /dev/null && docker rm "$CONTAINER" > /dev/null

echo "=== Audit Complete ==="
