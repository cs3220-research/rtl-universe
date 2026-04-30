# Ibex RISC-V Core — Restore the RTL Implementation

You are in `/app`, a **FuseSoC + Verilator** repository for the
**lowRISC Ibex** — a production-quality 32-bit RISC-V CPU core (RV32IMC)
written in SystemVerilog.

See `README.md`, `doc/`, and `ibex_configs.yaml` for the architecture.

## What Has Been Stripped

All **RTL implementation files** in `rtl/` have been emptied (zero bytes).
The `.core` files, testbenches, software tests, and documentation are intact.

**Stripped** (you must implement these):
- `rtl/ibex_pkg.sv` — package: enumerations, parameters, type definitions
- `rtl/ibex_alu.sv` — arithmetic/logic unit (ADD, SUB, shifts, comparison, B-ext)
- `rtl/ibex_branch_predict.sv` — static branch predictor
- `rtl/ibex_compressed_decoder.sv` — RVC compressed-to-32-bit instruction expander
- `rtl/ibex_controller.sv` — pipeline controller and hazard logic
- `rtl/ibex_counter.sv` — performance/cycle counter
- `rtl/ibex_cs_registers.sv` — control/status register file (RISC-V CSR spec)
- `rtl/ibex_csr.sv` — single CSR primitive (read-modify-write)
- `rtl/ibex_decoder.sv` — instruction decoder (opcode → control signals)
- `rtl/ibex_dummy_instr.sv` — dummy instruction insertion (security hardening)
- `rtl/ibex_ex_block.sv` — execution block (ALU + multiplier/divider)
- `rtl/ibex_fetch_fifo.sv` — fetch queue between IF and ID stages
- `rtl/ibex_icache.sv` — instruction cache
- `rtl/ibex_id_stage.sv` — instruction decode/execute stage
- `rtl/ibex_if_stage.sv` — instruction fetch stage
- `rtl/ibex_load_store_unit.sv` — load/store address generation and alignment
- `rtl/ibex_lockstep.sv` — lockstep redundancy (security feature)
- `rtl/ibex_multdiv_fast.sv` — fast single-cycle multiplier/divider
- `rtl/ibex_multdiv_slow.sv` — area-optimised multi-cycle multiplier/divider
- `rtl/ibex_pmp.sv` — Physical Memory Protection unit
- `rtl/ibex_prefetch_buffer.sv` — instruction prefetch buffer
- `rtl/ibex_register_file_ff.sv` — flip-flop register file (generic)
- `rtl/ibex_register_file_fpga.sv` — FPGA-optimized register file
- `rtl/ibex_register_file_latch.sv` — latch-based register file (ASIC)
- `rtl/ibex_top.sv` — top-level integration (core + lockstep + scramble)
- `rtl/ibex_top_tracing.sv` — top-level with RVFI tracing
- `rtl/ibex_tracer.sv` — instruction tracer
- `rtl/ibex_tracer_pkg.sv` — tracer package
- `rtl/ibex_wb_stage.sv` — writeback stage
- `examples/simple_system/rtl/ibex_simple_system.sv` — simple simulation system
- `examples/sw/simple_system/common/simple_system_common.c` — bare-metal SW library

**Kept** (do not modify):
- All `.core` FuseSoC descriptor files
- `dv/cs_registers/` — CS-registers testbench (complete, keep intact)
- `examples/simple_system/ibex_simple_system.cc` and `.h` — Verilator driver
- `examples/sw/simple_system/` — software test programs (`.c`, `Makefile`)
- `vendor/` — third-party dependencies
- `shared/`, `util/`, `lint/`, `doc/`, all `README.md` files

## Your Task

Implement the stripped files so all five test targets pass:

1. **`tb_cs_registers`** — Verilator simulation of the CSR register file
2. **`hello_test`** — bare-metal hello-world on simple system sim
3. **`dit_test`** — data-independent timing feature test
4. **`dummy_instr_test`** — dummy instruction insertion test
5. **`pmp_smoke_test`** — Physical Memory Protection smoke test


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

Reward is **proportional**: `passed_tests / total_tests` (float in `[0, 1]`).

- `tb_cs_registers` = 1 point (pass/fail, prints `// TEST PASSED //`)
- Each of the 4 software simulation tests = 1 point (exit code 0)
- **Total denominator: 5**

Getting a subset of tests to pass earns partial credit.

## Environment

- **Verilator 4.210**, **FuseSoC 2.4.3**, **Python 3**, **srecord** are pre-installed.
- **RISC-V toolchain** (`riscv32-unknown-elf-gcc`) is at `/tools/riscv/bin/`.
- `/app` is a fresh git repo — use it freely.

## Useful Commands

```bash
# Build and run the CS-registers testbench (tests ibex_cs_registers.sv)
fusesoc --cores-root=. run --target=sim --tool=verilator lowrisc:ibex:tb_cs_registers

# Build the simple-system simulator
fusesoc --cores-root=. run --target=sim --setup --build lowrisc:ibex:ibex_simple_system

# Compile a software test binary (produces hello_test.vmem)
cd examples/sw/simple_system/hello_test && make && cd /app

# Run a software test on the simulator
build/lowrisc_ibex_ibex_simple_system_0/sim-verilator/Vibex_simple_system \
    --raminit=examples/sw/simple_system/hello_test/hello_test.vmem

# Compile and run all SW tests in one shot via the Makefile shortcuts:
make build-simple-system          # build simulator (if not built yet)
cd examples/sw/simple_system/hello_test    && make && cd /app
cd examples/sw/simple_system/dit_test     && make && cd /app
cd examples/sw/simple_system/dummy_instr_test && make && cd /app
cd examples/sw/simple_system/pmp_smoke_test   && make && cd /app

# Check which Ibex configuration options are available
cat ibex_configs.yaml

# Lint the RTL
fusesoc --cores-root . run --target=lint lowrisc:ibex:ibex_core
```

## Module Hierarchy

```
ibex_top
  ibex_core
    ibex_if_stage
      ibex_icache / ibex_prefetch_buffer
      ibex_fetch_fifo
    ibex_id_stage
      ibex_decoder
      ibex_compressed_decoder
      ibex_controller
      ibex_ex_block
        ibex_alu
        ibex_multdiv_fast / ibex_multdiv_slow
      ibex_load_store_unit
      ibex_wb_stage
    ibex_cs_registers
      ibex_csr
      ibex_counter
      ibex_pmp
    ibex_branch_predict
    ibex_dummy_instr
    ibex_register_file_{ff,fpga,latch}
  ibex_lockstep
```

Refer to `doc/` and `rtl/ibex_core.f` for include-file order.
The package `ibex_pkg.sv` must be compiled first — it defines all enumerations.
