#!/bin/bash
# sync-skeleton.sh — Regenerate environment/skeleton/ and environment/warm_src/
# for each openpiton task variant from the canonical green source repo.
#
# Run this from the outputs/ directory before building Docker images.
#
# Usage:  bash tools/sync-skeleton.sh
#
# This script:
#   1. Rsyncs the green source repo into warm_src/ for each task variant
#   2. Creates a skeleton/ from warm_src/ by truncating implementation files
#   3. Validates that the 4 implementation files are empty in skeleton/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="$(dirname "$SCRIPT_DIR")"
GREEN_SOURCE="/data/saketh/research/cs3220-research/rtl-universe/repos/openpiton"

if [ ! -d "$GREEN_SOURCE" ]; then
    echo "error: green source not found at $GREEN_SOURCE" >&2
    exit 1
fi

echo "=== Syncing from $GREEN_SOURCE ==="

for VARIANT in openpiton-full openpiton-e2e; do
    ENV_DIR="$OUTPUTS_DIR/$VARIANT/environment"
    WARM="$ENV_DIR/warm_src"
    SKEL="$ENV_DIR/skeleton"

    echo ""
    echo "--- Variant: $VARIANT ---"

    # 1. Sync green source to warm_src/
    echo "  Syncing warm_src/ ..."
    mkdir -p "$WARM"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='build/' \
        "$GREEN_SOURCE"/ "$WARM"/

    # 2. Create skeleton/ from warm_src/
    echo "  Creating skeleton/ ..."
    rsync -a --delete "$WARM"/ "$SKEL"/

    # 3. Truncate implementation files (strip content, keep empty files)
    echo "  Stripping implementation files ..."
    : > "$SKEL/piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v"
    : > "$SKEL/piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v"
    : > "$SKEL/piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v"
    : > "$SKEL/piton/design/common/uart_pkttrace_dump/rtl/uart_serializer.v"

    # 4. Validate
    echo "  Validating stripped files ..."
    STRIPPED_OK=1
    for f in \
        "piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v" \
        "piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v" \
        "piton/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v" \
        "piton/design/common/uart_pkttrace_dump/rtl/uart_serializer.v"; do
        if [ -s "$SKEL/$f" ]; then
            echo "  ERROR: $f is not empty in skeleton!" >&2
            STRIPPED_OK=0
        else
            echo "    [OK] $f is empty"
        fi
    done

    if [ "$STRIPPED_OK" -eq 1 ]; then
        echo "  $VARIANT: skeleton OK"
    else
        echo "  $VARIANT: skeleton has errors!" >&2
    fi
done

echo ""
echo "=== sync-skeleton.sh complete ==="
echo "Next step: cd to a variant directory and run:"
echo "  docker build -t openpiton-full environment/"
echo "  docker build -t openpiton-e2e  environment/"
