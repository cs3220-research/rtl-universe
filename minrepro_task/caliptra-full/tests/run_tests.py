#!/usr/bin/env python3
"""
run_tests.py — Run L0 Verilator regression tests and count passes.

Reads test list from /tmp/_test_list.json (produced by parse_tests.py).
Writes pass count to /tmp/_passed_count.
Each test gets its own copy of the pre-built obj_dir.
"""
import json
import subprocess
import os
import shutil
import sys

caliptra_root = os.environ.get('CALIPTRA_ROOT', '/app')
build_dir = '/tmp/verifier_build'
scratch = '/tmp/caliptra_verifier_workspace/scratch/tests'
os.makedirs(scratch, exist_ok=True)

try:
    with open('/tmp/_test_list.json') as f:
        tests = json.load(f)
except FileNotFoundError:
    print("ERROR: /tmp/_test_list.json not found — run parse_tests.py first")
    sys.exit(1)

mfile = os.path.join(caliptra_root, 'tools/scripts/Makefile')
passed = []
failed = []

for i, t in enumerate(tests):
    suite = t['suite']
    yml_stem = t['yml_stem']
    testname = t['testname']
    plusargs = ' '.join(t['plusargs'])

    print(f"[{i+1}/{len(tests)}] Running: {yml_stem} ...", flush=True)

    # Create a fresh test directory by copying the pre-built Verilator binary
    testdir = os.path.join(scratch, yml_stem)
    if os.path.exists(testdir):
        shutil.rmtree(testdir)
    try:
        shutil.copytree(build_dir, testdir)
    except Exception as e:
        print(f"  ERROR copying build dir: {e}")
        failed.append(yml_stem)
        continue

    cmd = (
        f'make -C {testdir} -f {mfile} '
        f'TESTNAME={testname} '
        f'verilator VERILATOR_RUN_ARGS="{plusargs}"'
    )

    try:
        result = subprocess.run(
            cmd, shell=True, capture_output=True, text=True, timeout=300
        )
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        print(f"  TIMEOUT: {yml_stem}")
        failed.append(yml_stem)
        continue
    except Exception as e:
        print(f"  ERROR: {yml_stem}: {e}")
        failed.append(yml_stem)
        continue

    if "* TESTCASE PASSED" in output:
        passed.append(yml_stem)
        print(f"  PASS: {yml_stem}")
    else:
        failed.append(yml_stem)
        # Print last few lines of output for debugging
        lines = output.strip().split('\n')
        for line in lines[-5:]:
            print(f"    | {line}")
        print(f"  FAIL: {yml_stem} (exit={result.returncode})")

# Write results
print(f"\n{'='*60}")
print(f"Results: {len(passed)} passed, {len(failed)} failed out of {len(tests)} total")
print(f"{'='*60}")

if passed:
    print("PASSED:")
    for t in sorted(passed):
        print(f"  + {t}")

if failed:
    print("FAILED:")
    for t in sorted(failed):
        print(f"  - {t}")

with open('/tmp/_passed_count', 'w') as f:
    f.write(str(len(passed)) + '\n')

with open('/tmp/_passed_tests', 'w') as f:
    for t in sorted(passed):
        f.write(t + '\n')

with open('/tmp/_failed_tests', 'w') as f:
    for t in sorted(failed):
        f.write(t + '\n')

print(f"\nPassed count written to /tmp/_passed_count: {len(passed)}")
