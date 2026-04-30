#!/bin/bash
# solve.sh — Reference solution for caliptra-rtl Harbor task (caliptra-full).
# Run on the Harbor HOST (not inside the container).
# Copies the full green source (warm_src) into the running container's /app,
# then commits the change so the verifier sees it.
#
# Usage:
#   bash solution/solve.sh <container_id_or_name>

set -euo pipefail

CONTAINER_ID="${1:?Usage: solve.sh <container_id>}"
TASK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WARM_SRC="${TASK_DIR}/environment/warm_src"

if [ ! -d "${WARM_SRC}" ]; then
    echo "ERROR: warm_src not found at ${WARM_SRC}"
    echo "Populate warm_src from repos/caliptra-rtl (with submodules initialized)."
    exit 1
fi

echo "[solve] Copying green source into container ${CONTAINER_ID}..."
docker cp "${WARM_SRC}/." "${CONTAINER_ID}:/app/"

echo "[solve] Committing restored source..."
docker exec "${CONTAINER_ID}" bash -c '
    cd /app
    git add -A
    git -c user.email=x@x -c user.name=x commit -q -m "restore green source"
    echo "[solve] Done. Container /app now has the full implementation."
'
