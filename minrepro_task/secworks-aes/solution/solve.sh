#!/bin/bash
# Reference solution: restore the canonical green RTL sources into the task
# container.  Runs on the Harbor host (outside the container).
#
# Usage: solve.sh <container>

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <container>" >&2
    exit 2
fi

container="$1"
here="$(cd "$(dirname "$0")" && pwd)"

# The full green source lives at environment/warm_src relative to the task dir.
# Only the RTL files were stripped, so we copy just src/rtl/ from warm_src.
warm="$here/../environment/warm_src"
if [ ! -d "$warm" ]; then
    echo "error: warm source not found at $warm" >&2
    echo "       Ensure environment/warm_src/ is populated." >&2
    exit 1
fi

# Copy the implementation RTL files into the container (restores the 7 stripped files)
docker cp "$warm/src/rtl/." "$container":/app/src/rtl/

# Commit so the agent's git history is clean
docker exec "$container" bash -c \
    'cd /app && git add -A && \
     git -c user.email=x@x -c user.name=x commit -q -m solve || true'

echo "Solution applied to container $container"
echo "Run the verifier to confirm reward = 1.0"
