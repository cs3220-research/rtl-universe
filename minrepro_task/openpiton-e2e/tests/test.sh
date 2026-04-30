#!/bin/bash
# Verifier for the openpiton Harbor task (e2e mode).
# Runs only the integration-level tests: ifu_esl_lfsr (5) + uart_serializer (1) = 6 tests.
# Writes a proportional reward in [0, 1] to /logs/verifier/reward.txt.
#
# E2E rationale: LFSR and UART serializer exercise complete algorithmic pipelines
# (pseudo-random bit generation with polynomial feedback; multi-byte packet
# serialization with flow control). Counter and shift register are simpler
# sub-components and are excluded from this E2E subset.

set -u
mkdir -p /logs/verifier
cd /app

# ---------------------------------------------------------------------------
# Denominator
# ---------------------------------------------------------------------------
if [ -r /app/.harbor/e2e_targets ]; then
    TOTAL=$(wc -l < /app/.harbor/e2e_targets)
else
    TOTAL=6
fi

PASS=0
BUILD=/tmp/openpiton_e2e_build
mkdir -p "$BUILD"

PITON=/app/piton
INFR=$PITON/verif/env/test_infrstrct

run_tc() {
    local label="$1"
    local sim="$2"
    local tc_dir="$3"
    local tc_stem="$4"

    # A test PASSES when no "[FAILED]" or "HIT BAD TRAP" appears in output.
    # At VERBOSITY=0, passing tests don't print PASSED — only failures print.
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
