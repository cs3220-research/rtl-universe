#!/bin/bash
# Verifier entrypoint for the coralnpu all-tests Harbor task.
# Runs `bazel test //...` against the agent's /app and writes a proportional
# reward in [0, 1] to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# Denominator: total test targets the fresh skeleton discovers.
if [ -r /app/.harbor/total_tests ]; then
  TOTAL=$(cat /app/.harbor/total_tests)
else
  TOTAL=$(bazel query 'kind(".*_test", //...)' 2>/dev/null | wc -l)
fi

# Run the full test suite, keep going through failures, capture full log.
bazel test //... --keep_going --test_output=errors \
  --build_event_text_file=/logs/verifier/bep.txt \
  2>&1 | tee /logs/verifier/bazel.log

# Count passing targets. bazel emits one line per target in the summary:
#   //pkg:target   PASSED in 12.3s
PASSED=$(grep -cE "^//[^ ]+ +(\(cached\) )?PASSED in " /logs/verifier/bazel.log || true)

if [ "${TOTAL:-0}" -gt 0 ]; then
  python3 -c "print(f'{$PASSED/$TOTAL:.6f}')" > /logs/verifier/reward.txt
else
  echo 0 > /logs/verifier/reward.txt
fi

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=$PASSED, total=$TOTAL)"

# Pytest for structured reporting; non-fatal.
if command -v uvx >/dev/null 2>&1; then
  uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
    pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
