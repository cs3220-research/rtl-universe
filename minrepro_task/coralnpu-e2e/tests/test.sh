#!/bin/bash
# Verifier entrypoint for the coralnpu-e2e Harbor task.
# Runs the E2E subset of bazel tests against the agent's /app and writes a
# proportional reward in [0, 1] to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# Baseline E2E target list captured from the green source at image build.
BASELINE=/app/.harbor/e2e_targets
if [ ! -r "$BASELINE" ]; then
  echo "error: $BASELINE missing — task image is malformed" >&2
  echo 0 > /logs/verifier/reward.txt
  exit 0
fi

TOTAL=$(wc -l < "$BASELINE")
if [ "$TOTAL" -eq 0 ]; then
  echo "error: no e2e targets in baseline" >&2
  echo 0 > /logs/verifier/reward.txt
  exit 0
fi

# Pass the baseline targets to bazel. Targets that no longer build under the
# agent's code just count as failures against the fixed denominator.
mapfile -t targets < "$BASELINE"
bazel test "${targets[@]}" --keep_going --test_output=errors \
  --build_event_text_file=/logs/verifier/bep.txt \
  2>&1 | tee /logs/verifier/bazel.log

PASSED=$(grep -cE "^//[^ ]+ +(\(cached\) )?PASSED in " /logs/verifier/bazel.log || true)

python3 -c "print(f'{$PASSED/$TOTAL:.6f}')" > /logs/verifier/reward.txt
echo "reward: $(cat /logs/verifier/reward.txt)  (passed=$PASSED, total=$TOTAL)"

if command -v uvx >/dev/null 2>&1; then
  uvx --with pytest==8.4.1 --with pytest-json-ctrf==0.3.5 \
    pytest --ctrf /logs/verifier/ctrf.json /tests/test_state.py -rA || true
fi
