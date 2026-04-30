#!/bin/bash
# count_tests.sh — Run all OpenPiton open-source tests from the green source
# and record the counts for scoring.
#
# Testbenches covered (plain Verilog-2001, Icarus Verilog, no preprocessor):
#   ifu_esl_counter  : 8 test cases  (sparc_ifu_esl_counter.v)
#   ifu_esl_lfsr     : 5 test cases  (sparc_ifu_esl_lfsr.v)
#   ifu_esl_shiftreg : 4 test cases  (sparc_ifu_esl_shiftreg.v)
#   uart_serializer  : 1 test case   (uart_serializer.v)
#
# Total: 18 tests
#
# Outputs (written to /tmp/):
#   _total        — integer: total test count
#   _all_tests    — newline-separated list of test IDs
#   _e2e_targets  — newline-separated list of integration-level tests
#
# E2E definition: tests that exercise a composite module that integrates
# multiple sub-components (ifu_esl integrates counter+lfsr+shiftreg+fsm).
# For this repo, the uart_serializer test is the best standalone integration
# test (serializes multi-byte packets through a stateful datapath).

set -eux

PITON=/app/piton
INFR=$PITON/verif/env/test_infrstrct
BUILD=/tmp/openpiton_build
mkdir -p "$BUILD"

PASS=0
TOTAL=0

run_testbench() {
    local name="$1"          # friendly test ID
    local tb_dir="$2"        # testbench directory (contains *_top.v)
    local tb_top_v="$3"      # top-level testbench filename
    local tb_toplevel="$4"   # top-level module name
    local design_v="$5"      # design implementation file(s) (space-separated)
    local tc_dir="$6"        # test_cases directory
    local tc_stem="$7"       # test case stem (no _src/_sink suffix)

    echo "=== Running: $name ==="

    local sim="$BUILD/${name}.vvp"
    local compile_ok=0

    # Compile
    # shellcheck disable=SC2086
    if iverilog -g2001 \
        -I "$INFR" \
        $design_v \
        "$INFR/test_source.v" \
        "$INFR/test_sink.v" \
        "$INFR/test_infrstrct_fifo.v" \
        "$tb_dir/$tb_top_v" \
        -o "$sim" 2>"$BUILD/${name}_compile.log"; then
        compile_ok=1
    else
        echo "  COMPILE FAILED:"
        cat "$BUILD/${name}_compile.log"
    fi

    if [ "$compile_ok" -eq 0 ]; then
        echo "$name: FAILED (compile error)"
        TOTAL=$((TOTAL + 1))
        return
    fi

    # Run the simulation (at VERBOSITY=0 passing tests don't print PASSED)
    timeout 120 vvp "$sim" \
        "+test_cases_path=${tc_dir}/" \
        "+test_case=${tc_stem}" \
        > "$BUILD/${name}.log" 2>&1 || true

    if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/${name}.log" 2>/dev/null && \
       grep -q "Entering Test Suite" "$BUILD/${name}.log" 2>/dev/null; then
        echo "$name: PASSED"
        PASS=$((PASS + 1))
    else
        echo "$name: FAILED ($result)"
        if [ -f "$BUILD/${name}.log" ]; then
            tail -10 "$BUILD/${name}.log"
        fi
    fi
    TOTAL=$((TOTAL + 1))
}

# ---------------------------------------------------------------------------
# ifu_esl_counter tests (8 test cases)
# ---------------------------------------------------------------------------
COUNTER_TB=$PITON/verif/env/ifu_esl_counter
COUNTER_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v
COUNTER_TCS=$COUNTER_TB/test_cases

COUNTER_CASES="test_clear test_clear_set test_pause test_set test_step test_step_clear test_step_clear_set test_step_set"

# Compile counter sim once for all test cases
if iverilog -g2001 \
    -I "$INFR" \
    "$COUNTER_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$COUNTER_TB/ifu_esl_counter_top.v" \
    -o "$BUILD/ifu_esl_counter.vvp" 2>"$BUILD/ifu_esl_counter_compile.log"; then
    echo "ifu_esl_counter: compile OK"
    for tc in $COUNTER_CASES; do
        TOTAL=$((TOTAL + 1))
        echo "  Running counter/$tc ..."
        timeout 60 vvp "$BUILD/ifu_esl_counter.vvp" \
            "+test_cases_path=${COUNTER_TCS}/" \
            "+test_case=${tc}" \
            > "$BUILD/counter_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/counter_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/counter_${tc}.log" 2>/dev/null; then
            echo "  counter/$tc: PASSED"
            PASS=$((PASS + 1))
            echo "ifu_esl_counter/$tc" >> /tmp/_all_tests
        else
            echo "  counter/$tc: FAILED"
        fi
    done
else
    echo "ifu_esl_counter: COMPILE FAILED"
    cat "$BUILD/ifu_esl_counter_compile.log"
    for tc in $COUNTER_CASES; do
        TOTAL=$((TOTAL + 1))
    done
fi

# ---------------------------------------------------------------------------
# ifu_esl_lfsr tests (5 test cases)
# ---------------------------------------------------------------------------
LFSR_TB=$PITON/verif/env/ifu_esl_lfsr
LFSR_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v
LFSR_TCS=$LFSR_TB/test_cases

LFSR_CASES="test_exhaust test_ldstep test_pause test_seed test_state0"

if iverilog -g2001 \
    -I "$INFR" \
    "$LFSR_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$LFSR_TB/ifu_esl_lfsr_top.v" \
    -o "$BUILD/ifu_esl_lfsr.vvp" 2>"$BUILD/ifu_esl_lfsr_compile.log"; then
    echo "ifu_esl_lfsr: compile OK"
    for tc in $LFSR_CASES; do
        TOTAL=$((TOTAL + 1))
        echo "  Running lfsr/$tc ..."
        timeout 60 vvp "$BUILD/ifu_esl_lfsr.vvp" \
            "+test_cases_path=${LFSR_TCS}/" \
            "+test_case=${tc}" \
            > "$BUILD/lfsr_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/lfsr_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/lfsr_${tc}.log" 2>/dev/null; then
            echo "  lfsr/$tc: PASSED"
            PASS=$((PASS + 1))
            echo "ifu_esl_lfsr/$tc" >> /tmp/_all_tests
        else
            echo "  lfsr/$tc: FAILED"
        fi
    done
else
    echo "ifu_esl_lfsr: COMPILE FAILED"
    cat "$BUILD/ifu_esl_lfsr_compile.log"
    for tc in $LFSR_CASES; do
        TOTAL=$((TOTAL + 1))
    done
fi

# ---------------------------------------------------------------------------
# ifu_esl_shiftreg tests (4 test cases)
# ---------------------------------------------------------------------------
SHIFT_TB=$PITON/verif/env/ifu_esl_shiftreg
SHIFT_DSN=$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v
SHIFT_TCS=$SHIFT_TB/test_cases

SHIFT_CASES="test_pause test_set test_set_shift test_shift"

if iverilog -g2001 \
    -I "$INFR" \
    "$SHIFT_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$SHIFT_TB/ifu_esl_shiftreg_top.v" \
    -o "$BUILD/ifu_esl_shiftreg.vvp" 2>"$BUILD/ifu_esl_shiftreg_compile.log"; then
    echo "ifu_esl_shiftreg: compile OK"
    for tc in $SHIFT_CASES; do
        TOTAL=$((TOTAL + 1))
        echo "  Running shiftreg/$tc ..."
        timeout 60 vvp "$BUILD/ifu_esl_shiftreg.vvp" \
            "+test_cases_path=${SHIFT_TCS}/" \
            "+test_case=${tc}" \
            > "$BUILD/shiftreg_${tc}.log" 2>&1 || true
        if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/shiftreg_${tc}.log" 2>/dev/null && \
           grep -q "Entering Test Suite" "$BUILD/shiftreg_${tc}.log" 2>/dev/null; then
            echo "  shiftreg/$tc: PASSED"
            PASS=$((PASS + 1))
            echo "ifu_esl_shiftreg/$tc" >> /tmp/_all_tests
        else
            echo "  shiftreg/$tc: FAILED"
        fi
    done
else
    echo "ifu_esl_shiftreg: COMPILE FAILED"
    cat "$BUILD/ifu_esl_shiftreg_compile.log"
    for tc in $SHIFT_CASES; do
        TOTAL=$((TOTAL + 1))
    done
fi

# ---------------------------------------------------------------------------
# uart_serializer test (1 test case)
# ---------------------------------------------------------------------------
UART_TB=$PITON/verif/env/uart_serializer
UART_DSN=$PITON/design/common/uart_pkttrace_dump/rtl/uart_serializer.v
UART_TCS=$UART_TB/test_cases

if iverilog -g2001 \
    -I "$INFR" \
    "$UART_DSN" \
    "$INFR/test_source.v" \
    "$INFR/test_sink.v" \
    "$INFR/test_infrstrct_fifo.v" \
    "$UART_TB/uart_serializer_top.v" \
    -o "$BUILD/uart_serializer.vvp" 2>"$BUILD/uart_serializer_compile.log"; then
    echo "uart_serializer: compile OK"
    TOTAL=$((TOTAL + 1))
    tc="test"
    timeout 60 vvp "$BUILD/uart_serializer.vvp" \
        "+test_cases_path=${UART_TCS}/" \
        "+test_case=${tc}" \
        > "$BUILD/uart_${tc}.log" 2>&1 || true
    if ! grep -qE "FAILED|HIT BAD TRAP" "$BUILD/uart_${tc}.log" 2>/dev/null && \
       grep -q "Entering Test Suite" "$BUILD/uart_${tc}.log" 2>/dev/null; then
        echo "  uart_serializer/$tc: PASSED"
        PASS=$((PASS + 1))
        echo "uart_serializer/$tc" >> /tmp/_all_tests
    else
        echo "  uart_serializer/$tc: FAILED"
        if [ -f "$BUILD/uart_${tc}.log" ]; then
            tail -10 "$BUILD/uart_${tc}.log"
        fi
    fi
else
    echo "uart_serializer: COMPILE FAILED"
    cat "$BUILD/uart_serializer_compile.log"
    TOTAL=$((TOTAL + 1))
fi

# ---------------------------------------------------------------------------
# Write totals
# ---------------------------------------------------------------------------
echo ""
echo "Count stage complete: total=$TOTAL pass=$PASS"
echo "$TOTAL" > /tmp/_total
echo "$PASS"  > /tmp/_passed

# Ensure the all_tests file exists even if empty (it's appended above)
touch /tmp/_all_tests

# E2E targets: uart_serializer exercises a full multi-byte packet datapath
# (stateful, covers the complete serialization pipeline). This is the most
# "integration-like" of the 4 testbench families.
echo "uart_serializer/test" > /tmp/_e2e_targets

echo "Outputs written: _total, _passed, _all_tests, _e2e_targets"
