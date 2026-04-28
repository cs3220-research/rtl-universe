#!/bin/bash
# Verifier for the pulp-common-cells Harbor task.
# Runs per-module Verilator lint checks and ECC functional tests against
# the agent's /app workspace and computes a proportional reward.
# Writes reward (float in [0, 1]) to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# ── Denominator ───────────────────────────────────────────────────────────────
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=67
fi

SRCDIR="/app/src"
INCDIR="/app/include"

# tech_cells_generic primitives — fetched by bender checkout in the image
TCG_CLK=$(ls .bender/git/checkouts/tech_cells_generic-*/src/rtl/tc_clk.sv 2>/dev/null | head -1 || true)
TCG_PULP=$(ls .bender/git/checkouts/tech_cells_generic-*/src/deprecated/pulp_clk_cells.sv 2>/dev/null | head -1 || true)

# All source files in dependency order
ALL_SRCS=()
[ -n "$TCG_CLK"  ] && ALL_SRCS+=("$TCG_CLK")
[ -n "$TCG_PULP" ] && ALL_SRCS+=("$TCG_PULP")

ALL_SRCS+=(
  "$SRCDIR/cb_filter_pkg.sv"
  "$SRCDIR/cdc_reset_ctrlr_pkg.sv"
  "$SRCDIR/cf_math_pkg.sv"
  "$SRCDIR/ecc_pkg.sv"
  "$SRCDIR/stream_intf.sv"
  "$SRCDIR/binary_to_gray.sv"
  "$SRCDIR/cc_onehot.sv"
  "$SRCDIR/clk_int_div.sv"
  "$SRCDIR/credit_counter.sv"
  "$SRCDIR/delta_counter.sv"
  "$SRCDIR/edge_propagator_tx.sv"
  "$SRCDIR/exp_backoff.sv"
  "$SRCDIR/fifo_v3.sv"
  "$SRCDIR/gray_to_binary.sv"
  "$SRCDIR/heaviside.sv"
  "$SRCDIR/isochronous_4phase_handshake.sv"
  "$SRCDIR/isochronous_spill_register.sv"
  "$SRCDIR/lfsr.sv"
  "$SRCDIR/lfsr_16bit.sv"
  "$SRCDIR/lfsr_8bit.sv"
  "$SRCDIR/lossy_valid_to_stream.sv"
  "$SRCDIR/mv_filter.sv"
  "$SRCDIR/onehot_to_bin.sv"
  "$SRCDIR/plru_tree.sv"
  "$SRCDIR/passthrough_stream_fifo.sv"
  "$SRCDIR/popcount.sv"
  "$SRCDIR/ring_buffer.sv"
  "$SRCDIR/rr_arb_tree.sv"
  "$SRCDIR/rstgen_bypass.sv"
  "$SRCDIR/serial_deglitch.sv"
  "$SRCDIR/shift_reg.sv"
  "$SRCDIR/shift_reg_gated.sv"
  "$SRCDIR/spill_register_flushable.sv"
  "$SRCDIR/stream_demux.sv"
  "$SRCDIR/stream_filter.sv"
  "$SRCDIR/stream_fork.sv"
  "$SRCDIR/stream_join_dynamic.sv"
  "$SRCDIR/stream_mux.sv"
  "$SRCDIR/stream_throttle.sv"
  "$SRCDIR/sub_per_hash.sv"
  "$SRCDIR/sync.sv"
  "$SRCDIR/sync_wedge.sv"
  "$SRCDIR/unread.sv"
  "$SRCDIR/read.sv"
  "$SRCDIR/addr_decode_dync.sv"
  "$SRCDIR/boxcar.sv"
  "$SRCDIR/cdc_2phase.sv"
  "$SRCDIR/cdc_4phase.sv"
  "$SRCDIR/clk_int_div_static.sv"
  "$SRCDIR/trip_counter.sv"
  "$SRCDIR/addr_decode.sv"
  "$SRCDIR/addr_decode_napot.sv"
  "$SRCDIR/multiaddr_decode.sv"
  "$SRCDIR/cb_filter.sv"
  "$SRCDIR/cdc_fifo_2phase.sv"
  "$SRCDIR/clk_mux_glitch_free.sv"
  "$SRCDIR/counter.sv"
  "$SRCDIR/ecc_decode.sv"
  "$SRCDIR/ecc_encode.sv"
  "$SRCDIR/edge_detect.sv"
  "$SRCDIR/lzc.sv"
  "$SRCDIR/max_counter.sv"
  "$SRCDIR/rstgen.sv"
  "$SRCDIR/spill_register.sv"
  "$SRCDIR/stream_delay.sv"
  "$SRCDIR/stream_fifo.sv"
  "$SRCDIR/stream_fork_dynamic.sv"
  "$SRCDIR/stream_join.sv"
  "$SRCDIR/cdc_reset_ctrlr.sv"
  "$SRCDIR/cdc_fifo_gray.sv"
  "$SRCDIR/fall_through_register.sv"
  "$SRCDIR/id_queue.sv"
  "$SRCDIR/stream_to_mem.sv"
  "$SRCDIR/stream_arbiter_flushable.sv"
  "$SRCDIR/stream_fifo_optimal_wrap.sv"
  "$SRCDIR/stream_register.sv"
  "$SRCDIR/stream_xbar.sv"
  "$SRCDIR/cdc_fifo_gray_clearable.sv"
  "$SRCDIR/cdc_2phase_clearable.sv"
  "$SRCDIR/mem_to_banks_detailed.sv"
  "$SRCDIR/stream_arbiter.sv"
  "$SRCDIR/stream_omega_net.sv"
  "$SRCDIR/mem_to_banks.sv"
)

# Modules to test (excludes packages/interfaces and Verilator-buggy modules)
MODULES=(
  binary_to_gray gray_to_binary cc_onehot clk_int_div credit_counter delta_counter
  edge_propagator_tx exp_backoff fifo_v3 heaviside isochronous_4phase_handshake
  isochronous_spill_register lfsr lfsr_16bit lfsr_8bit lossy_valid_to_stream mv_filter
  onehot_to_bin plru_tree passthrough_stream_fifo popcount ring_buffer rr_arb_tree
  rstgen_bypass serial_deglitch shift_reg shift_reg_gated spill_register_flushable
  stream_demux stream_filter stream_fork stream_join_dynamic stream_mux stream_throttle
  sub_per_hash sync sync_wedge unread read boxcar cdc_2phase cdc_4phase
  clk_int_div_static trip_counter addr_decode addr_decode_napot cb_filter cdc_fifo_2phase
  clk_mux_glitch_free counter ecc_decode ecc_encode edge_detect lzc max_counter rstgen
  spill_register stream_delay stream_fifo stream_fork_dynamic stream_join cdc_reset_ctrlr
  cdc_fifo_gray fall_through_register id_queue stream_to_mem stream_arbiter_flushable
  stream_fifo_optimal_wrap stream_register stream_xbar cdc_fifo_gray_clearable
  cdc_2phase_clearable stream_arbiter stream_omega_net mem_to_banks
)

PASSED=0
{
echo "=== Per-module Verilator lint tests ==="

for mod in "${MODULES[@]}"; do
  test_name="lint::${mod}"
  output=$(verilator --lint-only -sv -I"$INCDIR" "${ALL_SRCS[@]}" \
    --top-module "$mod" -Wno-fatal --unroll-count 2048 2>&1 || true)

  real_errors=$(echo "$output" | grep "^%Error:" | \
    grep -v "^%Error: Exiting due to [0-9]* warning" | wc -l)
  internal_errors=$(echo "$output" | grep -c "Internal Error\|internal fault\|OOPS" || true)
  total_errors=$((real_errors + internal_errors))

  if [ "$total_errors" -eq 0 ]; then
    echo "PASS: $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL: $test_name"
    echo "$output" | grep "^%Error:\|Internal Error" | head -3
  fi
done

echo ""
echo "=== ECC Functional Tests ==="

for ecc_mod in ecc_encode ecc_decode; do
  test_name="functional::${ecc_mod}"
  build_dir="/tmp/verilator_${ecc_mod}_verify"
  mkdir -p "$build_dir"

  verilator --cc -sv -I"$INCDIR" \
    "$SRCDIR/ecc_pkg.sv" \
    "$SRCDIR/${ecc_mod}.sv" \
    "test/ecc/ecc.cpp" \
    "test/ecc/${ecc_mod}.cpp" \
    --top-module "$ecc_mod" --trace --exe \
    -Mdir "$build_dir" 2>&1 | tee "/logs/verifier/${ecc_mod}_verilate.log" || true

  make -C "$build_dir" -f "V${ecc_mod}.mk" 2>&1 | tee "/logs/verifier/${ecc_mod}_build.log" || true

  binary="$build_dir/V${ecc_mod}"
  if [ -x "$binary" ]; then
    "$binary" > /logs/verifier/${ecc_mod}_run.log 2>&1
    if [ $? -eq 0 ]; then
      echo "PASS: $test_name"
      PASSED=$((PASSED + 1))
    else
      echo "FAIL: $test_name (non-zero exit)"
    fi
  else
    echo "FAIL: $test_name (binary not built)"
  fi
done

echo ""
echo "=== Summary ==="
echo "passed=$PASSED total=$TOTAL"
} 2>&1 | tee /logs/verifier/test.log

# ── Compute reward ────────────────────────────────────────────────────────────
python3 -c "
passed = int('${PASSED}')
total  = int('${TOTAL}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
