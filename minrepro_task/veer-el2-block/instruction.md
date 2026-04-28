# VeeR EL2 Block Tests — Restore the RTL Modules

You are in `/app`, the root of the **CHIPS Alliance VeeR EL2** RISC-V core repository.
VeeR EL2 is a 32-bit RV32IMC in-order core. See `README.md` and `docs/source/` for details.

This is the **block-level variant**: only the block-level cocotb tests under
`verification/block/` are scored. Each block tests one design module in isolation
using Verilator + cocotb via a wrapper SV module.

## What was stripped

All synthesizable SystemVerilog/Verilog implementation files under `design/` have been
emptied (zero-byte). Files to restore (from `testbench/flist`):

| Module | File(s) |
|--------|---------|
| EXU ALU | `design/exu/el2_exu_alu_ctl.sv` |
| EXU MUL | `design/exu/el2_exu_mul_ctl.sv` |
| EXU DIV | `design/exu/el2_exu_div_ctl.sv` |
| EXU top | `design/exu/el2_exu.sv` |
| DEC decode | `design/dec/el2_dec_decode_ctl.sv` |
| DEC GPR | `design/dec/el2_dec_gpr_ctl.sv` |
| DEC IB | `design/dec/el2_dec_ib_ctl.sv` |
| DEC PMP | `design/dec/el2_dec_pmp_ctl.sv` |
| DEC TLU | `design/dec/el2_dec_tlu_ctl.sv` |
| DEC trigger | `design/dec/el2_dec_trigger.sv` |
| DEC top | `design/dec/el2_dec.sv` |
| DMA ctrl | `design/el2_dma_ctrl.sv` |
| IFU blocks | `design/ifu/el2_ifu_*.sv` (8 files) |
| LSU blocks | `design/lsu/el2_lsu*.sv` (11 files) |
| DBG | `design/dbg/el2_dbg.sv` |
| DMI | `design/dmi/dmi_mux.v`, `dmi_wrapper.v`, `dmi_jtag_to_core_sync.v`, `rvjtag_tap.v` |
| PIC | `design/el2_pic_ctrl.sv` |
| PMP | `design/el2_pmp.sv` |
| Lib | `design/lib/beh_lib.sv`, `el2_lib.sv`, `el2_mem_if.sv`, `mem_lib.sv` |
| Top | `design/el2_veer.sv`, `el2_veer_wrapper.sv`, `el2_mem.sv`, `el2_veer_lockstep.sv` |

**Keep** (do not remove or modify): all test files, wrapper SVs, `configs/`, `tools/`, `third_party/`.

## Environment

Pre-installed tools:
- `verilator` (v5.x)
- `python3`, `pip`, `nox`
- `cocotb` 1.8.0, `pyuvm`, `pytest`, `scipy`
- `make`, `git`

Set `RV_ROOT=/app` before running Make.

## Block test structure

Each block in `verification/block/<name>/` has:
- A `Makefile` (includes `../common.mk`)
- `testbench.py` — cocotb testbench
- `test_*.py` — test functions
- `*_wrapper.sv` — SV wrapper for DUT (already present, not stripped)
- `config.vlt` — Verilator warnings suppression (if present)

Tests are run via `nox` from `verification/block/`:

```bash
cd /app
export RV_ROOT=/app
pip install -r requirements.txt

# Run all block tests:
cd verification/block
nox -t tests

# Run a single block:
make -C verification/block/exu_alu all

# Run a single test module within a block:
make -C verification/block/exu_alu MODULE=test_arith all
```

## Block test inventory

| Block | Tests |
|-------|-------|
| `exu_alu` | test_arith, test_logic, test_zbb, test_zbs, test_zbp, test_zba |
| `exu_mul` | test_mul |
| `exu_div` | test_div |
| `dec` | test_dec |
| `dec_ib` | test_dec_ib |
| `dec_tl` | test_dec_tl |
| `dec_tlu_ctl` | test_dec_tl |
| `dec_pmp_ctl` | test_dec_pmp_ctl |
| `dma` | test_reset, test_read, test_write, test_address, test_ecc, test_debug_read, test_debug_write, test_debug_address |
| `dccm` | test_readwrite |
| `iccm` | test_readwrite |
| `ifu_compress` | test_compress |
| `ifu_mem_ctl` | test_miss, test_err, test_err_stop |
| `lib_ahb_to_axi4` | test_write, test_read |
| `lib_axi4_to_ahb` | test_axi, test_axi_read_channel, test_axi_write_channel |
| `dmi` | test_jtag_ir, test_dmi_read_write, test_dmi_tap_fsm |
| `dcls` | test_lockstep |
| `pmp` | test_xwr_access, test_address_matching, test_multiple_configs |
| `pmp_random` | test_pmp_random |
| `pic` | test_reset, test_clken, test_config, test_pending, test_prioritization, test_servicing |
| `pic_gw` | test_gateway |
| `lsu_tl` | test_lsu_tl |

## Scoring

Reward is **proportional**: `passed_test_functions / total_test_functions`.

Each cocotb test function (one entry in `results.xml`) counts as one test.
The denominator is stored in `/app/.harbor/total_tests`.

Getting even one block's tests passing earns partial credit. Blocks are
**independent** — you can focus on simpler blocks like `exu_alu` first.

## Quick start — easiest blocks to implement first

1. **`exu_alu`** — pure combinational logic (no state). ALU with rv32imc + bitmanip.
2. **`exu_div`** / **`exu_mul`** — arithmetic units
3. **`ifu_compress`** — RVC decompressor (combinational)
4. **`lib_ahb_to_axi4`** / **`lib_axi4_to_ahb`** — bus bridges (from `design/lib/`)
5. More complex: `dma`, `pic`, `dccm`/`iccm` (memories with ECC), `dec`, `lsu`

## Config generation

Each block Makefile auto-generates VeeR config headers in its `snapshots/default/`:

```bash
# Done automatically by the block Makefile; or manually:
export RV_ROOT=/app
$RV_ROOT/configs/veer.config
```

## Key interfaces to understand

- `design/include/el2_def.sv` — parameter definitions (kept, not stripped)
- `design/lib/el2_mem_if.sv` — memory interface types
- `verification/block/common/` — shared Python utilities (axi.py, csrs.py, utils.py)
- `verification/block/common.mk` — common cocotb Makefile fragment
