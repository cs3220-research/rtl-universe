# Caliptra Root of Trust — Restore RTL and Firmware

You are in `/app`, the **Caliptra Root of Trust** RTL repository — a
hardware security module (HSM) providing cryptographic attestation for
SoC boot flows.

This is the **harder variant** of the caliptra-rtl task: both the
RTL implementation **and** the firmware test C source files have been stripped.

## What has been stripped

**RTL implementation** (same as caliptra-rtl task):
- All `.sv` / `.v` implementation files in `src/*/rtl/` and `submodules/*/rtl/`
  (state machines, datapaths, controllers for every IP block)

**Firmware test source files**:
- All `*.c` files in `src/integration/test_suites/<test_name>/` (one per test)
- Example: `src/integration/test_suites/smoke_test_sha256/smoke_test_sha256.c`

**Kept intact** (do NOT modify):
- All `.yml` test descriptor files (they describe the test name and plusargs)
- All firmware library files in `src/integration/test_suites/libs/` (printf,
  caliptra_isr, sha256, hmac, etc.) — these are shared support libraries
- All testbench `.sv` files, `.vf` filelists, `.h` header files
- The Makefile (`tools/scripts/Makefile`)
- The submodules directory

## Architecture of a firmware-driven test

Each test in `src/integration/test_suites/<name>/` contains:
- `<name>.c` — RISC-V C program that runs on the VeeR EL2 core
- `<name>.yml` — test descriptor (testname, optional plusargs)
- Optional `caliptra_isr.h`, `<name>.ld` (custom linker script)

The C program:
1. Drives the Caliptra hardware via MMIO registers
2. Uses shared libraries in `libs/` (sha256, hmac, ecc, etc.) to compute
   expected results
3. Writes to `STDOUT` (a special MMIO address) to signal pass/fail
4. The testbench reads STDOUT and emits `* TESTCASE PASSED` on success

The `src/integration/rtl/caliptra_reg/caliptra_reg.h` header defines
all register addresses. The `src/integration/test_suites/includes/`
directory has shared defines. Look at `src/integration/test_suites/libs/`
for the helper libraries with their `.h` and `.c` files.

## Scoring

Reward is **proportional**: `passed_tests / total_tests` (float in [0, 1]).
The denominator is the same as caliptra-rtl (53 L0 tests).

## Key commands

```bash
export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic

# Rebuild Verilator binary after RTL changes
mkdir -p /tmp/build_dir
make -C /tmp/build_dir -f $CALIPTRA_ROOT/tools/scripts/Makefile verilator-build

# Run a single test (firmware must be compiled separately first)
mkdir -p /tmp/run_smoke_test_sha256
make -C /tmp/run_smoke_test_sha256 \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    TESTNAME=smoke_test_sha256 \
    verilator VERILATOR_RUN_ARGS=+CLP_REGRESSION

# Look at a reference firmware structure
cat src/integration/test_suites/iccm_lock/iccm_lock.c   # simple test
cat src/integration/test_suites/smoke_test_sha256/smoke_test_sha256.c  # (stripped)

# Look at shared libs for APIs
ls src/integration/test_suites/libs/
cat src/integration/test_suites/libs/sha256/sha256.h
cat src/integration/test_suites/libs/sha256/sha256.c

# Check register addresses
cat src/integration/rtl/caliptra_reg/caliptra_reg.h | grep SHA256

# Test list
cat src/integration/stimulus/L0_regression.yml
```

## Architecture overview

See `README.md` and `docs/` for the full Caliptra specification.
The `src/integration/test_suites/includes/caliptra_defines.h` file has
key MMIO address definitions.
