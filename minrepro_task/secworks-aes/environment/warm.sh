#!/bin/bash
set -eux
cd /app

git init -q
git add -A
git -c user.email=x@x -c user.name=x commit -q -m init

fusesoc library add aes /app

TARGETS="tb_aes tb_aes_core tb_aes_key_mem tb_aes_encipher_block tb_aes_decipher_block"

# Run all testbench targets
for tgt in $TARGETS; do
    fusesoc run --target="$tgt" secworks:crypto:aes 2>&1 | tee "/tmp/${tgt}.log" || true
done

# Count passing test cases from logs
# Pattern: "*** All NN test cases completed successfully"
# or:      "*** NN tests completed - MM test cases did not complete successfully."
TOTAL=0
PASS=0
for tgt in $TARGETS; do
    log="/tmp/${tgt}.log"
    tc=0; err=0
    if grep -q "All [0-9]* test cases completed" "$log" 2>/dev/null; then
        tc=$(grep -o "All [0-9]* test" "$log" | grep -o "[0-9]*")
        err=0
    elif grep -q "tests completed -" "$log" 2>/dev/null; then
        tc=$(grep -o "\*\*\* [0-9]* tests" "$log" | grep -o "[0-9]*" || echo 0)
        err=$(grep -o "[0-9]* test cases did not" "$log" | grep -o "^[0-9]*" || echo 0)
    fi
    [ -z "$tc" ] && tc=0
    [ -z "$err" ] && err=0
    passed=$((tc - err))
    TOTAL=$((TOTAL + tc))
    PASS=$((PASS + passed))
    echo "  $tgt: tc=$tc err=$err passed=$passed"
done

echo "$TOTAL" > /tmp/_total
echo "$PASS" > /tmp/_passed
printf '%s\n' $TARGETS > /tmp/_all_tests
echo "Warm stage complete. total=$TOTAL pass=$PASS"
