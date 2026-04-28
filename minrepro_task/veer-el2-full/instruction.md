# VeeR EL2 — Restore the RTL

You are in `/app`, the root of the **CHIPS Alliance VeeR EL2** RISC-V core repository
(Apache-2.0). VeeR EL2 is a production-grade 32-bit RV32IMC in-order core used in
automotive and industrial SoCs. See `README.md` and `docs/source/` for architecture details.

The **test infrastructure is fully intact**, but all the implementation RTL has been
removed. Your task is to restore it so that the test suite passes.

## What was stripped

All synthesizable SystemVerilog/Verilog implementation files under `design/` have been
emptied (zero-byte files). The files that were cleared are listed in the flist at
`testbench/flist`. Specifically:

- `design/el2_veer.sv`, `design/el2_veer_wrapper.sv`, `design/el2_veer_lockstep.sv`
- `design/el2_mem.sv`, `design/el2_pic_ctrl.sv`, `design/el2_dma_ctrl.sv`, `design/el2_pmp.sv`
- `design/ifu/` — all 8 IFU modules (`el2_ifu*.sv`)
- `design/dec/` — all 7 DEC modules (`el2_dec*.sv`)
- `design/exu/` — ALU, MUL, DIV, top (`el2_exu*.sv`)
- `design/lsu/` — all 11 LSU modules (`el2_lsu*.sv`)
- `design/dbg/el2_dbg.sv`
- `design/dmi/` — 4 DMI files (`dmi_*.v`, `rvjtag_tap.v`)
- `design/lib/` — `el2_lib.sv`, `el2_mem_if.sv`, `beh_lib.sv`, `mem_lib.sv`

**What was kept** (do not remove or modify):
- All test files: `verification/block/*/test_*.py`, `testbench/`, `verification/top/`
- All testbench wrappers: `verification/block/*/*_wrapper.sv`
- Configuration tools: `configs/veer.config`, `configs/veer_config_gen.py`
- `testbench/flist`, `testbench/*.sv`, `testbench/*.cpp`
- `tools/Makefile`, `tools/picolibc.mk`, `tools/riscof/`
- `third_party/`, `docs/`, `requirements.txt`
- Generated config headers are re-created by `configs/veer.config` at build time

## Environment

The following tools are pre-installed:

- `verilator` (v5.x)
- `riscv64-unknown-elf-gcc` (rv32imc cross-compiler)
- `python3`, `pip`, `nox`
- `cocotb` 1.8.0, `pyuvm`, `pytest`, `scipy` (from `requirements.txt`)
- `spike` (RISC-V ISS for RISCOF compliance)
- `riscof` (RISC-V compliance framework)
- `make`, `git`

Set `RV_ROOT=/app` before running any Make-based commands.

## How tests are organized

### 1. Block-level cocotb tests (`verification/block/`)

Each subdirectory contains a Makefile that drives a Verilator + cocotb simulation
of one design block. Tests are orchestrated via `nox`:

```bash
cd /app
export RV_ROOT=/app
pip install -r requirements.txt   # if not already done
cd verification/block
nox -t tests                       # run all block cocotb tests
# or run a single block:
make -C verification/block/exu_alu all
```

Individual blocks: `exu_alu`, `exu_mul`, `exu_div`, `dec`, `dec_ib`, `dec_tl`,
`dec_tlu_ctl`, `dec_pmp_ctl`, `dma`, `dccm`, `iccm`, `ifu_compress`, `ifu_mem_ctl`,
`lib_ahb_to_axi4`, `lib_axi4_to_ahb`, `dmi`, `dcls`, `pmp`, `pmp_random`, `pic`, `pic_gw`, `lsu_tl`

### 2. Top-level Verilator simulation (`tools/Makefile`)

Runs a full-core Verilator simulation with a compiled RISC-V binary:

```bash
export RV_ROOT=/app
cd /tmp/sim_run
make -f $RV_ROOT/tools/Makefile TEST=hello_world verilator
# Other tests: dhry, insns, irq, pmp, ecc, csr_access, ...
```

### 3. Top-level pyuvm test (`verification/top/test_pyuvm/`)

Full-core cocotb/pyuvm simulation testing interrupt behavior:

```bash
export RV_ROOT=/app
cd verification/top/test_pyuvm
python -m venv venv && source venv/bin/activate
pip install -r ../requirements.txt
python -m pytest -sv test_pyuvm.py
```

### 4. RISCOF compliance (`tools/riscof/`)

RISC-V architecture compliance tests comparing VeeR vs Spike signatures:

```bash
export RV_ROOT=/app
# First build the verilated model (from a temp dir):
mkdir -p /tmp/riscof_work && cd /tmp/riscof_work
make -f $RV_ROOT/tools/Makefile verilator-build
# Then run riscof
cp $RV_ROOT/tools/riscof/config.ini .
cp -r $RV_ROOT/tools/riscof/spike .
cp -r $RV_ROOT/tools/riscof/veer .
riscof testlist --config=config.ini \
  --suite=riscv-arch-test/riscv-test-suite/ \
  --env=riscv-arch-test/riscv-test-suite/env
riscof run --no-browser --config=config.ini \
  --suite=riscv-arch-test/riscv-test-suite/ \
  --env=riscv-arch-test/riscv-test-suite/env
```

## Scoring

Reward is **proportional**: `passed_tests / total_tests` (float in `[0, 1]`).

The denominator is set at image build time from the green-source run. It counts
the number of cocotb/nox test functions that passed. Partial credit is awarded
for any test that passes — even a single block like `exu_alu` earns points.

The test count is stored in `/app/.harbor/total_tests`.

## Useful commands

```bash
export RV_ROOT=/app

# List design files to restore:
cat testbench/flist

# Generate VeeR config headers (needed before any sim):
$RV_ROOT/configs/veer.config -set build_axi4

# Run a single block test:
make -C verification/block/exu_alu all

# Run all block tests via nox:
cd verification/block && nox -t tests

# Build verilated model for top-level tests:
mkdir -p /tmp/run && cd /tmp/run
make -f $RV_ROOT/tools/Makefile verilator-build
make -f $RV_ROOT/tools/Makefile TEST=hello_world verilator

# Check the design flist:
cat testbench/flist
```

## Architecture pointers

- Top-level wrapper: `design/el2_veer_wrapper.sv` (AXI4 or AHB-Lite port select)
- IFU: fetch, branch prediction, L0 I-cache, ICCM — `design/ifu/`
- DEC: decode, IB, GPR, TLU, trigger — `design/dec/`
- EXU: ALU (rv32imc + Zba/Zbb/Zbs/Zbc), MUL (3-cycle), DIV — `design/exu/`
- LSU: addr check, DCCM, store buffer, bus interface, ECC — `design/lsu/`
- DMA: AXI4 DMA controller — `design/el2_dma_ctrl.sv`
- PIC: programmable interrupt controller — `design/el2_pic_ctrl.sv`
- Debug: JTAG + DMI — `design/dbg/`, `design/dmi/`
- PMP: physical memory protection — `design/el2_pmp.sv`
- DCLS: dual-core lockstep — `design/el2_veer_lockstep.sv`
