#!/bin/bash
# solve.sh — Reference solution for opentitan-full.
#
# Run on the Harbor HOST (not inside the container).
# Copies the warm_src (green source) into the running container's /app
# so the verifier will score 1.0.
#
# Usage:
#   CONTAINER_ID=<container-id> bash solution/solve.sh
#
# Or let Harbor call it automatically with $HARBOR_CONTAINER_ID set.

set -euo pipefail

CONTAINER_ID="${CONTAINER_ID:-${HARBOR_CONTAINER_ID:?'Set CONTAINER_ID or HARBOR_CONTAINER_ID'}}"
TASK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WARM_SRC="${TASK_DIR}/environment/warm_src"

if [ ! -d "${WARM_SRC}" ]; then
    echo "ERROR: warm_src/ not found at ${WARM_SRC}"
    echo "Run tools/sync-skeleton.sh first to populate warm_src/."
    exit 1
fi

echo "Copying green source into container ${CONTAINER_ID}..."
docker cp "${WARM_SRC}/." "${CONTAINER_ID}:/app/"

echo "Committing..."
docker exec "${CONTAINER_ID}" bash -c \
    'cd /app && git add -A && git -c user.email=x@x -c user.name=x commit -q -m solve 2>/dev/null || true'

echo "solve.sh: green source copied. Run verifier to confirm reward = 1.0"
