#!/bin/bash
# sync-skeleton.sh — Regenerate skeleton/ and warm_src/ for both nvdla task variants.
#
# Run this script from the outputs/ directory before building Docker images.
# It copies the green source repo, strips the RTL implementation to create
# the skeleton, and populates warm_src/ with the complete green source.
#
# Usage:
#   cd /path/to/outputs/
#   NVDLA_SOURCE=/path/to/nvdla bash tools/sync-skeleton.sh
#
# After running, build each Docker image:
#   cd nvdla-full/environment && docker build -t nvdla-full .
#   cd nvdla-e2e/environment  && docker build -t nvdla-e2e .

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="$(dirname "${SCRIPT_DIR}")"

NVDLA_SOURCE="${NVDLA_SOURCE:-/data/saketh/research/cs3220-research/rtl-universe/repos/nvdla}"

if [ ! -d "${NVDLA_SOURCE}" ]; then
    echo "ERROR: NVDLA source not found at ${NVDLA_SOURCE}"
    echo "Set NVDLA_SOURCE to the path of the nvdla repository."
    exit 1
fi

echo "=== Syncing NVDLA Harbor tasks from ${NVDLA_SOURCE} ==="

# Tasks to generate (both use the same skeleton and warm_src)
TASKS=(nvdla-full nvdla-e2e)

for task in "${TASKS[@]}"; do
    TASK_DIR="${OUTPUTS_DIR}/${task}"
    ENV_DIR="${TASK_DIR}/environment"

    echo "--- Task: ${task} ---"

    # -----------------------------------------------------------------------
    # 1. Populate warm_src/ (complete green source, no stripping)
    # -----------------------------------------------------------------------
    echo "  Syncing warm_src/..."
    mkdir -p "${ENV_DIR}/warm_src"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='outdir/' \
        --exclude='*.pyc' \
        --exclude='__pycache__/' \
        "${NVDLA_SOURCE}/" "${ENV_DIR}/warm_src/"

    # -----------------------------------------------------------------------
    # 2. Populate skeleton/ (implementation stripped, tests intact)
    # -----------------------------------------------------------------------
    echo "  Syncing skeleton/ (with RTL implementation stripped)..."
    mkdir -p "${ENV_DIR}/skeleton"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='outdir/' \
        --exclude='*.pyc' \
        --exclude='__pycache__/' \
        "${NVDLA_SOURCE}/" "${ENV_DIR}/skeleton/"

    # Strip implementation: zero out all Verilog files in vmod/nvdla/
    # Keep: vlibs/, rams/, include/ (these are support files, not implementation)
    # Strip: nvdla/ subdirectories (bdma, cacc, car, cbuf, cdma, cdp, cmac,
    #         csb_master, csc, glb, nocif, pdp, retiming, rubik, sdp, top)
    echo "  Stripping RTL implementation from skeleton/vmod/nvdla/..."
    find "${ENV_DIR}/skeleton/vmod/nvdla" -name "*.v" | while read -r f; do
        > "${f}"
    done

    echo "  Skeleton ready: $(find "${ENV_DIR}/skeleton/vmod/nvdla" -name "*.v" | wc -l) .v files zeroed"
    echo "  warm_src ready: $(find "${ENV_DIR}/warm_src/vmod/nvdla" -name "*.v" | wc -l) .v files intact"
done

echo ""
echo "=== Done. Next steps ==="
echo "  cd nvdla-full/environment && docker build -t nvdla-full ."
echo "  cd nvdla-e2e/environment  && docker build -t nvdla-e2e ."
