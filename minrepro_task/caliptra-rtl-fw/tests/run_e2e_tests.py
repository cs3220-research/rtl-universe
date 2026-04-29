#!/usr/bin/env python3
"""
run_e2e_tests.py — Run the E2E subset for the verifier.

Runs the same E2E test list used at build time (from /app/.harbor/e2e_tests
if present, otherwise uses the built-in list).

Writes:
  /tmp/_passed_count  — integer count of passing tests
  /tmp/_passed_tests  — newline-separated list of passing test names
  /tmp/_failed_tests  — newline-separated list of failing test names
"""
import os
import shutil
import subprocess
import sys

CALIPTRA_ROOT = os.environ.get("CALIPTRA_ROOT", "/app")
BUILD_DIR = "/tmp/verifier_build"
SCRATCH = "/tmp/caliptra_verifier_workspace/scratch/e2e_tests"
TEST_TIMEOUT = 300

# Built-in E2E test list (mirrors run_e2e_tests.py from the count stage)
BUILTIN_E2E_TESTS = [
    "smoke_test_sha256",
    "smoke_test_sha512",
    "smoke_test_hmac",
    "smoke_test_sha3",
    "smoke_test_aes_gcm",
    "smoke_test_kv",
    "smoke_test_kv_hmac_flow",
    "smoke_test_kv_doe",
    "smoke_test_kv_uds_reset",
    "smoke_test_mbox",
    "smoke_test_trng",
    "smoke_test_datavault_basic",
    "smoke_test_zeroize_crypto",
    "smoke_test_mldsa_edge",
    "smoke_test_mlkem",
]

os.makedirs(SCRATCH, exist_ok=True)

# Load test list from .harbor/e2e_tests if available
e2e_tests_path = "/app/.harbor/e2e_tests"
if os.path.exists(e2e_tests_path):
    with open(e2e_tests_path) as fh:
        test_names = [line.strip() for line in fh if line.strip()]
    print(f"Loaded {len(test_names)} E2E tests from {e2e_tests_path}", file=sys.stderr)
else:
    test_names = BUILTIN_E2E_TESTS
    print(f"Using built-in list of {len(test_names)} E2E tests", file=sys.stderr)

mfile = os.path.join(CALIPTRA_ROOT, "tools/scripts/Makefile")
passed = []
failed = []

for i, testname in enumerate(test_names):
    yml_stem = testname
    plusargs = "+CLP_REGRESSION"
    print(f"[{i+1}/{len(test_names)}] Running: {yml_stem} ...", flush=True)

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
print(f"E2E results: {len(passed)} passed, {len(failed)} failed out of {len(test_names)} total")
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
