#!/bin/bash
# count_tests.sh — Run all OpenPiton tests, but record only the E2E subset
# (LFSR + uart_serializer) as the scored denominator for this task variant.
#
# E2E scored tests: ifu_esl_lfsr (5) + uart_serializer (1) = 6 total
# Full test count is also computed but used only for informational purposes.
#
# Outputs (written to /tmp/):
#   _total        — integer: e2e test count (6) — this is the verifier denominator
#   _all_tests    — newline-separated list of all test IDs (18)
#   _e2e_targets  — newline-separated list of e2e test IDs (6)

set -eux

PITON=/app/piton
INFR=$PITON/verif/env/test_infrstrct
BUILD=/tmp/openpiton_count_build
mkdir -p "$BUILD"

# Initialize
: > /tmp/_all_tests
: > /tmp/_e2e_targets
PASS_ALL=0
TOTAL_ALL=0
PASS_E2E=0
TOTAL_E2E=0

# ---------------------------------------------------------------------------
# ifu_esl_counter (8 test cases) — not E2E scored
# ---------------------------------------------------------------------------
COUNTER_TB=$PITON/verif/env/ifu_esl_counter
COUNTER_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v

if iverilog -g2001 -I "$INFR" \
    "$COUNTER_DSN" \
    "$INFR/test_source.v" "$INFR/test_sink.v" "$INFR/test_infrstrct_fifo.v" \
    "$COUNTER_TB/ifu_esl_counter_top.v" \
    -o "$BUILD/counter.vvp" 2>"$BUILD/counter_compile.log"; then

    for tc in test_clear test_clear_set test_pause test_set \
              test_step test_step_clear test_step_clear_set test_step_set; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
        timeout 60 vvp "$BUILD/counter.vvp" \
            "+test_cases_path=${COUNTER_TB}/test_cases/" \
            "+test_case=${tc}" \
            > "$BUILD/counter_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/counter_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/counter_${tc}.log" 2>/dev/null; then
            PASS_ALL=$((PASS_ALL + 1))
            echo "ifu_esl_counter/$tc" >> /tmp/_all_tests
        fi
    done
else
    echo "ifu_esl_counter: COMPILE FAILED"
    for tc in test_clear test_clear_set test_pause test_set \
              test_step test_step_clear test_step_clear_set test_step_set; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
    done
fi

# ---------------------------------------------------------------------------
# ifu_esl_lfsr (5 test cases) — E2E SCORED
# ---------------------------------------------------------------------------
LFSR_TB=$PITON/verif/env/ifu_esl_lfsr
LFSR_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v

if iverilog -g2001 -I "$INFR" \
    "$LFSR_DSN" \
    "$INFR/test_source.v" "$INFR/test_sink.v" "$INFR/test_infrstrct_fifo.v" \
    "$LFSR_TB/ifu_esl_lfsr_top.v" \
    -o "$BUILD/lfsr.vvp" 2>"$BUILD/lfsr_compile.log"; then

    for tc in test_exhaust test_ldstep test_pause test_seed test_state0; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
        TOTAL_E2E=$((TOTAL_E2E + 1))
        echo "ifu_esl_lfsr/$tc" >> /tmp/_e2e_targets
        timeout 60 vvp "$BUILD/lfsr.vvp" \
            "+test_cases_path=${LFSR_TB}/test_cases/" \
            "+test_case=${tc}" \
            > "$BUILD/lfsr_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/lfsr_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/lfsr_${tc}.log" 2>/dev/null; then
            PASS_ALL=$((PASS_ALL + 1))
            PASS_E2E=$((PASS_E2E + 1))
            echo "ifu_esl_lfsr/$tc" >> /tmp/_all_tests
        fi
    done
else
    echo "ifu_esl_lfsr: COMPILE FAILED"
    for tc in test_exhaust test_ldstep test_pause test_seed test_state0; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
        TOTAL_E2E=$((TOTAL_E2E + 1))
        echo "ifu_esl_lfsr/$tc" >> /tmp/_e2e_targets
    done
fi

# ---------------------------------------------------------------------------
# ifu_esl_shiftreg (4 test cases) — not E2E scored
# ---------------------------------------------------------------------------
SHIFT_TB=$PITON/verif/env/ifu_esl_shiftreg
SHIFT_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v

if iverilog -g2001 -I "$INFR" \
    "$SHIFT_DSN" \
    "$INFR/test_source.v" "$INFR/test_sink.v" "$INFR/test_infrstrct_fifo.v" \
    "$SHIFT_TB/ifu_esl_shiftreg_top.v" \
    -o "$BUILD/shiftreg.vvp" 2>"$BUILD/shiftreg_compile.log"; then

    for tc in test_pause test_set test_set_shift test_shift; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
        timeout 60 vvp "$BUILD/shiftreg.vvp" \
            "+test_cases_path=${SHIFT_TB}/test_cases/" \
            "+test_case=${tc}" \
            > "$BUILD/shiftreg_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/shiftreg_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/shiftreg_${tc}.log" 2>/dev/null; then
            PASS_ALL=$((PASS_ALL + 1))
            echo "ifu_esl_shiftreg/$tc" >> /tmp/_all_tests
        fi
    done
else
    echo "ifu_esl_shiftreg: COMPILE FAILED"
    for tc in test_pause test_set test_set_shift test_shift; do
        TOTAL_ALL=$((TOTAL_ALL + 1))
    done
fi

# ---------------------------------------------------------------------------
# uart_serializer (1 test case) — E2E SCORED
# ---------------------------------------------------------------------------
UART_TB=$PITON/verif/env/uart_serializer
UART_DSN=$PITON/design/common/uart_pkttrace_dump/rtl/uart_serializer.v

TOTAL_ALL=$((TOTAL_ALL + 1))
TOTAL_E2E=$((TOTAL_E2E + 1))
echo "uart_serializer/test" >> /tmp/_e2e_targets

if iverilog -g2001 -I "$INFR" \
    "$UART_DSN" \
    "$INFR/test_source.v" "$INFR/test_sink.v" "$INFR/test_infrstrct_fifo.v" \
    "$UART_TB/uart_serializer_top.v" \
    -o "$BUILD/uart.vvp" 2>"$BUILD/uart_compile.log"; then

    timeout 60 vvp "$BUILD/uart.vvp" \
        "+test_cases_path=${UART_TB}/test_cases/" \
        "+test_case=test" \
        > "$BUILD/uart_test.log" 2>&1 || true
    if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/uart_test.log" 2>/dev/null && \
       grep -q "Entering Test Suite" "$BUILD/uart_test.log" 2>/dev/null; then
        PASS_ALL=$((PASS_ALL + 1))
        PASS_E2E=$((PASS_E2E + 1))
        echo "uart_serializer/test" >> /tmp/_all_tests
    fi
else
    echo "uart_serializer: COMPILE FAILED"
    cat "$BUILD/uart_compile.log"
fi

# ---------------------------------------------------------------------------
# Write outputs
# ---------------------------------------------------------------------------
echo ""
echo "Count complete: all_total=$TOTAL_ALL all_pass=$PASS_ALL"
echo "               e2e_total=$TOTAL_E2E e2e_pass=$PASS_E2E"

# For this E2E variant, _total is the E2E subset size (6)
echo "$TOTAL_E2E" > /tmp/_total
echo "$PASS_E2E"  > /tmp/_passed

echo "Outputs written: _total ($TOTAL_E2E), _all_tests, _e2e_targets"
