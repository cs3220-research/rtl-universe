#!/bin/bash
# Reference solution: restore the canonical sources into the task container.
# Runs on the Harbor host (outside the container).
#
# Usage:  solve.sh <container>

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
    echo "       (run tools/sync-skeleton.sh first)" >&2
    exit 1
fi

docker cp "$warm/." "$container":/app/
docker exec "$container" bash -c \
    'cd /app && git add -A && git -c user.email=x@x -c user.name=x commit -q -m solve || true'
