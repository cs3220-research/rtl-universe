#!/bin/bash
# sync-skeleton.sh — Regenerate environment/skeleton/ and environment/warm_src/
# for the caliptra-rtl and caliptra-rtl-fw Harbor tasks from the green source.
#
# Run this whenever the source repo changes before rebuilding the Docker image.
#
# Usage:
#   CALIPTRA_SRC=/path/to/caliptra-rtl bash tools/sync-skeleton.sh

set -euo pipefail

CALIPTRA_SRC="${CALIPTRA_SRC:-/data/saketh/research/cs3220-research/rtl-universe/repos/caliptra-rtl}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="${SCRIPT_DIR}/../outputs"

if [ ! -d "${CALIPTRA_SRC}" ]; then
    echo "ERROR: CALIPTRA_SRC=${CALIPTRA_SRC} does not exist"
    exit 1
fi

echo "[sync-skeleton] Source: ${CALIPTRA_SRC}"

# ── Sync warm_src (green source, for Docker build count stage) ─────────────
for task in caliptra-rtl caliptra-rtl-fw; do
    WARM_DST="${OUTPUTS_DIR}/${task}/environment/warm_src"
    echo "[sync-skeleton] Syncing warm_src for ${task}..."
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='obj_dir/' \
        --exclude='*.log' \
        --exclude='*.dis' \
        --exclude='*.exe' \
        --exclude='*.o' \
        --exclude='*.hex' \
        --exclude='program.hex' \
        --exclude='dccm.hex' \
        --exclude='iccm.hex' \
        "${CALIPTRA_SRC}/" "${WARM_DST}/"
    echo "[sync-skeleton]   Done: ${WARM_DST}"
done

# ── Build skeleton (stripped implementation) for caliptra-rtl ─────────────
SKELETON_DST="${OUTPUTS_DIR}/caliptra-rtl/environment/skeleton"
echo "[sync-skeleton] Building skeleton for caliptra-rtl..."

rsync -a --delete \
    --exclude='.git/' \
    --exclude='obj_dir/' \
    --exclude='*.log' \
    --exclude='*.dis' \
    --exclude='*.exe' \
    --exclude='*.o' \
    --exclude='*.hex' \
    "${CALIPTRA_SRC}/" "${SKELETON_DST}/"

# Strip RTL implementation files from src/*/rtl/ (not submodules)
# Strategy: zero out .sv and .v files in rtl/ directories
# Keep: *_pkg.sv, *.svh (package/defines/includes), *_reg.sv (generated regs)
# Strip: all other .sv and .v implementation files
echo "[sync-skeleton] Stripping RTL implementation files..."

find "${SKELETON_DST}/src" -path "*/rtl/*.sv" | while read -r f; do
    basename="$(basename "${f}")"
    # Keep package files, header includes, and generated register files
    if [[ "${basename}" == *_pkg.sv ]] || \
       [[ "${basename}" == *.svh ]] || \
       [[ "${basename}" == *_reg.sv ]]; then
        continue
    fi
    # Truncate (zero out) the file — keeps the file so .vf references still resolve
    > "${f}"
done

find "${SKELETON_DST}/src" -path "*/rtl/*.v" | while read -r f; do
    basename="$(basename "${f}")"
    # Keep .vh header files
    if [[ "${basename}" == *.vh ]]; then
        continue
    fi
    > "${f}"
done

# Strip submodule RTL implementation as well (adams-bridge)
find "${SKELETON_DST}/submodules" -path "*/rtl/*.sv" | while read -r f; do
    basename="$(basename "${f}")"
    if [[ "${basename}" == *_pkg.sv ]] || \
       [[ "${basename}" == *.svh ]]; then
        continue
    fi
    > "${f}"
done

find "${SKELETON_DST}/submodules" -path "*/rtl/*.v" | while read -r f; do
    basename="$(basename "${f}")"
    if [[ "${basename}" == *.vh ]]; then
        continue
    fi
    > "${f}"
done

echo "[sync-skeleton] Skeleton RTL stripping complete"
echo "[sync-skeleton] Files zeroed: $(find "${SKELETON_DST}" -path "*/rtl/*.sv" -empty | wc -l) .sv, $(find "${SKELETON_DST}" -path "*/rtl/*.v" -empty | wc -l) .v"

# ── Build skeleton for caliptra-rtl-fw ───────────────────────────────────
# The firmware-driven test variant: strips RTL AND firmware test C files
SKELETON_FW="${OUTPUTS_DIR}/caliptra-rtl-fw/environment/skeleton"
echo "[sync-skeleton] Building skeleton for caliptra-rtl-fw..."

rsync -a --delete \
    --exclude='.git/' \
    --exclude='obj_dir/' \
    --exclude='*.log' \
    "${SKELETON_DST}/" "${SKELETON_FW}/"

# Additionally strip firmware test C files (agent must implement those too)
echo "[sync-skeleton] Stripping firmware test files..."
find "${SKELETON_FW}/src/integration/test_suites" \
    -name "*.c" \
    -not -path "*/libs/*" \
    -not -name "*.h" | while read -r f; do
    > "${f}"
done

echo "[sync-skeleton] Skeleton (fw variant) complete"

echo ""
echo "[sync-skeleton] All tasks synced successfully."
echo "  caliptra-rtl warm_src:  ${OUTPUTS_DIR}/caliptra-rtl/environment/warm_src"
echo "  caliptra-rtl skeleton:  ${OUTPUTS_DIR}/caliptra-rtl/environment/skeleton"
echo "  caliptra-rtl-fw warm_src: ${OUTPUTS_DIR}/caliptra-rtl-fw/environment/warm_src"
echo "  caliptra-rtl-fw skeleton: ${OUTPUTS_DIR}/caliptra-rtl-fw/environment/skeleton"
