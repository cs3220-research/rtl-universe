# VeeR EL2 RISC-V Core — Restore the RTL

You are in `/app`, a clone of the **VeeR EL2 RISC-V core** from CHIPS Alliance.
VeeR EL2 is a production-grade, in-order, 2-stage pipeline RISC-V core
implementing RV32IMC with configurable DCCM, ICCM, instruction cache, PIC, and
DMA. See `docs/source/overview.md` and `README.md` for the architecture.

## What has been stripped

All SystemVerilog/Verilog implementation files under `design/` have been
**zeroed out** (truncated to empty). The test infrastructure is fully intact:

- `verification/block/` — 22 block-level cocotb testbenches (48 test sessions total)
- `verification/top/` — PyUVM integration test (`test_irq`)
- `verification/block/noxfile.py` — nox runner that orchestrates all block tests
- `verification/block/common.mk` — shared Makefile for the cocotb flow
- `configs/veer.config` — Perl script to generate VeeR configuration headers
- `testbench/` — top-level testbench files

**Stripped files** (you must restore):
```
design/el2_veer_wrapper.sv     design/el2_veer.sv
design/el2_veer_lockstep.sv    design/el2_mem.sv
design/el2_pic_ctrl.sv         design/el2_dma_ctrl.sv
design/el2_pmp.sv
design/dbg/el2_dbg.sv
design/dec/el2_dec.sv          design/dec/el2_dec_decode_ctl.sv
design/dec/el2_dec_gpr_ctl.sv  design/dec/el2_dec_ib_ctl.sv
design/dec/el2_dec_pmp_ctl.sv  design/dec/el2_dec_tlu_ctl.sv
design/dec/el2_dec_trigger.sv
design/dmi/dmi_wrapper.v       design/dmi/dmi_mux.v
design/dmi/rvjtag_tap.v        design/dmi/dmi_jtag_to_core_sync.v
design/exu/el2_exu.sv          design/exu/el2_exu_alu_ctl.sv
design/exu/el2_exu_mul_ctl.sv  design/exu/el2_exu_div_ctl.sv
design/ifu/el2_ifu.sv          design/ifu/el2_ifu_aln_ctl.sv
design/ifu/el2_ifu_bp_ctl.sv   design/ifu/el2_ifu_compress_ctl.sv
design/ifu/el2_ifu_ic_mem.sv   design/ifu/el2_ifu_iccm_mem.sv
design/ifu/el2_ifu_ifc_ctl.sv  design/ifu/el2_ifu_mem_ctl.sv
design/lib/beh_lib.sv          design/lib/el2_lib.sv
design/lib/ahb_to_axi4.sv      design/lib/axi4_to_ahb.sv
design/lib/el2_mem_if.sv       design/lib/el2_regfile_if.sv
design/lib/mem_lib.sv
design/lsu/el2_lsu.sv          design/lsu/el2_lsu_addrcheck.sv
design/lsu/el2_lsu_bus_buffer.sv design/lsu/el2_lsu_bus_intf.sv
design/lsu/el2_lsu_clkdomain.sv design/lsu/el2_lsu_dccm_ctl.sv
design/lsu/el2_lsu_dccm_mem.sv design/lsu/el2_lsu_ecc.sv
design/lsu/el2_lsu_lsc_ctl.sv  design/lsu/el2_lsu_stbuf.sv
design/lsu/el2_lsu_trigger.sv
```

**Kept** (do not modify these):
- All `verification/block/*/test_*.py` — the cocotb tests
- All `verification/block/*/*.sv` wrapper files (block-level test harnesses)
- `verification/block/noxfile.py`, `common.mk`, `requirements.txt`
- `verification/top/` — E2E PyUVM test
- `configs/veer.config` — configuration generator
- `design/include/el2_def.sv` — global parameter/type definitions (NOT stripped)
- `testbench/` — top-level testbench


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

Reward is **proportional**: `passing_nox_sessions / total_nox_sessions` (float
in `[0, 1]`). Each of the 48 block test sessions is scored independently — you
earn partial credit for any sessions that pass. The total denominator is the
number of passing sessions on the green source (captured at image build time).

## Environment

- **Verilator** (Debian bookworm, ≥4.106) is pre-installed
- **cocotb 1.8.0**, pyuvm 2.9.1, nox, and all dependencies from
  `verification/block/requirements.txt` are installed system-wide via pip
- **Perl** is available for `configs/veer.config`
- `RV_ROOT` must be set to `/app` before running any tests

## Useful commands

```bash
# Set the required environment variable
export RV_ROOT=/app

# Generate VeeR core configuration (required before building any block test)
# This creates snapshots/default/common_defines.vh and el2_pdef.vh
$RV_ROOT/configs/veer.config -fpga_optimize=0

# Run all block tests via nox (all 48 sessions, tagged "tests")
cd /app/verification/block
nox -t tests --no-venv

# Run a single block test directly
cd /app/verification/block/exu_alu
RV_ROOT=/app make MODULE=test_arith COCOTB_RESULTS_FILE=test_arith.xml

# Run a single nox session
cd /app/verification/block
nox -s "exu_alu_verify-all-test_arith" --no-venv

# List all nox sessions
nox -l

# Check results XML after a block test run
cat /app/verification/block/exu_alu/test_arith.xml
```

## Tips

1. Start with isolated leaf modules (`exu_alu`, `ifu_compress`, `pmp`) — they
   have few dependencies and give fast feedback from Verilator.
2. The `design/include/el2_def.sv` file defines core parameter interfaces;
   read it carefully as it's the basis for many module port declarations.
3. Each block test's `Makefile` lists the exact RTL files it needs under
   `VERILOG_SOURCES` — focus on those files first.
4. The `common.mk` generates the VeeR config snapshot automatically before
   building if `snapshots/default/common_defines.vh` doesn't exist.
5. The `noxfile.py` shows the complete test-to-module mapping — use it to
   prioritize high-value modules (e.g., `dma` has 8 test cases).
