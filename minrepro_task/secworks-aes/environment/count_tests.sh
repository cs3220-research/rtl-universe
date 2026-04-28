#!/bin/bash
# count_tests.sh — Run all testbenches on the green source and record pass count.
# Called during Docker build (count stage). Writes:
#   /tmp/_total     — total individual test cases across all testbenches
#   /tmp/_all_tests — newline-separated list of testbench target names
set -eux
cd /app

git init -q
git add -A
git -c user.email=x@x -c user.name=x commit -q -m init

# Register the core with FuseSoC
fusesoc library add aes /app

TARGETS="tb_aes tb_aes_core tb_aes_key_mem tb_aes_encipher_block tb_aes_decipher_block"

# Run all testbench targets and capture logs
for tgt in $TARGETS; do
    fusesoc run --target="$tgt" secworks:crypto:aes 2>&1 | tee "/tmp/${tgt}.log" || true
done

# Count total test cases from the summary lines.
# Each testbench prints one of:
#   "*** All NN test cases completed successfully"
#   "*** NN tests completed - MM test cases did not complete successfully."
TOTAL=0
for tgt in $TARGETS; do
    log="/tmp/${tgt}.log"
    tc=0
    if grep -q "All [0-9]* test cases completed" "$log" 2>/dev/null; then
        tc=$(grep -o "All [0-9]* test" "$log" | grep -o "[0-9]*" | head -1 || echo 0)
    elif grep -q "tests completed -" "$log" 2>/dev/null; then
        tc=$(grep -o "\*\*\* [0-9]* tests" "$log" | grep -o "[0-9]*" | head -1 || echo 0)
    fi
    [ -z "$tc" ] && tc=0
    TOTAL=$((TOTAL + tc))
    echo "  $tgt: tc=$tc"
done

echo "$TOTAL" > /tmp/_total
printf '%s\n' $TARGETS > /tmp/_all_tests
echo "count_tests: total=$TOTAL"
