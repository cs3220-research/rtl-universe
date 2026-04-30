#!/bin/bash
# solve.sh — Reference solution for the cva6 Harbor task.
# Restores the canonical green RTL sources into the task container.
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

warm="${here}/../environment/warm_src"
if [ ! -d "${warm}" ]; then
    echo "error: warm source not found at ${warm}" >&2
    echo "       Run tools/sync-skeleton.sh to populate environment/warm_src/." >&2
    exit 1
fi

# Restore stripped RTL implementation files
# Core RTL modules
docker cp "${warm}/core/." "${container}:/app/core/"

# Boot ROM
docker cp "${warm}/corev_apu/bootrom/." "${container}:/app/corev_apu/bootrom/"

# Commit so the agent's git history is clean
docker exec "${container}" bash -c \
    'cd /app && git add -A && \
     git -c user.email=x@x -c user.name=x commit -q -m solve || true'

echo "Solution applied to container ${container}"
echo "Run the verifier to confirm reward = 1.0"
