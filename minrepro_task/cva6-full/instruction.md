# CVA6 — Restore the RISC-V Core RTL

You are in `/app`, the root of the **OpenHW Group CVA6** (formerly Ariane) RISC-V
processor repository (Apache-2.0 / Solderpad-2.0). CVA6 is a Linux-capable,
6-stage, single-issue, in-order 64-bit core implementing RV64IMAFDC with Sv39
virtual memory. It runs full Linux and was taped out in 22 nm FDSOI.

See `README.md` and `docs/` for architecture documentation.

The **verification infrastructure is fully intact** but all synthesizable
implementation RTL has been removed (emptied to zero bytes). Your task is to
restore the RTL so that the Verilator-based test suite passes.

## What was stripped

All synthesizable SystemVerilog source files in `core/` have been zeroed out.
The files to restore are listed in `core/Flist.cva6`. Specifically:

**Core pipeline (core/):**
- `core/cva6.sv` — top-level core
- `core/alu.sv`, `core/alu_wrapper.sv` — arithmetic/logic unit
- `core/branch_unit.sv` — branch resolution
- `core/compressed_decoder.sv`, `core/macro_decoder.sv`, `core/zcmt_decoder.sv` — instruction decoders
- `core/controller.sv` — flush/stall controller
- `core/csr_buffer.sv`, `core/csr_regfile.sv` — CSR file
- `core/decoder.sv` — main instruction decoder
- `core/ex_stage.sv` — execute stage
- `core/id_stage.sv` — issue/dispatch stage
- `core/instr_realign.sv` — 16/32-bit instruction realigner
- `core/issue_read_operands.sv`, `core/issue_stage.sv` — scoreboard/issue
- `core/load_unit.sv`, `core/store_unit.sv`, `core/load_store_unit.sv`, `core/lsu_bypass.sv`
- `core/mult.sv`, `core/multiplier.sv`, `core/serdiv.sv` — M-extension
- `core/commit_stage.sv` — commit/retire
- `core/perf_counters.sv` — hardware performance monitors
- `core/ariane_regfile_ff.sv`, `core/ariane_regfile_fpga.sv` — register file
- `core/scoreboard.sv`, `core/raw_checker.sv` — hazard detection
- `core/amo_buffer.sv`, `core/axi_shim.sv` — AMO support
- `core/acc_dispatcher.sv` — accelerator dispatch
- `core/fpu_wrap.sv` — FPU wrapper (cvfpu integration)
- `core/aes.sv` — AES utility (for crypto extension)
- `core/cva6_accel_first_pass_decoder_stub.sv`
- `core/cva6_fifo_v3.sv`, `core/cva6_rvfi_probes.sv`, `core/cva6_rvfi.sv`
- `core/trigger_module.sv`

**Core/CVXIF (core/cvxif_example/):**
- `core/cvxif_example/cvxif_example_coprocessor.sv`
- `core/cvxif_example/instr_decoder.sv`
- `core/cvxif_example/compressed_instr_decoder.sv`
- `core/cvxif_example/copro_alu.sv`
- `core/cvxif_fu.sv`, `core/cvxif_compressed_if_driver.sv`
- `core/cvxif_issue_register_commit_if_driver.sv`

**Frontend (core/frontend/):**
- `core/frontend/frontend.sv` — IF stage + branch prediction glue
- `core/frontend/btb.sv` — branch target buffer
- `core/frontend/bht.sv`, `core/frontend/bht2lvl.sv` — branch history tables
- `core/frontend/ras.sv` — return address stack
- `core/frontend/instr_scan.sv`, `core/frontend/instr_queue.sv`

**Cache subsystem (core/cache_subsystem/):**
- `core/cache_subsystem/cva6_icache.sv`, `core/cache_subsystem/cva6_icache_axi_wrapper.sv`
- `core/cache_subsystem/wt_dcache.sv`, `core/cache_subsystem/wt_dcache_ctrl.sv`
- `core/cache_subsystem/wt_dcache_mem.sv`, `core/cache_subsystem/wt_dcache_missunit.sv`
- `core/cache_subsystem/wt_dcache_wbuffer.sv`, `core/cache_subsystem/wt_axi_adapter.sv`
- `core/cache_subsystem/wt_cache_subsystem.sv`
- `core/cache_subsystem/std_nbdcache.sv`, `core/cache_subsystem/std_cache_subsystem.sv`
- `core/cache_subsystem/cache_ctrl.sv`, `core/cache_subsystem/miss_handler.sv`
- `core/cache_subsystem/axi_adapter.sv`, `core/cache_subsystem/tag_cmp.sv`
- `core/cache_subsystem/amo_alu.sv`
- Various HPDcache files under `core/cache_subsystem/hpdcache/`

**MMU (core/cva6_mmu/):**
- `core/cva6_mmu/cva6_mmu.sv`, `core/cva6_mmu/cva6_ptw.sv`
- `core/cva6_mmu/cva6_tlb.sv`, `core/cva6_mmu/cva6_shared_tlb.sv`

**PMP (core/pmp/src/):**
- `core/pmp/src/pmp.sv`, `core/pmp/src/pmp_entry.sv`, `core/pmp/src/pmp_data_if.sv`

**What was kept** (do NOT remove or modify):
- All package/include files: `core/include/*.sv`, `core/include/*.svh`
- All testbench files: `corev_apu/tb/`, `verif/`
- All vendor library code: `vendor/pulp-platform/`
- The common utility modules: `common/`
- RISC-V test binaries at `tmp/riscv-tests/build/` (pre-built)
- `core/Flist.cva6` — the file list used by Verilator
- `core/cvfpu/` — vendored FPU IP (cvxif_fpnew); **do NOT modify**
- `core/cache_subsystem/hpdcache/` — vendored HPDcache IP; **do NOT modify**
- `corev_apu/riscv-dbg/`, `corev_apu/rv_plic/`, etc. — vendored corev_apu IP
- `Makefile`, `Bender.yml`, and all build infrastructure

## Environment

The following tools are pre-installed under `/tools/`:

- **Verilator** v5.008 (`/tools/verilator/bin/verilator`)
- **RISC-V GCC toolchain** (`/tools/riscv/bin/riscv64-unknown-elf-*`)
  - Configured for RV64IMAFDC with Linux ABI
- **Spike** ISS (built from vendored source) at `/tools/spike/`
- **Python 3** with riscv-dv dependencies
- **make**, **git**, **cmake**, **dtc**, **autoconf**

Environment variables set in the container:
```bash
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
export CVA6_REPO_DIR=/app
export NUM_JOBS=8
export TARGET_CFG=cv64a6_imafdc_sv39
```

## How tests are organized

### 1. Verilator model build

The Verilator model (`Variane_testharness`) is built from the top-level Makefile:

```bash
cd /app
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=8
# Produces: work-ver/Variane_testharness
```

### 2. CI regression tests (scored)

The CI test suites from `ci/` test lists run individual RISC-V test binaries
through the Verilator model. Tests exit via the `tohost` mechanism — success
means the binary writes `1` to `tohost` before hitting the max cycle limit.

**Test categories and counts:**
| Suite | List file | Count |
|-------|-----------|-------|
| ASM (integer) | `ci/riscv-asm-tests.list` | 110 |
| AMO (atomics) | `ci/riscv-amo-tests.list` | 38 |
| MUL (multiply) | `ci/riscv-mul-tests.list` | 26 |
| FP (floating point) | `ci/riscv-fp-tests.list` | 46 |
| Benchmarks | `ci/riscv-benchmarks.list` | 8 |
| **Total** | | **228** |

Run all tests via the Makefile Verilator targets:
```bash
cd /app
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike

# Build first (if not already done)
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=8

# Run a single test manually:
work-ver/Variane_testharness \
  tmp/riscv-tests/build/isa/rv64ui-p-add

# Run all ASM tests via make:
make run-asm-tests-verilator target=cv64a6_imafdc_sv39

# Run benchmarks:
make run-benchmarks-verilator target=cv64a6_imafdc_sv39

# Or use the CI check scripts:
make run-asm-tests-verilator run-amo-verilator run-mul-verilator run-fp-verilator run-benchmarks-verilator
```

### 3. RISCV-DV based tests (Spike comparison)

The `verif/sim/cva6.py` orchestrator compares Verilator execution traces against
Spike. These are invoked via regression scripts in `verif/regress/`:

```bash
cd /app
export DV_SIMULATORS=veri-testharness,spike

# Smoke test (a handful of tests to check basic correctness)
source verif/regress/smoke-tests-cv64a6_imafdc_sv39.sh

# Full RISC-V compliance
source verif/regress/dv-riscv-compliance.sh

# RISC-V tests
source verif/regress/dv-riscv-tests.sh
```

## Scoring

Reward is **proportional**: `passed_tests / total_tests` (float in `[0, 1]`).

The denominator is the number of CI test binaries (from `ci/*.list`) that
passed on the green source. It is stored in `/app/.harbor/total_tests`.

Partial credit is awarded: even restoring just the ALU and decode path
allows the integer ASM tests to pass, earning substantial partial credit.

## Useful commands

```bash
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
export NUM_JOBS=8

# Show which implementation files need restoring:
cat core/Flist.cva6

# Check package/include files (already intact — read-only reference):
ls core/include/
cat core/include/ariane_pkg.sv | head -100

# Build the Verilator model:
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=$NUM_JOBS

# Test a single binary:
work-ver/Variane_testharness tmp/riscv-tests/build/isa/rv64ui-p-add

# Run all CI test suites:
make run-asm-tests-verilator run-amo-verilator run-mul-verilator \
     run-fp-verilator run-benchmarks-verilator \
     target=cv64a6_imafdc_sv39

# Check test logs:
ls tmp/riscv-asm-tests-*.log | head -5
grep "PASS\|FAIL\|tohost" tmp/riscv-asm-tests-rv64ui-p-add.log | tail -5
```

## Architecture pointers

- **Top-level**: `core/cva6.sv` — instantiates all pipeline stages
- **Frontend / IF**: `core/frontend/frontend.sv` — PC generation, BPU, fetch
  - BTB: `core/frontend/btb.sv` (direct-mapped, parameterized size)
  - BHT: `core/frontend/bht.sv` / `bht2lvl.sv` (2-bit saturating counters)
  - RAS: `core/frontend/ras.sv`
  - Instruction queue: `core/frontend/instr_queue.sv`
- **ID stage**: `core/id_stage.sv` — decode, compressed expansion
  - Main decoder: `core/decoder.sv`
  - Compressed: `core/compressed_decoder.sv`
- **Issue stage**: `core/issue_stage.sv`, `core/scoreboard.sv`
- **EX stage**: `core/ex_stage.sv`
  - ALU: `core/alu.sv`
  - Branch unit: `core/branch_unit.sv`
  - LSU: `core/load_store_unit.sv`
  - MUL/DIV: `core/mult.sv`, `core/multiplier.sv`, `core/serdiv.sv`
  - FPU: `core/fpu_wrap.sv` (wraps cvfpu at `core/cvfpu/`)
- **Commit stage**: `core/commit_stage.sv`
- **CSR file**: `core/csr_regfile.sv` — all M/S/U mode CSRs
- **D-cache**: `core/cache_subsystem/wt_dcache.sv` (write-through) or `std_nbdcache.sv`
- **I-cache**: `core/cache_subsystem/cva6_icache.sv`
- **MMU/TLB**: `core/cva6_mmu/cva6_mmu.sv`, `cva6_ptw.sv`
- **PMP**: `core/pmp/src/pmp.sv`
- **Package files** (intact, use as reference):
  - `core/include/ariane_pkg.sv` — core-wide type definitions
  - `core/include/riscv_pkg.sv` — RISC-V ISA encodings
  - `core/include/cv64a6_imafdc_sv39_config_pkg.sv` — hardware configuration
  - `core/include/wt_cache_pkg.sv`, `std_cache_pkg.sv` — cache types
