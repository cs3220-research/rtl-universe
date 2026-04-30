#!/bin/bash
# solve.sh — Reference solution for nvdla-full.
# Run on the Harbor HOST (not inside the container).
# Copies the full green RTL source back into /app, replacing the skeleton.
#
# Usage:
#   CONTAINER_ID=<id> bash solve.sh
#
# The container must already be running.

set -euo pipefail

CONTAINER_ID="${CONTAINER_ID:?ERROR: set CONTAINER_ID to the running container ID}"
WARM_SRC="${WARM_SRC:-$(dirname "$0")/../environment/warm_src}"

if [ ! -d "${WARM_SRC}" ]; then
    echo "ERROR: warm_src not found at ${WARM_SRC}"
    echo "Run tools/sync-skeleton.sh first to populate environment/warm_src."
    exit 1
fi

echo "Copying green source into container ${CONTAINER_ID}..."
docker cp "${WARM_SRC}/." "${CONTAINER_ID}:/app/"

echo "Committing restored source..."
docker exec "${CONTAINER_ID}" bash -c '
    cd /app
    git add -A
    git -c user.email=x@x -c user.name=x commit -q -m solve
'

echo "Done. Run the verifier:"
echo "  docker exec ${CONTAINER_ID} bash /tests/test.sh"
echo "  docker exec ${CONTAINER_ID} cat /logs/verifier/reward.txt"
