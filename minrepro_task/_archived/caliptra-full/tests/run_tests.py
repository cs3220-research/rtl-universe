#!/usr/bin/env python3
"""
run_tests.py — Run L0 Verilator regression tests for the verifier.

Reads /tmp/_test_list.json (produced by parse_tests.py).
Writes:
  /tmp/_passed_count  — integer count of passing tests
  /tmp/_passed_tests  — newline-separated list of passing test names
  /tmp/_failed_tests  — newline-separated list of failing test names
"""
import json
import os
import shutil
import subprocess
import sys

CALIPTRA_ROOT = os.environ.get("CALIPTRA_ROOT", "/app")
BUILD_DIR = "/tmp/verifier_build"
SCRATCH = "/tmp/caliptra_verifier_workspace/scratch/tests"
TEST_TIMEOUT = 300

os.makedirs(SCRATCH, exist_ok=True)

try:
    with open("/tmp/_test_list.json") as fh:
        tests = json.load(fh)
except FileNotFoundError:
    print("ERROR: /tmp/_test_list.json not found — run parse_tests.py first")
    sys.exit(1)

mfile = os.path.join(CALIPTRA_ROOT, "tools/scripts/Makefile")
passed = []
failed = []

for i, t in enumerate(tests):
    suite = t["suite"]
    yml_stem = t["yml_stem"]
    testname = t["testname"]
    plusargs = " ".join(t["plusargs"])

    print(f"[{i+1}/{len(tests)}] Running: {yml_stem} ...", flush=True)

    testdir = os.path.join(SCRATCH, yml_stem)
    if os.path.exists(testdir):
        shutil.rmtree(testdir)
    try:
        shutil.copytree(BUILD_DIR, testdir)
    except Exception as exc:
        print(f"  ERROR copying build dir: {exc}")
        failed.append(yml_stem)
        continue

    cmd = (
        f"make -C {testdir} -f {mfile} "
        f"TESTNAME={testname} "
        f'verilator VERILATOR_RUN_ARGS="{plusargs}"'
    )

    try:
        result = subprocess.run(
            cmd, shell=True, capture_output=True, text=True, timeout=TEST_TIMEOUT
        )
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        print(f"  TIMEOUT: {yml_stem}")
        failed.append(yml_stem)
        continue
    except Exception as exc:
        print(f"  ERROR: {yml_stem}: {exc}")
        failed.append(yml_stem)
        continue

    if "* TESTCASE PASSED" in output:
        passed.append(yml_stem)
        print(f"  PASS: {yml_stem}")
    else:
        failed.append(yml_stem)
        lines = output.strip().split("\n")
        for line in lines[-5:]:
            print(f"    | {line}")
        print(f"  FAIL: {yml_stem} (exit={result.returncode})")

print(f"\n{'='*60}")
print(f"Results: {len(passed)} passed, {len(failed)} failed out of {len(tests)} total")
print(f"{'='*60}")

if passed:
    print("PASSED:")
    for name in sorted(passed):
        print(f"  + {name}")

if failed:
    print("FAILED:")
    for name in sorted(failed):
        print(f"  - {name}")

with open("/tmp/_passed_count", "w") as fh:
    fh.write(str(len(passed)) + "\n")

with open("/tmp/_passed_tests", "w") as fh:
    for name in sorted(passed):
        fh.write(name + "\n")

with open("/tmp/_failed_tests", "w") as fh:
    for name in sorted(failed):
        fh.write(name + "\n")

print(f"\nPassed count written to /tmp/_passed_count: {len(passed)}")
