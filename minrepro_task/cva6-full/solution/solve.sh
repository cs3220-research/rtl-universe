#!/bin/bash
# Reference solution for the cva6 Harbor task.
#
# Run on the Harbor HOST (not inside the container).
# Usage:  CONTAINER_ID=<id> bash solve.sh
#
# Copies the green-source skeleton back into the container, then commits
# so the verifier sees a clean git state.

set -euo pipefail

CONTAINER_ID="${CONTAINER_ID:?Must set CONTAINER_ID to the running container ID}"
TASK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WARM_SRC="${TASK_DIR}/environment/warm_src"

if [ ! -d "${WARM_SRC}" ]; then
    echo "ERROR: warm_src directory not found at ${WARM_SRC}"
    echo "Run tools/sync-skeleton.sh first to populate environment/warm_src/"
    exit 1
fi

echo "Copying green source into container ${CONTAINER_ID}..."
docker cp "${WARM_SRC}/." "${CONTAINER_ID}:/app/"

echo "Committing restored source inside container..."
docker exec "${CONTAINER_ID}" bash -c '
    cd /app
    git add -A
    git -c user.email=x@x -c user.name=x commit -q -m "restore green source"
    echo "Done. Run the verifier to confirm reward=1.0"
'
