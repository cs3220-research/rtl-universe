# CVA6 Smoke Tests — Restore the RISC-V Core RTL (Subset)

You are in `/app`, the root of the **OpenHW Group CVA6** (formerly Ariane) RISC-V
processor repository. CVA6 is a Linux-capable, 6-stage, in-order 64-bit core
implementing RV64IMAFDC. See `README.md` for architecture details.

This is the **smoke-test variant**: only 10 representative RISC-V integer ASM tests
are scored. This makes it faster for the agent to iterate and get signal.

The full task (228 tests) is in the `cva6` Harbor task.

## What was stripped

Same as the full task — all synthesizable SystemVerilog implementation files in
`core/` have been zeroed out. See `core/Flist.cva6` for the complete list.

The key files to restore:

**Minimum for any integer tests to pass:**
- `core/cva6.sv` — top-level core
- `core/decoder.sv`, `core/compressed_decoder.sv` — instruction decode
- `core/alu.sv` — integer ALU (add/sub/shift/logic/compare)
- `core/branch_unit.sv` — branch resolution
- `core/controller.sv` — flush/stall control
- `core/id_stage.sv`, `core/issue_stage.sv`, `core/issue_read_operands.sv`
- `core/ex_stage.sv` — execute stage
- `core/commit_stage.sv` — retire/writeback
- `core/scoreboard.sv`, `core/raw_checker.sv`
- `core/ariane_regfile_ff.sv` — integer register file
- `core/csr_regfile.sv` — CSR file (mstatus, mepc, etc.)
- `core/load_unit.sv`, `core/store_unit.sv`, `core/load_store_unit.sv`, `core/lsu_bypass.sv`
- `core/frontend/frontend.sv`, `core/frontend/instr_queue.sv`, `core/frontend/instr_scan.sv`
- `core/frontend/bht.sv`, `core/frontend/btb.sv`, `core/frontend/ras.sv`
- `core/cache_subsystem/cva6_icache.sv`, `core/cache_subsystem/cva6_icache_axi_wrapper.sv`
- `core/cache_subsystem/wt_dcache.sv` (+ ctrl, mem, missunit, wbuffer)
- `core/cache_subsystem/wt_cache_subsystem.sv`, `core/cache_subsystem/wt_axi_adapter.sv`
- `core/cache_subsystem/axi_adapter.sv`
- `core/axi_shim.sv`

**What was kept** (do NOT modify):
- All `core/include/*.sv` package files — intact, use as reference
- All testbench files under `corev_apu/tb/`, `verif/`
- All `vendor/pulp-platform/` files
- `ci/riscv-asm-tests.list` — the 10-test smoke subset
- `core/Flist.cva6` — Verilator file list

## Environment

Pre-installed tools:
- **Verilator** v5.008 at `/tools/verilator/`
- **RISC-V GCC** (rv64gc, linux ABI) at `/tools/riscv/`
- **Spike** ISS at `/tools/spike/`
- **make**, **git**, **python3**

Pre-built test binaries at `/app/tmp/riscv-tests/build/isa/`.

```bash
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
export PATH="/tools/verilator/bin:/tools/riscv/bin:$PATH"
```

## How to build and run

```bash
export CVA6_REPO_DIR=/app
export RISCV=/tools/riscv
export VERILATOR_INSTALL_DIR=/tools/verilator
export SPIKE_INSTALL_DIR=/tools/spike
export NUM_JOBS=8

# Build the Verilator model:
make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=$NUM_JOBS

# Run a single test:
work-ver/Variane_testharness tmp/riscv-tests/build/isa/rv64ui-p-add

# Run the smoke test suite:
for test in rv64ui-p-add rv64ui-p-addi rv64ui-p-and rv64ui-p-or rv64ui-p-sub \
            rv64ui-p-jal rv64ui-p-jalr rv64ui-p-beq rv64ui-p-lw rv64ui-p-sw; do
    echo -n "Testing ${test}: "
    work-ver/Variane_testharness tmp/riscv-tests/build/isa/${test} \
        && echo "PASS" || echo "FAIL"
done
```

## Scoring

Reward is proportional: `passed / 10`. Even one passing test earns 0.1 reward.
