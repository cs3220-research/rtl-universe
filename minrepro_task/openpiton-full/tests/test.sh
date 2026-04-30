#!/bin/bash
# Verifier for the openpiton Harbor task (all-tests mode).
# Runs all 18 Icarus Verilog tests against the agent's /app.
# Writes a proportional reward in [0, 1] to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# ---------------------------------------------------------------------------
# Denominator: total tests captured at image build time.
# ---------------------------------------------------------------------------
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=18
fi

PASS=0
BUILD=/tmp/openpiton_verif_build
mkdir -p "$BUILD"

PITON=/app/piton
INFR=$PITON/verif/env/test_infrstrct

# Helper: compile + run one testbench against one test case.
# Args: tb_name  sim_bin  tc_dir  tc_stem
run_tc() {
    local label="$1"
    local sim="$2"
    local tc_dir="$3"
    local tc_stem="$4"

    # A test PASSES when:
    # - vvp doesn't timeout (exit code 0 or non-zero from $finish)
    # - No "[FAILED]" or "HIT BAD TRAP" appears in the output
    # (At VERBOSITY=0, passing tests don't print PASSED — only failures print)
    timeout 60 vvp "$sim" \
        "+test_cases_path=${tc_dir}/" \
        "+test_case=${tc_stem}" \
        > "$BUILD/${label}.log" 2>&1 || true
    if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/${label}.log" 2>/dev/null && \
       grep -q "Entering Test Suite" "$BUILD/${label}.log" 2>/dev/null; then
        echo "[PASS] $label" | tee -a /logs/verifier/all.log
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $label" | tee -a /logs/verifier/all.log
        if [ -f "$BUILD/${label}.log" ]; then
            tail -5 "$BUILD/${label}.log" >> /logs/verifier/all.log
        fi
    fi
}

# ---------------------------------------------------------------------------
# ifu_esl_counter (8 test cases)
# ---------------------------------------------------------------------------
echo "=== ifu_esl_counter ===" | tee -a /logs/verifier/all.log
COUNTER_TB=$PITON/verif/env/ifu_esl_counter
COUNTER_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v
COUNTER_SIM=$BUILD/ifu_esl_counter.vvp

if iverilog -g2001 \
    -I "$INFR" \
    "$COUNTER_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$COUNTER_TB/ifu_esl_counter_top.v" \
    -o "$COUNTER_SIM" 2>>"$BUILD/ifu_esl_counter_compile.log"; then

    for tc in test_clear test_clear_set test_pause test_set \
              test_step test_step_clear test_step_clear_set test_step_set; do
        run_tc "counter_${tc}" "$COUNTER_SIM" "$COUNTER_TB/test_cases" "$tc"
    done
else
    echo "[COMPILE FAIL] ifu_esl_counter — skipping 8 test cases" \
        | tee -a /logs/verifier/all.log
    cat "$BUILD/ifu_esl_counter_compile.log" >> /logs/verifier/all.log
fi

# ---------------------------------------------------------------------------
# ifu_esl_lfsr (5 test cases)
# ---------------------------------------------------------------------------
echo "=== ifu_esl_lfsr ===" | tee -a /logs/verifier/all.log
LFSR_TB=$PITON/verif/env/ifu_esl_lfsr
LFSR_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v
LFSR_SIM=$BUILD/ifu_esl_lfsr.vvp

if iverilog -g2001 \
    -I "$INFR" \
    "$LFSR_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$LFSR_TB/ifu_esl_lfsr_top.v" \
    -o "$LFSR_SIM" 2>>"$BUILD/ifu_esl_lfsr_compile.log"; then

    for tc in test_exhaust test_ldstep test_pause test_seed test_state0; do
        run_tc "lfsr_${tc}" "$LFSR_SIM" "$LFSR_TB/test_cases" "$tc"
    done
else
    echo "[COMPILE FAIL] ifu_esl_lfsr — skipping 5 test cases" \
        | tee -a /logs/verifier/all.log
    cat "$BUILD/ifu_esl_lfsr_compile.log" >> /logs/verifier/all.log
fi

# ---------------------------------------------------------------------------
# ifu_esl_shiftreg (4 test cases)
# ---------------------------------------------------------------------------
echo "=== ifu_esl_shiftreg ===" | tee -a /logs/verifier/all.log
SHIFT_TB=$PITON/verif/env/ifu_esl_shiftreg
SHIFT_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v
SHIFT_SIM=$BUILD/ifu_esl_shiftreg.vvp

if iverilog -g2001 \
    -I "$INFR" \
    "$SHIFT_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$SHIFT_TB/ifu_esl_shiftreg_top.v" \
    -o "$SHIFT_SIM" 2>>"$BUILD/ifu_esl_shiftreg_compile.log"; then

    for tc in test_pause test_set test_set_shift test_shift; do
        run_tc "shiftreg_${tc}" "$SHIFT_SIM" "$SHIFT_TB/test_cases" "$tc"
    done
else
    echo "[COMPILE FAIL] ifu_esl_shiftreg — skipping 4 test cases" \
        | tee -a /logs/verifier/all.log
    cat "$BUILD/ifu_esl_shiftreg_compile.log" >> /logs/verifier/all.log
fi

# ---------------------------------------------------------------------------
# uart_serializer (1 test case)
# ---------------------------------------------------------------------------
echo "=== uart_serializer ===" | tee -a /logs/verifier/all.log
UART_TB=$PITON/verif/env/uart_serializer
UART_DSN=$PITON/design/common/uart_pkttrace_dump/rtl/uart_serializer.v
UART_SIM=$BUILD/uart_serializer.vvp

if iverilog -g2001 \
    -I "$INFR" \
    "$UART_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$UART_TB/uart_serializer_top.v" \
    -o "$UART_SIM" 2>>"$BUILD/uart_serializer_compile.log"; then

    run_tc "uart_test" "$UART_SIM" "$UART_TB/test_cases" "test"
else
    echo "[COMPILE FAIL] uart_serializer — skipping 1 test case" \
        | tee -a /logs/verifier/all.log
    cat "$BUILD/uart_serializer_compile.log" >> /logs/verifier/all.log
fi

# ---------------------------------------------------------------------------
# Compute and write reward
# ---------------------------------------------------------------------------
echo "" | tee -a /logs/verifier/all.log
echo "Total: passed=${PASS} / total=${TOTAL}" | tee -a /logs/verifier/all.log

if [ "${TOTAL:-0}" -gt 0 ]; then
    python3 -c "
passed = int('${PASS}')
total  = int('${TOTAL}')
reward = min(1.0, passed / total)
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt
else
    echo "0.000000" > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASS}, total=${TOTAL})"
