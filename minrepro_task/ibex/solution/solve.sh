#!/bin/bash
# Reference solution: restore the canonical green source into the task container.
# Runs on the Harbor host (outside the container).
#
# Usage: solve.sh <container_id>

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <container>" >&2
    exit 2
fi

container="$1"
here="$(cd "$(dirname "$0")" && pwd)"

# The full green source lives at environment/warm_src in the task directory.
# (sync-skeleton.sh populates it from the ibex repo.)
warm="$here/../environment/warm_src"
if [ ! -d "$warm" ]; then
    echo "error: warm source not found at $warm" >&2
    echo "       run tools/sync-skeleton.sh first" >&2
    exit 1
fi

# Copy green source over the skeleton workspace inside the container.
docker cp "$warm/." "$container":/app/

# Stage and commit so the agent's git history reflects the restored source.
docker exec "$container" bash -c \
    'cd /app && git add -A && git -c user.email=x@x -c user.name=x commit -q -m solve || true'

echo "Solved: green source copied into container $container"
