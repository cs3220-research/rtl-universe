# Caliptra Root of Trust — Restore the RTL

You are in `/app`, the **Caliptra Root of Trust** RTL repository — a
hardware security module (HSM) that provides cryptographic attestation
for SoC boot flows. Caliptra is an open-source Root-of-Trust IP block
developed under the CHIPS Alliance.

## What has been stripped

All **RTL implementation files** (`.sv` / `.v`) in `src/*/rtl/` have been
zeroed out (file exists but content is empty).  Specifically:

- All state machines, datapaths, and control logic for every IP block:
  - `src/sha256/rtl/` — SHA-256 accelerator
  - `src/sha512/rtl/` — SHA-512 accelerator
  - `src/sha512_masked/rtl/` — masked SHA-512 (side-channel protected)
  - `src/sha3/rtl/` — SHA-3 / KMAC block
  - `src/hmac/rtl/` — HMAC accelerator
  - `src/hmac_drbg/rtl/` — HMAC-DRBG
  - `src/ecc/rtl/` — ECC (secp384r1) accelerator
  - `src/doe/rtl/` — Device Obfuscation Engine (AES-CBC)
  - `src/aes/rtl/` — AES-GCM block
  - `src/axi/rtl/` — AXI4 infrastructure (subordinate, manager, DMA)
  - `src/keyvault/rtl/` — Key Vault
  - `src/pcrvault/rtl/` — PCR Vault
  - `src/datavault/rtl/` — Data Vault
  - `src/soc_ifc/rtl/` — SoC interface (mailbox, WDT, SHA accel, boot FSM)
  - `src/libs/rtl/` — shared RTL libraries
  - `src/ahb_lite_bus/rtl/` — AHB-Lite bus fabric
  - `src/caliptra_prim/rtl/` — primitive cells (FIFOs, arbiters, flops)
  - `src/caliptra_prim_generic/rtl/` — generic technology implementations
  - `src/caliptra_tlul/rtl/` — TL-UL interconnect adapter
  - `src/csrng/rtl/` — CSRNG (NIST SP 800-90A CTR-DRBG)
  - `src/edn/rtl/` — Entropy Distribution Network
  - `src/entropy_src/rtl/` — Entropy source
  - `src/lc_ctrl/rtl/` — Lifecycle controller
  - `src/kmac/rtl/` — KMAC accelerator
  - `src/riscv_core/veer_el2/rtl/` — VeeR EL2 RISC-V core
  - `src/integration/rtl/` — top-level integration (caliptra_top.sv, reg headers)

**Kept intact** (do NOT modify):
- All testbench files (`src/*/tb/`, `src/integration/tb/`)
- All `.vf` filelist configs (`src/*/config/`)
- All firmware tests (`src/integration/test_suites/`)
- All firmware libraries (`src/integration/test_suites/libs/`)
- The Makefile (`tools/scripts/Makefile`)
- The submodules directory (`submodules/`)
- All coverage, UVM, formal, stimulus files
- Documentation (`docs/`, `README.md`)

## What is being tested

The verifier runs the **L0 Verilator regression** — the same set of
firmware-driven simulation tests run in CI.  Each test:

1. Compiles a small RISC-V C program (in `src/integration/test_suites/<name>/`)
   using the `riscv64-unknown-elf-gcc` cross-compiler
2. Runs the Caliptra Verilator simulation with that firmware
3. Checks the simulation log for `* TESTCASE PASSED`

The full test list is in `src/integration/stimulus/L0_regression.yml`.
The Verilator binary is pre-built in `obj_dir/` at Docker image build time
so you do **not** need to rebuild Verilator from scratch — but any RTL file
change requires running `make verilator-build` again.

## Scoring

Reward is **proportional**: `passed_tests / total_tests` (float in [0, 1]).
Partial credit is awarded for any passing test. The denominator is the number
of L0 regression tests discovered at image build time (stored in
`.harbor/total_tests`).

## Environment

- **Verilator** pre-installed (apt); version ≥ 5.x
- **riscv64-unknown-elf-gcc** cross-compiler on PATH (GCC 12.2.0)
- **Python 3** with PyYAML (for the regression script)
- **CALIPTRA_ROOT** environment variable set to `/app`
- Pre-built Verilator simulation binary in `/app/obj_dir/` (from green source)

## Key commands

```bash
# Set required env vars
export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic

# Rebuild Verilator binary after changing RTL
cd /tmp/build_dir
make -C /tmp/build_dir -f $CALIPTRA_ROOT/tools/scripts/Makefile verilator-build

# Run a single test (from a scratch directory)
mkdir -p /tmp/run_smoke_test_sha256
make -C /tmp/run_smoke_test_sha256 \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    TESTNAME=smoke_test_sha256 \
    verilator VERILATOR_RUN_ARGS=+CLP_REGRESSION

# Run full regression
cd /app && python3 tools/scripts/run_verilator_l0_regression.py

# Look at the test list
cat src/integration/stimulus/L0_regression.yml

# Check filelist for RTL sources
cat src/integration/config/caliptra_top_tb.vf
```

## Architecture overview

Caliptra is a single SystemVerilog SoC with:
- A **VeeR EL2** RISC-V core (rv32imc) running firmware
- Cryptographic accelerators: SHA-256, SHA-512 (+ masked variant), SHA-3,
  HMAC, HMAC-DRBG, ECC/secp384r1, DOE (AES-CBC), AES-GCM, MLDSA, MLKEM
- **Key Vault** (48 slots × 512 bits), **PCR Vault**, **Data Vault**
- **SoC Interface**: AHB-Lite mailbox, WDT, SHA accelerator, boot FSM
- **AXI4** DMA engine
- **TRNG** subsystem (entropy_src → CSRNG → EDN)

See `README.md` and `docs/` for the full specification.
Check `src/integration/config/caliptra_top_tb.vf` for the complete ordered
list of RTL source files.
