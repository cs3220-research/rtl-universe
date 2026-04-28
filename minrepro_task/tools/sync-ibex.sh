#!/usr/bin/env bash
# sync-skeleton.sh — Regenerate task environments from the canonical ibex source.
#
# Usage: ./tools/sync-skeleton.sh [ibex-source-dir]
#
# Positional argument: path to the ibex git checkout (default: ../../../../ibex)
#
# What it does:
#   1. Copies the full ibex repo to environment/warm_src/ (green source).
#   2. Copies the full ibex repo to environment/skeleton/ then strips
#      implementation files (empties RTL and simple_system_common.c).
#
# Run this before building any Docker image.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="$(dirname "$SCRIPT_DIR")"
IBEX_SRC="${1:-$(cd "$SCRIPT_DIR/../../../../.." && pwd)/ibex}"

if [ ! -d "$IBEX_SRC" ]; then
    echo "error: ibex source not found at '$IBEX_SRC'" >&2
    echo "       usage: $0 [ibex-source-dir]" >&2
    exit 1
fi

echo "ibex source: $IBEX_SRC"
echo "outputs dir: $OUTPUTS_DIR"

# Files to strip: RTL implementation + simple_system SV + common SW
RTL_IMPL_FILES=(
    rtl/ibex_pkg.sv
    rtl/ibex_alu.sv
    rtl/ibex_branch_predict.sv
    rtl/ibex_compressed_decoder.sv
    rtl/ibex_controller.sv
    rtl/ibex_counter.sv
    rtl/ibex_cs_registers.sv
    rtl/ibex_csr.sv
    rtl/ibex_decoder.sv
    rtl/ibex_dummy_instr.sv
    rtl/ibex_ex_block.sv
    rtl/ibex_fetch_fifo.sv
    rtl/ibex_icache.sv
    rtl/ibex_id_stage.sv
    rtl/ibex_if_stage.sv
    rtl/ibex_load_store_unit.sv
    rtl/ibex_lockstep.sv
    rtl/ibex_multdiv_fast.sv
    rtl/ibex_multdiv_slow.sv
    rtl/ibex_pmp.sv
    rtl/ibex_prefetch_buffer.sv
    rtl/ibex_register_file_ff.sv
    rtl/ibex_register_file_fpga.sv
    rtl/ibex_register_file_latch.sv
    rtl/ibex_top.sv
    rtl/ibex_top_tracing.sv
    rtl/ibex_tracer.sv
    rtl/ibex_tracer_pkg.sv
    rtl/ibex_wb_stage.sv
    examples/simple_system/rtl/ibex_simple_system.sv
    examples/sw/simple_system/common/simple_system_common.c
)

sync_task() {
    local task_name="$1"
    echo ""
    echo "=== Syncing task: $task_name ==="

    local env_dir="$OUTPUTS_DIR/$task_name/environment"
    local warm_dst="$env_dir/warm_src"
    local skel_dst="$env_dir/skeleton"

    # ---- 1. Populate warm_src (full green source) ----
    echo "  Populating warm_src..."
    mkdir -p "$warm_dst"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='build/' \
        --exclude='*.pyc' \
        --exclude='__pycache__/' \
        "$IBEX_SRC/" "$warm_dst/"

    # ---- 2. Populate skeleton (stripped source) ----
    echo "  Populating skeleton..."
    mkdir -p "$skel_dst"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='build/' \
        --exclude='*.pyc' \
        --exclude='__pycache__/' \
        "$IBEX_SRC/" "$skel_dst/"

    # Truncate (zero out) each implementation file
    for f in "${RTL_IMPL_FILES[@]}"; do
        local dst="$skel_dst/$f"
        if [ -f "$dst" ]; then
            > "$dst"
            echo "  stripped: $f"
        else
            echo "  WARNING: not found in skeleton: $f"
        fi
    done

    echo "  Done: $task_name"
}

sync_task "ibex"
sync_task "ibex-e2e"

echo ""
echo "sync-skeleton.sh complete."
echo "Next steps:"
echo "  docker build -t ibex-task:latest $OUTPUTS_DIR/ibex/environment/"
echo "  docker build -t ibex-e2e-task:latest $OUTPUTS_DIR/ibex-e2e/environment/"
