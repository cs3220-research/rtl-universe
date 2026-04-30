# CVA6 RISC-V Core — E2E Integration Test Variant

You are in `/app`, a **Make + Bender + Verilator** repository for the
**CVA6 (Ariane) RISC-V processor core**.

This is the **E2E (end-to-end) variant** of the CVA6 task. It scores only
the most fundamental integration tests: **bare-metal ISA tests that execute
directly in machine mode** with no virtual memory paging. Passing any of
these tests requires the entire pipeline to be functional.

## E2E Test Subset (~79 tests)

The scored tests are:
- `rv64ui-p-*` — User integer instructions, physical (bare-metal) mode (46 tests)
- `rv64mi-p-*` — Machine-mode privileged instructions (7 tests)
- `rv64si-p-*` — Supervisor-mode instructions (6 tests)
- `rv64uc-p-*` — Compressed instructions, bare-metal (1 test)
- `rv64ua-p-*` — Atomic (AMO) instructions, bare-metal (19 tests)

These tests are in the `ci/riscv-asm-tests.list` and `ci/riscv-amo-tests.list` files.
The verifier filters for the `*-p-*` (physical/bare-metal) variants automatically.

For the full 228-test benchmark (includes virtual memory, FP, MUL, benchmarks),
see the **cva6** task.

## Repository Structure

```
Makefile                   — Top-level build system
Bender.yml                 — PULP dependency manager
core/                      — Core RTL (STRIPPED — you must implement)
  cva6.sv                  — Top-level core
  frontend/                — Fetch, branch predictor
  id_stage.sv              — Decode
  issue_stage.sv           — Issue and operand read
  ex_stage.sv              — Execute
  commit_stage.sv          — Commit
  alu.sv                   — ALU
  multiplier.sv            — Mul/div unit
  load_unit.sv / store_unit.sv
  csr_regfile.sv           — CSR registers
  cache_subsystem/         — Caches
  pmp/                     — Physical memory protection
  include/                 — Package files (KEPT)
corev_apu/tb/              — Testbench (KEPT)
vendor/                    — Vendored libs (KEPT)
ci/                        — Test lists (KEPT)
```

## What Has Been Stripped

All RTL implementation files under `core/` have been emptied (zero bytes).
Package files, testbenches, and vendor libraries are intact.

See the `cva6` task `instruction.md` for the full stripped/kept listing.


## Important: Partial Credit and Persistence

You are scored **proportionally** — every single test you get to pass earns
credit. You do NOT need to complete the entire project. Even restoring a
handful of modules that pass their unit tests is valuable progress.

**Do not give up or stop early because the task looks large.** Work
incrementally: pick a module, read its test spec, implement it, verify it
passes, then move to the next one. You have up to 24 hours. Use all of it.
The best strategy is to start with the simplest, most self-contained modules
and work outward toward integration.

## Scoring

Reward = `passed_e2e_tests / total_e2e_tests`.

Even passing a single basic test like `rv64ui-p-add` (adds two registers and
checks the result) earns partial credit. You need a working fetch-decode-execute
pipeline for the integer tests to pass.

## Environment

- **Verilator 5.008** at `/opt/verilator/bin/verilator`
- **RISC-V GNU toolchain** at `/opt/riscv/bin/`
- **Spike ISS libraries** at `/opt/spike/lib/`
- **riscv-tests binaries** at `/opt/riscv-tests/` (symlinked to `/app/tmp/riscv-tests`)
- **Bender** at `/usr/local/bin/bender`

## Useful Commands

```bash
# Set up environment
export RISCV=/opt/riscv
export SPIKE_INSTALL_DIR=/opt/spike
export VERILATOR_INSTALL_DIR=/opt/verilator
export VL_INC_DIR=/opt/verilator/share/verilator/include
export CVA6_REPO_DIR=/app
export TARGET_CFG=cv64a6_imafdc_sv39
export HPDCACHE_DIR=/app/core/cache_subsystem/hpdcache
export PATH=/opt/riscv/bin:/opt/verilator/bin:$PATH
export LD_LIBRARY_PATH=/opt/riscv/lib:/opt/spike/lib:$LD_LIBRARY_PATH

# Ensure riscv-tests are accessible
mkdir -p /app/tmp && ln -sf /opt/riscv-tests /app/tmp/riscv-tests

# Build the Verilator simulation
make verilate

# Run a single E2E test (simplest: integer add)
./work-ver/Variane_testharness +max-cycles=10000000 \
    /opt/riscv-tests/share/riscv-tests/isa/rv64ui-p-add

# A passing test prints "SUCCESS" — check with:
./work-ver/Variane_testharness +max-cycles=10000000 \
    /opt/riscv-tests/share/riscv-tests/isa/rv64ui-p-add 2>&1 | grep -i "success\|fail"

# List all E2E test ELFs
ls /opt/riscv-tests/share/riscv-tests/isa/rv64ui-p-*
ls /opt/riscv-tests/share/riscv-tests/isa/rv64mi-p-*
ls /opt/riscv-tests/share/riscv-tests/isa/rv64ua-p-*
```

## Implementation Strategy

The `rv64ui-p-add` test is the simplest: it loads two registers, adds them,
and writes the result to the tohost CSR. This exercises:
1. Instruction fetch from the boot ROM
2. Instruction decode (R-type and I-type)
3. Register file read
4. ALU (addition)
5. Register file write
6. CSR write (tohost)

Start with the ALU and basic integer datapath, then add decode, issue, and
commit logic. The pipeline can be simplified initially (in-order, no branch
prediction) to get basic tests passing quickly.

Reference `core/include/ariane_pkg.sv` for all type definitions.
Reference `core/include/cv64a6_imafdc_sv39_config_pkg.sv` for parameters.
