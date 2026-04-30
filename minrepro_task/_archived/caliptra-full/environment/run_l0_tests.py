#!/usr/bin/env python3
"""
run_l0_tests.py — Run the L0 Verilator regression tests and record pass/fail.

Reads /tmp/_test_list.json (produced by parse_l0_tests.py).
Each test gets a fresh copy of the pre-built Verilator binary from build_dir.
Writes results to:
  /tmp/_all_tests     — newline-separated list of passing test names
  /tmp/_failed_tests  — newline-separated list of failing test names
  /tmp/_total         — count of passing tests (denominator for reward)
  /tmp/_passed_count  — same as _total (used by verifier)
"""
import json
import os
import shutil
import subprocess
import sys

CALIPTRA_ROOT = os.environ.get("CALIPTRA_ROOT", "/app")
BUILD_DIR = os.environ.get("BUILD_DIR", "/tmp/verilated_image")
SCRATCH = "/tmp/caliptra_workspace/scratch/tests"
TEST_TIMEOUT = int(os.environ.get("TEST_TIMEOUT", "300"))

os.makedirs(SCRATCH, exist_ok=True)

test_list_path = "/tmp/_test_list.json"
try:
    with open(test_list_path) as fh:
        tests = json.load(fh)
except FileNotFoundError:
    print(f"ERROR: {test_list_path} not found — run parse_l0_tests.py first")
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

    # Fresh test directory — copy pre-built Verilator binary
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

# Write result files
with open("/tmp/_all_tests", "w") as fh:
    for name in sorted(passed):
        fh.write(name + "\n")

with open("/tmp/_failed_tests", "w") as fh:
    for name in sorted(failed):
        fh.write(name + "\n")

with open("/tmp/_total", "w") as fh:
    fh.write(str(len(passed)) + "\n")

with open("/tmp/_passed_count", "w") as fh:
    fh.write(str(len(passed)) + "\n")

print(f"\nPassed count written to /tmp/_total: {len(passed)}")
