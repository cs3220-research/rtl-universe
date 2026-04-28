#!/usr/bin/env bash
# sync-skeleton.sh — Regenerate environment/skeleton/ and environment/warm_src/
# for the pulp-common-cells Harbor task from the canonical source repo.
#
# Usage: ./tools/sync-skeleton.sh [--source <path-to-common_cells-repo>]
#
# Default source: ../../../repos/common_cells (relative to the minrepro_task root)
# Output:         pulp-common-cells/environment/skeleton/
#                 pulp-common-cells/environment/warm_src/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TASK_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TASK_ENV="$TASK_DIR/pulp-common-cells/environment"

# Allow override via --source flag
SOURCE_REPO=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --source) SOURCE_REPO="$2"; shift 2 ;;
        *) echo "Unknown arg: $1" >&2; exit 1 ;;
    esac
done

if [ -z "$SOURCE_REPO" ]; then
    # Default: three levels up from outputs/ to the repos/ directory
    SOURCE_REPO="$(cd "$SCRIPT_DIR/../../repos/common_cells" 2>/dev/null && pwd)" || true
    if [ ! -d "$SOURCE_REPO" ]; then
        echo "Error: cannot find common_cells repo. Use --source <path>." >&2
        exit 1
    fi
fi

echo "Source repo:  $SOURCE_REPO"
echo "Task env:     $TASK_ENV"
echo ""

SKELETON="$TASK_ENV/skeleton"
WARM_SRC="$TASK_ENV/warm_src"

# ── Sync warm_src (full green source) ────────────────────────────────────────
echo "[1/2] Syncing warm_src from green source..."
mkdir -p "$WARM_SRC"
rsync -a --delete \
    --exclude='.git/' \
    --exclude='.bender/' \
    --exclude='tmp/' \
    --exclude='obj_dir/' \
    "$SOURCE_REPO/" "$WARM_SRC/"
echo "warm_src synced."

# ── Sync skeleton (implementation stripped) ──────────────────────────────────
echo "[2/2] Syncing skeleton (stripping RTL implementation)..."
mkdir -p "$SKELETON"
rsync -a --delete \
    --exclude='.git/' \
    --exclude='.bender/' \
    --exclude='tmp/' \
    --exclude='obj_dir/' \
    "$SOURCE_REPO/" "$SKELETON/"

# Strip all RTL source files under src/ by truncating them to zero bytes.
# Keep the files (BUILD system references them by name) but remove the content.
# The agent must recreate the content.
find "$SKELETON/src" -name "*.sv" -exec sh -c '> "$1"' _ {} \;

echo "Skeleton synced and stripped."
echo ""
echo "Done. Next steps:"
echo "  1. cd $TASK_DIR"
echo "  2. docker build -t pulp-common-cells ./pulp-common-cells/environment/"
echo "  3. Run the verifier to verify unsolved score ~0"
echo "  4. Run solve.sh to copy warm_src back and verify solved score = 1.0"
