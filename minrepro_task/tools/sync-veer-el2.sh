#!/bin/bash
# sync-skeleton.sh — Regenerates skeleton/ and warm_src/ for both task variants
# from the canonical VeeR EL2 source repo.
#
# Usage:
#   ./tools/sync-skeleton.sh [--source /path/to/veer-el2]
#
# Default source: inferred from this script's location (../../../repos/veer-el2)
#
# This script:
#   1. Copies the full green source to environment/warm_src/ for each task
#   2. Creates environment/skeleton/ by zeroing all implementation RTL
#
# Run this whenever the source repo changes before rebuilding Docker images.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default source location
SOURCE="${1:-}"
if [ -z "$SOURCE" ]; then
    # Try to find the repo relative to the skill workspace
    CANDIDATE="$(cd "$SCRIPT_DIR/../../../../repos/veer-el2" 2>/dev/null && pwd)" || true
    if [ -d "$CANDIDATE" ]; then
        SOURCE="$CANDIDATE"
    else
        echo "error: cannot find veer-el2 source. Pass as argument: $0 /path/to/veer-el2" >&2
        exit 1
    fi
fi

if [ ! -f "$SOURCE/configs/veer.config" ]; then
    echo "error: $SOURCE does not look like a VeeR EL2 repo (missing configs/veer.config)" >&2
    exit 1
fi

echo "Source: $SOURCE"

# Implementation RTL files to zero out in the skeleton.
# These are all files listed in testbench/flist that are design sources.
IMPL_FILES=(
    "design/el2_veer.sv"
    "design/el2_veer_wrapper.sv"
    "design/el2_veer_lockstep.sv"
    "design/el2_mem.sv"
    "design/el2_pic_ctrl.sv"
    "design/el2_dma_ctrl.sv"
    "design/el2_pmp.sv"
    "design/ifu/el2_ifu_aln_ctl.sv"
    "design/ifu/el2_ifu_compress_ctl.sv"
    "design/ifu/el2_ifu_ifc_ctl.sv"
    "design/ifu/el2_ifu_bp_ctl.sv"
    "design/ifu/el2_ifu_ic_mem.sv"
    "design/ifu/el2_ifu_mem_ctl.sv"
    "design/ifu/el2_ifu_iccm_mem.sv"
    "design/ifu/el2_ifu.sv"
    "design/dec/el2_dec_decode_ctl.sv"
    "design/dec/el2_dec_gpr_ctl.sv"
    "design/dec/el2_dec_ib_ctl.sv"
    "design/dec/el2_dec_pmp_ctl.sv"
    "design/dec/el2_dec_tlu_ctl.sv"
    "design/dec/el2_dec_trigger.sv"
    "design/dec/el2_dec.sv"
    "design/exu/el2_exu_alu_ctl.sv"
    "design/exu/el2_exu_mul_ctl.sv"
    "design/exu/el2_exu_div_ctl.sv"
    "design/exu/el2_exu.sv"
    "design/lsu/el2_lsu.sv"
    "design/lsu/el2_lsu_clkdomain.sv"
    "design/lsu/el2_lsu_addrcheck.sv"
    "design/lsu/el2_lsu_lsc_ctl.sv"
    "design/lsu/el2_lsu_stbuf.sv"
    "design/lsu/el2_lsu_bus_buffer.sv"
    "design/lsu/el2_lsu_bus_intf.sv"
    "design/lsu/el2_lsu_ecc.sv"
    "design/lsu/el2_lsu_dccm_mem.sv"
    "design/lsu/el2_lsu_dccm_ctl.sv"
    "design/lsu/el2_lsu_trigger.sv"
    "design/dbg/el2_dbg.sv"
    "design/dmi/dmi_mux.v"
    "design/dmi/dmi_wrapper.v"
    "design/dmi/dmi_jtag_to_core_sync.v"
    "design/dmi/rvjtag_tap.v"
    "design/lib/el2_lib.sv"
    "design/lib/el2_mem_if.sv"
    "design/lib/beh_lib.sv"
    "design/lib/mem_lib.sv"
)

sync_task() {
    local task_name="$1"
    local task_dir="$OUTPUTS_DIR/$task_name"
    local warm_dst="$task_dir/environment/warm_src"
    local skel_dst="$task_dir/environment/skeleton"

    echo ""
    echo "=== Syncing $task_name ==="

    # ---- warm_src: full green source ----
    echo "  Syncing warm_src..."
    mkdir -p "$warm_dst"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='snapshots/' \
        --exclude='obj_dir/' \
        --exclude='sim-build*/' \
        --exclude='*.log' \
        --exclude='*.xml' \
        --exclude='*.vcd' \
        --exclude='*.fst' \
        --exclude='verilator-build' \
        "$SOURCE/" "$warm_dst/"

    # ---- skeleton: zeroed implementation ----
    echo "  Syncing skeleton..."
    mkdir -p "$skel_dst"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='snapshots/' \
        --exclude='obj_dir/' \
        --exclude='sim-build*/' \
        --exclude='*.log' \
        --exclude='*.xml' \
        --exclude='*.vcd' \
        --exclude='*.fst' \
        --exclude='verilator-build' \
        "$SOURCE/" "$skel_dst/"

    # Zero out all implementation files
    echo "  Zeroing implementation RTL..."
    for rel_path in "${IMPL_FILES[@]}"; do
        local abs_path="$skel_dst/$rel_path"
        if [ -f "$abs_path" ]; then
            > "$abs_path"
            echo "    zeroed: $rel_path"
        else
            echo "    WARNING: not found: $rel_path"
        fi
    done

    echo "  Done: $task_name"
}

# Sync both task variants
sync_task "veer-el2"
sync_task "veer-el2-block"

echo ""
echo "=== sync-skeleton.sh complete ==="
echo "Now rebuild Docker images:"
echo "  docker build -t veer-el2:latest veer-el2/environment/"
echo "  docker build -t veer-el2-block:latest veer-el2-block/environment/"
