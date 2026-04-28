#!/bin/bash
# Reference solution: restore the canonical green RTL sources into the task
# container. Runs on the Harbor host (outside the container).
#
# Usage: solve.sh <container>

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <container>" >&2
    exit 2
fi

container="$1"
here="$(cd "$(dirname "$0")" && pwd)"

warm="$here/../environment/warm_src"
if [ ! -d "$warm" ]; then
    echo "error: warm source not found at $warm" >&2
    echo "       Ensure environment/warm_src/ is populated (run sync-skeleton.sh)." >&2
    exit 1
fi

# Restore the stripped RTL implementation files from warm_src/src/
docker cp "$warm/src/." "$container":/app/src/

# Commit so the agent's git history is clean
docker exec "$container" bash -c \
    'cd /app && git add -A && \
     git -c user.email=x@x -c user.name=x commit -q -m solve || true'

echo "Solution applied to container $container"
echo "Run the verifier to confirm reward = 1.0"
