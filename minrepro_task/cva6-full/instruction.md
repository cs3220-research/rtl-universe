# CVA6 RISC-V Core — Restore the RTL Implementation

You are in `/app`, a **Make + Bender + Verilator** repository for the
**CVA6 (Ariane) RISC-V processor core** — a full 6-stage, out-of-order
capable, application-class RISC-V core developed at ETH Zurich and
supported by OpenHW Group.

## Repository Structure

```
Makefile                   — Top-level build system entry point
Bender.yml                 — PULP dependency manager manifest
core/                      — Core RTL implementation (STRIPPED)
  cva6.sv                  — Top-level core module
  frontend/                — Instruction fetch, branch predictor
  id_stage.sv              — Instruction decode
  issue_stage.sv           — Issue and operand read
  ex_stage.sv              — Execute stage
  commit_stage.sv          — Commit stage
  alu.sv                   — Integer ALU
  multiplier.sv            — Multiplication unit (mul/div)
  load_unit.sv             — Load unit
  store_unit.sv            — Store unit
  csr_regfile.sv           — CSR register file
  cache_subsystem/         — Data and instruction cache
  cvfpu/                   — Floating-point unit (submodule)
  include/                 — Package files and configuration
    config_pkg.sv          — Core configuration (KEEP — contains parameters)
    ariane_pkg.sv          — Core types and constants
    cv64a6_imafdc_sv39_config_pkg.sv  — Default target config
corev_apu/                 — SoC-level peripherals and testbench
  tb/                      — Testbench infrastructure (KEPT)
    ariane_tb.cpp          — Verilator C++ main testbench driver (KEPT)
    ariane_testharness.sv  — SoC testbench harness (KEPT)
    dpi/                   — DPI helpers for simulation (KEPT)
  riscv-dbg/               — Debug module (submodule, KEPT)
vendor/                    — Vendored dependencies (KEPT)
  pulp-platform/           — AXI, common cells, etc.
common/                    — Shared utilities (KEPT)
ci/                        — CI test lists and scripts (KEPT)
  riscv-asm-tests.list     — 110 ASM ISA test names
  riscv-amo-tests.list     — 38 AMO ISA test names
  riscv-mul-tests.list     — 26 MUL ISA test names
  riscv-fp-tests.list      — 46 FP ISA test names
  riscv-benchmarks.list    — 8 benchmark test names
```

## What Has Been Stripped

All **RTL implementation files** under `core/` have been emptied
(zero bytes). Package files, configuration files, testbenches, build
scripts, and all vendored dependencies are intact.

**Stripped** (you must implement):
- All `core/*.sv` modules: ALU, multiplier, load/store units, CSR file,
  branch predictor, decode, issue, execute, commit stages, frontend, MMU
- All `core/cache_subsystem/*.sv` modules (write-through and writeback caches)
- All `core/pmp/*.sv` modules (physical memory protection)
- All `core/frontend/*.sv` modules (fetch, branch prediction)
- `corev_apu/bootrom/bootrom.sv` — boot ROM

**Kept** (do not modify):
- All `core/include/*.sv` — package files and configuration
- All `corev_apu/tb/` — testbench harness, C++ driver, DPI
- All `vendor/` — AXI, common_cells, tech_cells_generic
- All `corev_apu/riscv-dbg/` — RISC-V debug module
- All `ci/` — test lists and check scripts
- `Makefile`, `Bender.yml`, `verilator_config.vlt`
- All `core/cvfpu/` — floating-point unit (git submodule, pre-populated)
- All `core/cache_subsystem/hpdcache/` — HPD cache (git submodule)

## Scoring

Reward is **proportional**: `passed_tests / total_tests`.

The verifier:
1. Runs `make verilate` to build the Verilator simulation binary
2. Runs 228 RISC-V ISA tests and benchmarks through the simulation
3. Awards 1 point for each test that prints `SUCCESS` in simulation output

Each test that passes earns partial credit — you do not need to pass
everything to get a non-zero reward.

**Total baseline: 228 tests**
- 110 integer ISA tests (rv64ui, rv64mi, rv64si, rv64uc)
- 38 atomic AMO tests (rv64ua)
- 26 multiply/divide tests (rv64um)
- 46 floating-point tests (rv64uf, rv64ud)
- 8 benchmark programs (dhrystone, median, multiply, qsort, rsort, towers, vvadd, pmp)

## Environment

- **Verilator 5.008** pre-installed at `/opt/verilator/bin/verilator`
- **RISC-V GNU toolchain** pre-installed at `/opt/riscv/bin/`
- **Spike ISS libraries** pre-installed at `/opt/spike/lib/`
  (`libriscv.so`, `libfesvr.so`, `libdisasm.so`, `libyaml-cpp.so`)
- **riscv-tests binaries** pre-installed at `/opt/riscv-tests/`
  (symlinked to `/app/tmp/riscv-tests`)
- **Bender** pre-installed at `/usr/local/bin/bender`
- `/app` is a git repository — commit freely

Key environment variables needed by the Makefile:
```bash
export RISCV=/opt/riscv
export SPIKE_INSTALL_DIR=/opt/spike
export VERILATOR_INSTALL_DIR=/opt/verilator
export VL_INC_DIR=/opt/verilator/share/verilator/include
export CVA6_REPO_DIR=/app
export TARGET_CFG=cv64a6_imafdc_sv39
export HPDCACHE_DIR=/app/core/cache_subsystem/hpdcache
```

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
mkdir -p /app/tmp
ln -sf /opt/riscv-tests /app/tmp/riscv-tests

# Build the Verilator simulation (takes ~10-15 min)
make verilate

# Run a single test manually
./work-ver/Variane_testharness +max-cycles=10000000 \
    /opt/riscv-tests/share/riscv-tests/isa/rv64ui-p-add

# Run all ASM tests
make run-asm-tests-verilator

# Lint check individual module
verilator --lint-only -sv \
    -I/app/core/include \
    -I/app/vendor/pulp-platform/common_cells/include \
    -I/app/vendor/pulp-platform/axi/include \
    /app/core/include/riscv_pkg.sv \
    /app/core/include/ariane_pkg.sv \
    /app/core/alu.sv \
    --top-module alu -Wno-fatal

# Fetch Bender dependencies
bender checkout

# Check the Bender file list
bender script flist -t cv64a6_imafdc_sv39 | head -20

# View the core architecture
cat README.md
cat docs/01_cva6_user/CVA6_softcore.rst 2>/dev/null || cat README.md
```

## Implementation Guidance

CVA6 is a 6-stage RISC-V core: **IF → ID → IS → EX → WB → COM**

Key modules and their roles:
- `frontend/` — Instruction fetch, PC generation, branch predictor (BTB, BHT, RAS)
- `id_stage.sv` — Instruction decode, immediate extraction, scoreboard entry creation
- `issue_stage.sv` — Issue queue, operand forwarding, register file read
- `ex_stage.sv` — Execution units (ALU, branch, load/store, multiplier, FPU, CSR)
- `commit_stage.sv` — In-order commit, exception handling, writeback
- `csr_regfile.sv` — Control and status registers (mstatus, mtvec, etc.)
- `cache_subsystem/` — Write-through or writeback D-cache and I-cache

Start with simpler functional units (ALU, multiplier, compressed decoder) and
work toward the pipeline stages (decode, issue, execute, commit).

The `core/include/` directory contains all type definitions — study
`ariane_pkg.sv` carefully for the scoreboard entry, exception, and
instruction types used throughout the pipeline.

Reference the `vendor/pulp-platform/common_cells/` library for FIFOs,
arbiters, and other primitives (lzc, fifo_v3, rr_arb_tree, etc.) — they
are pre-compiled and available via Bender.
