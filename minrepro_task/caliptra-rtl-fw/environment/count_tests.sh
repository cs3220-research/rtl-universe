#!/bin/bash
# count_tests.sh — Run the L0 regression on green source and record the pass count.
# Called during Docker build (Stage 2).  Output: /tmp/_total, /tmp/_all_tests
set -euo pipefail

export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic
export CALIPTRA_WORKSPACE=/tmp/caliptra_workspace
mkdir -p "${CALIPTRA_WORKSPACE}/scratch"

# ── Step 1: Build the Verilator simulation binary ──────────────────────────
BUILD_DIR=/tmp/verilated_image
mkdir -p "${BUILD_DIR}"

echo "[count_tests] Building Verilator simulation binary..."
make -C "${BUILD_DIR}" -f "${CALIPTRA_ROOT}/tools/scripts/Makefile" \
    verilator-build \
    2>&1 | tail -20
echo "[count_tests] Verilator build complete."

# ── Step 2: Parse the L0 regression YAML for test names ───────────────────
# Get tests from L0_regression.yml, excluding clock-gating tests that are
# skipped in Verilator (match the run_verilator_l0_regression.py filter).
python3 - <<'PYEOF'
import yaml, re, json, os

caliptra_root = os.environ['CALIPTRA_ROOT']
regress_file = f"{caliptra_root}/src/integration/stimulus/L0_regression.yml"

with open(regress_file) as f:
    data = yaml.load(f, Loader=yaml.FullLoader)

skip_pattern = re.compile(
    r'(smoke_test_clk_gating|smoke_test_cg_wdt|smoke_test_mbox_cg|'
    r'smoke_test_kv_cg|smoke_test_doe_cg|smoke_test_dma\b|smoke_test_wdt_rst)'
)

tests = []
for item in data.get("contents", []):
    for key, val in item.items():
        for path in val.get("paths", []):
            m = re.search(r'\.\./test_suites/(\S+)/(\S+)\.yml', path)
            if not m:
                continue
            suite = m.group(1)
            yml_stem = m.group(2)
            if skip_pattern.search(suite):
                continue
            tests.append({"suite": suite, "yml": yml_stem})

with open('/tmp/_test_list.json', 'w') as f:
    json.dump(tests, f, indent=2)

print(f"[count_tests] Found {len(tests)} tests to run")
PYEOF

# ── Step 3: Run each test and count passes ─────────────────────────────────
echo "[count_tests] Running regression tests..."
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=$(python3 -c "import json; tests=json.load(open('/tmp/_test_list.json')); print(len(tests))")

echo "${TOTAL_COUNT}" > /tmp/_total

python3 - <<'PYEOF'
import json, subprocess, os, shutil, re

caliptra_root = os.environ['CALIPTRA_ROOT']
build_dir = '/tmp/verilated_image'
scratch = '/tmp/caliptra_workspace/scratch/tests'
os.makedirs(scratch, exist_ok=True)

with open('/tmp/_test_list.json') as f:
    tests = json.load(f)

passed = []
failed = []

for t in tests:
    suite = t['suite']
    yml_stem = t['yml']
    testname = suite  # Makefile uses suite name as TESTNAME

    # Determine PLUS_ARGS from yml file
    yml_path = f"{caliptra_root}/src/integration/test_suites/{suite}/{yml_stem}.yml"
    plusargs = "+CLP_REGRESSION"
    try:
        import yaml
        with open(yml_path) as yf:
            ydata = yaml.load(yf, Loader=yaml.FullLoader)
        if ydata and ydata.get('plusargs'):
            plusargs += " " + " ".join(ydata['plusargs'])
    except Exception:
        pass

    # Create a fresh test directory (copy the verilated build)
    testdir = os.path.join(scratch, yml_stem)
    if os.path.exists(testdir):
        shutil.rmtree(testdir)
    shutil.copytree(build_dir, testdir)

    mfile = f"{caliptra_root}/tools/scripts/Makefile"
    cmd = (
        f"make -C {testdir} -f {mfile} "
        f"TESTNAME={testname} "
        f'verilator VERILATOR_RUN_ARGS="{plusargs}"'
    )

    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=300)
    output = result.stdout + result.stderr

    if "* TESTCASE PASSED" in output:
        passed.append(yml_stem)
        print(f"  PASS: {yml_stem}")
    else:
        failed.append(yml_stem)
        print(f"  FAIL: {yml_stem} (exit={result.returncode})")

print(f"\n[count_tests] Results: {len(passed)} passed, {len(failed)} failed")

# Write results
with open('/tmp/_all_tests', 'w') as f:
    for t in sorted(passed):
        f.write(t + '\n')

with open('/tmp/_total', 'w') as f:
    f.write(str(len(passed)) + '\n')

with open('/tmp/_failed_tests', 'w') as f:
    for t in sorted(failed):
        f.write(t + '\n')
PYEOF

echo "[count_tests] Total passing tests: $(cat /tmp/_total)"
echo "[count_tests] Passing tests written to /tmp/_all_tests"
