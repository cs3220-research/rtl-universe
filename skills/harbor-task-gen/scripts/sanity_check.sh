#!/bin/bash
# sanity_check.sh — Verify a Harbor task works at both extremes
#
# Usage:
#   ./sanity_check.sh <task-dir> <image-name>
#
# Checks:
#   1. Unsolved skeleton scores ~0 (no freebie tests beyond documented ones)
#   2. Solved (green source copied in) scores 1.0
#   3. Docker image builds successfully

set -euo pipefail

TASK_DIR="${1:?Usage: sanity_check.sh <task-dir> <image-name>}"
IMAGE="${2:?Usage: sanity_check.sh <task-dir> <image-name>}"

echo "=== Sanity Check: $TASK_DIR ==="
echo ""

# Check skeleton and warm_src exist
if [ ! -d "$TASK_DIR/environment/skeleton" ]; then
  echo "ERROR: $TASK_DIR/environment/skeleton/ not found. Run sync-skeleton.sh first."
  exit 1
fi
if [ ! -d "$TASK_DIR/environment/warm_src" ]; then
  echo "ERROR: $TASK_DIR/environment/warm_src/ not found. Run sync-skeleton.sh first."
  exit 1
fi

# Build image if needed
echo "1. Building Docker image..."
docker build -t "$IMAGE" "$TASK_DIR/environment/" 2>&1 | tail -3
echo ""

# Test unsolved (bare skeleton)
echo "2. Testing unsolved skeleton (expect reward ~0)..."
CONTAINER=$(docker run -d --cpus=4 --memory=16g "$IMAGE" sh -c "sleep infinity")
UNSOLVED_REWARD=$(docker exec -u builder "$CONTAINER" bash -c '
  cd /app
  bash /tests/test.sh 2>/dev/null
  cat /logs/verifier/reward.txt 2>/dev/null || echo "ERROR"
')
docker stop "$CONTAINER" > /dev/null && docker rm "$CONTAINER" > /dev/null
echo "  Unsolved reward: $UNSOLVED_REWARD"
echo ""

# Test solved (copy green source)
echo "3. Testing with green source (expect reward 1.0)..."
CONTAINER=$(docker run -d --cpus=8 --memory=32g "$IMAGE" sh -c "sleep infinity")
docker cp "$TASK_DIR/environment/warm_src/." "$CONTAINER:/app/"
docker exec "$CONTAINER" chown -R builder:builder /app/
SOLVED_REWARD=$(docker exec -u builder "$CONTAINER" bash -c '
  cd /app
  git add -A
  git -c user.email=x@x -c user.name=x commit -q -m solve 2>/dev/null || true
  bash /tests/test.sh 2>/dev/null
  cat /logs/verifier/reward.txt 2>/dev/null || echo "ERROR"
')
docker stop "$CONTAINER" > /dev/null && docker rm "$CONTAINER" > /dev/null
echo "  Solved reward: $SOLVED_REWARD"
echo ""

# Validate
echo "=== Results ==="
echo "  Unsolved: $UNSOLVED_REWARD (expected ~0)"
echo "  Solved:   $SOLVED_REWARD (expected 1.0)"

if [ "$SOLVED_REWARD" = "1.000000" ] || [ "$SOLVED_REWARD" = "1.0" ]; then
  echo "  PASS: Solved score is 1.0"
else
  echo "  WARN: Solved score is not 1.0 — investigate failing tests"
fi
