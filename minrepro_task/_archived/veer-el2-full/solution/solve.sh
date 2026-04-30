#!/bin/bash
# Reference solution: restore the canonical RTL sources into the task container.
# Runs on the Harbor host (outside the container).
#
# Usage: solve.sh <container>

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <container>" >&2
    exit 2
fi

container="$1"
here="$(cd "$(dirname "$0")" && pwd)"

# The full green source lives at environment/warm_src relative to this task dir.
warm="$here/../environment/warm_src"
if [ ! -d "$warm" ]; then
    echo "error: warm source not found at $warm" >&2
    echo "Run the skeleton sync script first to populate environment/warm_src/" >&2
    exit 1
fi

# Copy the green design RTL over the agent's /app, preserving test files.
docker cp "$warm/." "$container":/app/
docker exec "$container" bash -c \
    'cd /app && git add -A && git -c user.email=x@x -c user.name=x commit -q -m solve || true'

echo "Solution applied to container $container"
