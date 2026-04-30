# NVDLA — Restore the RTL Implementation

## What This Repository Is

This is **NVIDIA's Deep Learning Accelerator (NVDLA)**, an open-source hardware
design for deep learning inference. NVDLA is a full-chip Verilog implementation
of a deep learning accelerator with the following functional blocks:

| Block | Purpose |
|-------|---------|
| **BDMA** | Bulk DMA engine |
| **CDMA** | Convolution DMA — fetches feature maps and weights |
| **CSC** | Convolution sequence controller |
| **CMAC** | Convolution MAC array (2048 × INT8 or 1024 × FP16 MACs) |
| **CACC** | Convolution accumulator |
| **SDP** | Single data path — batch norm, bias add, ReLU, LUT |
| **PDP** | Planar data path — pooling (max, avg, min) |
| **CDP** | Channel data path — local response normalization |
| **RUBIK** | Data reshape engine |
| **NOCIF** | Network-on-chip interface (AXI4 primary and CVSRAM ports) |
| **GLB** | Global controller |
| **TOP** | Top-level instantiation (`NV_nvdla`) |

The design has **2048 8-bit MACs** (or 1024 16-bit fixed/floating-point MACs),
a 16-bank convolution buffer (CBUF), and dual AXI4 memory interfaces.

## What Has Been Stripped

All implementation Verilog files in `vmod/nvdla/` have been **zeroed out**
(empty files). The file names and directory structure are intact, but every
module body has been removed. You must restore them.

**Kept intact (do not modify):**
- `vmod/vlibs/` — library cells and standard cells (intact)
- `vmod/rams/` — behavioral RAM models (intact)
- `vmod/include/` — include headers (intact)
- `verif/` — the entire testbench and traces directory (intact)
- `tools/` — build tools (intact)
- `cmod/` — C++ transaction-level model (intact, not used in Verilator tests)

## Test Infrastructure

Tests use a **Verilator trace-player**. The flow is:

1. Run `verilator` to elaborate `vmod/nvdla/top/NV_nvdla.v` (and its
   dependencies from `vmod/`) plus the testbench driver `verif/verilator/nvdla.cpp`.
2. Compile the generated C++ into `VNV_nvdla` binary.
3. Convert each test's `input.txn` to binary format with
   `verif/verilator/input_txn_to_verilator.pl`.
4. Run `VNV_nvdla trace.bin` — the simulator reads register writes/reads and
   memory loads/dumps from the trace, drives the RTL, and checks output data.
5. A test **passes** when `VNV_nvdla` prints `*** PASS` at the end.

## Scored Tests (12 total)

| Test name | What it exercises |
|-----------|-------------------|
| `sanity0` | Basic register read/write sanity |
| `sanity1` | Single convolution layer (DBB memory) |
| `sanity1_cvsram` | Same convolution using on-chip SRAM |
| `sanity2` | Multi-layer sequence |
| `sanity2_cvsram` | Same with CVSRAM |
| `sanity3` | Longer regression test with data checking |
| `sanity3_cvsram` | Same with CVSRAM |
| `conv_8x8_fc_int16` | 8×8 fully-connected convolution, INT16 weights |
| `googlenet_conv2_3x3_int16` | GoogLeNet Conv2 layer, INT16 |
| `pdp_max_pooling_int16` | PDP max pooling, INT16 |
| `sdp_relu_int16` | SDP ReLU activation, INT16 |
| `cc_alexnet_conv5_relu5_int16_dtest_cvsram` | AlexNet conv5+relu5, INT16, CVSRAM |

## How to Build and Run Tests

```bash
# 1. Create tree.make (tells the build system about tool paths)
cat > tree.make << 'EOF'
PROJECTS  := nv_full
OUTDIR    := outdir
CPP       := cpp
GCC       := g++
PERL      := perl
VERILATOR := verilator
CLANG     := clang
EOF

# 2. Create the outdir symlink that verilator.f expects
mkdir -p outdir/nv_full
ln -sfn /app/vmod outdir/nv_full/vmod

# 3. Verilator elaboration (run from verif/verilator/)
cd verif/verilator
mkdir -p /app/outdir/nv_full/verilator
verilator --cc --exe \
    -f verilator.f \
    nvdla.cpp \
    --Mdir /app/outdir/nv_full/verilator \
    --output-split 5000000 \
    --output-split-cfuncs 5000000

# 4. Compile
make -j8 -C /app/outdir/nv_full/verilator -f VNV_nvdla.mk CC=gcc CXX=g++

# 5. Run a single test (e.g., sanity0)
TEST=sanity0
mkdir -p /app/outdir/nv_full/verilator/test/${TEST}
perl input_txn_to_verilator.pl \
    /app/verif/traces/traceplayer/${TEST} \
    /app/outdir/nv_full/verilator/test/${TEST}/trace.bin

cd /app/outdir/nv_full/verilator/test/${TEST}
/app/outdir/nv_full/verilator/VNV_nvdla trace.bin
# Should print: *** PASS
```

## Important Notes on the Build

- `verilator.f` references `../../outdir/nv_full/vmod/...` — these paths
  resolve because of the `ln -sfn /app/vmod outdir/nv_full/vmod` symlink above.
- Use Verilator 5.x (installed at `/tools/verilator/bin/verilator`).
- The `-DDESIGNWARE_NOEXIST` define in `verilator.f` enables substitute models
  for DesignWare components (min/max/lsd trees) that live in `vmod/vlibs/`.
- `-DSYNTHESIS` and `-DNO_PLI` are required to suppress simulation-only constructs.

## Module Organization

Each subdirectory of `vmod/nvdla/` corresponds to a functional block:

```
vmod/nvdla/
├── top/         NV_nvdla.v (top-level instantiation of all partitions)
├── bdma/        Bulk DMA
├── cacc/        Convolution accumulator
├── car/         Clock-and-reset
├── cbuf/        Convolution buffer (SRAM banks)
├── cdma/        Convolution DMA (fetches weights + feature maps)
├── cdp/         Channel data path (LRN)
├── cmac/        MAC array
├── csb_master/  CSB register bus master
├── csc/         Convolution sequence controller
├── glb/         Global controller / interrupt
├── nocif/       Network-on-chip interfaces (AXI4 primary + CVSRAM)
├── pdp/         Planar data path (pooling)
├── retiming/    Optional retiming registers
├── rubik/       Data reshape
└── sdp/         Single data path (batch norm, bias, activation)
```

Start with the simpler, lower-fanout modules (`car/`, `glb/`, simple subunits)
and work up to the larger modules (`cdma/`, `cmac/`, `csc/`, `top/`).

## Important: Partial Credit and Persistence

You are scored **proportionally** — every single test you get to pass earns
credit. You do NOT need to restore all 266 modules. Even getting `sanity0` to
pass (which exercises the full register bus and basic convolution path) is a
meaningful score.

**Do not give up or stop early because the task looks large.** Work
incrementally: pick a module, examine its inputs/outputs and the C-model
reference in `cmod/`, implement it, verify it elaborates cleanly in Verilator,
then run `sanity0` to check. You have up to 24 hours. Use all of it.

The C-model in `cmod/` is a high-level transaction-level model in C++/SystemC.
It is NOT synthesizable, but its data structures and algorithms describe what
each RTL module is supposed to compute — use it as a reference.

## File Structure Reference

```
nvdla/
├── cmod/          C++ transaction-level reference model (keep intact)
├── spec/          RTL configuration (defines for nv_full variant)
├── tools/         Build scripts (tmake, perl helpers)
├── verif/
│   ├── traces/traceplayer/   Test trace directories (input.txn files)
│   ├── verilator/            Verilator testbench (nvdla.cpp, verilator.f)
│   └── synth_tb/             Verilog testbench (VCS only, not scored)
└── vmod/
    ├── nvdla/     ← IMPLEMENTATION STRIPPED (restore these)
    ├── rams/      Behavioral RAM models (intact)
    ├── vlibs/     Library/cell models (intact)
    └── include/   Header files (intact)
```
