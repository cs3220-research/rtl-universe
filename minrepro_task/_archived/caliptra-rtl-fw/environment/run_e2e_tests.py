#!/usr/bin/env python3
"""
run_e2e_tests.py — Run the E2E subset of Caliptra L0 tests for the count stage.

The E2E subset selects tests that exercise multiple major subsystems together:
  - Tests requiring crypto accelerators + key vault + firmware + VeeR core
  - Tests that would fail if any major block is missing
  - Excludes simple CPU-only tests (memCpy, hello_world, iccm_lock, c_intr)
  - Excludes tests skipped by Verilator (clk_gating, dma, kv_cg, doe_cg, mbox_cg)

Writes:
  /tmp/_all_tests    — passing E2E test names (one per line)
  /tmp/_e2e_tests    — same (canonical E2E list)
  /tmp/_failed_tests — failing E2E test names
  /tmp/_total        — count of passing tests (denominator)
  /tmp/_passed_count — same as _total
"""
import os
import shutil
import subprocess
import sys

CALIPTRA_ROOT = os.environ.get("CALIPTRA_ROOT", "/app")
BUILD_DIR = os.environ.get("BUILD_DIR", "/tmp/verilated_image")
SCRATCH = "/tmp/caliptra_workspace/scratch/e2e_tests"
TEST_TIMEOUT = 300

# E2E test subset: tests that exercise the full integrated Caliptra design.
# These require VeeR core + cryptographic accelerators + SoC interface + key
# vault all working together. Ordered roughly by complexity.
E2E_TESTS = [
    # Core crypto block tests (require full SoC boot + crypto accelerator)
    ("smoke_test_sha256",        "+CLP_REGRESSION"),
    ("smoke_test_sha512",        "+CLP_REGRESSION"),
    ("smoke_test_hmac",          "+CLP_REGRESSION"),
    ("smoke_test_sha3",          "+CLP_REGRESSION"),
    ("smoke_test_aes_gcm",       "+CLP_REGRESSION"),
    # Key vault flow tests (require crypto + keyvault + SoC interface)
    ("smoke_test_kv",            "+CLP_REGRESSION"),
    ("smoke_test_kv_hmac_flow",  "+CLP_REGRESSION"),
    ("smoke_test_kv_doe",        "+CLP_REGRESSION"),
    ("smoke_test_kv_uds_reset",  "+CLP_REGRESSION"),
    # Full system integration (mailbox, trng, datavault)
    ("smoke_test_mbox",          "+CLP_REGRESSION"),
    ("smoke_test_trng",          "+CLP_REGRESSION"),
    ("smoke_test_datavault_basic", "+CLP_REGRESSION"),
    # Multi-block zeroize (exercises entire crypto subsystem)
    ("smoke_test_zeroize_crypto", "+CLP_REGRESSION"),
    # MLDSA / MLKEM (require adams-bridge submodule RTL)
    ("smoke_test_mldsa_edge",    "+CLP_REGRESSION"),
    ("smoke_test_mlkem",         "+CLP_REGRESSION"),
]

os.makedirs(SCRATCH, exist_ok=True)

mfile = os.path.join(CALIPTRA_ROOT, "tools/scripts/Makefile")
passed = []
failed = []

for i, (testname, plusargs) in enumerate(E2E_TESTS):
    yml_stem = testname
    print(f"[{i+1}/{len(E2E_TESTS)}] Running: {yml_stem} ...", flush=True)

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
print(f"E2E results: {len(passed)} passed, {len(failed)} failed out of {len(E2E_TESTS)} total")
print(f"{'='*60}")

with open("/tmp/_all_tests", "w") as fh:
    for name in sorted(passed):
        fh.write(name + "\n")

with open("/tmp/_e2e_tests", "w") as fh:
    for name, _ in E2E_TESTS:
        fh.write(name + "\n")

with open("/tmp/_failed_tests", "w") as fh:
    for name in sorted(failed):
        fh.write(name + "\n")

with open("/tmp/_total", "w") as fh:
    fh.write(str(len(passed)) + "\n")

with open("/tmp/_passed_count", "w") as fh:
    fh.write(str(len(passed)) + "\n")

print(f"Passed count written to /tmp/_total: {len(passed)}")
