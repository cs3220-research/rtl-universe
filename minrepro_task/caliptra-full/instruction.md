# Caliptra Root of Trust — Restore the RTL

You are in `/app`, the **Caliptra Root of Trust** RTL repository — a hardware
security module that provides cryptographic attestation for SoC boot flows.
Caliptra is an open-source Root-of-Trust IP block developed under the CHIPS
Alliance.

## What has been stripped

All **RTL implementation files** (`.sv` / `.v`) in `src/*/rtl/` have been
zeroed out (file exists but content is empty). Specifically:

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
- `src/mldsa/rtl/` — ML-DSA (Dilithium) accelerator interface
- `src/riscv_core/veer_el2/rtl/` — VeeR EL2 RISC-V core
- `src/integration/rtl/` — top-level integration (caliptra_top.sv, reg headers)

**Kept intact** (do NOT modify):
- All testbench files (`src/*/tb/`, `src/integration/tb/`)
- All `.vf` filelist configs (`src/*/config/`)
- All firmware tests (`src/integration/test_suites/`)
- All firmware libraries (`src/integration/test_suites/libs/`)
- The Makefile (`tools/scripts/Makefile`)
- The submodules directory (`submodules/adams-bridge/`) — MLDSA/MLKEM hardware
- All coverage, UVM, formal, stimulus files
- Documentation (`docs/`, `README.md`)

## What is being tested

The verifier runs the **L0 Verilator regression** — the same set of
firmware-driven simulation tests run in CI. Each test:

1. Compiles a small RISC-V C program from `src/integration/test_suites/<name>/`
   using the `riscv64-unknown-elf-gcc` cross-compiler
2. Runs the Caliptra Verilator simulation with that firmware
3. Checks the simulation log for `* TESTCASE PASSED`

The full test list is in `src/integration/stimulus/L0_regression.yml`.


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

Reward is **proportional**: `passed_tests / total_tests` (float in [0, 1]).
Partial credit is awarded for any passing test. The denominator is the number
of L0 regression tests discovered at image build time (stored in
`.harbor/total_tests`).

## Environment

- **Verilator** pre-installed via apt (version 5.x+)
- **riscv64-unknown-elf-gcc** cross-compiler on PATH
- **Python 3** with PyYAML
- `CALIPTRA_ROOT` environment variable = `/app`
- `CALIPTRA_PRIM_ROOT` = `/app/src/caliptra_prim_generic`

## Key commands

```bash
# Set required env vars
export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic

# Rebuild Verilator binary after changing RTL (run from a scratch directory)
mkdir -p /tmp/build_dir
make -C /tmp/build_dir \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    verilator-build

# Run a single test (from a fresh scratch directory)
mkdir -p /tmp/run_sha256
cp -r /tmp/build_dir/. /tmp/run_sha256/
make -C /tmp/run_sha256 \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    TESTNAME=smoke_test_sha256 \
    verilator VERILATOR_RUN_ARGS="+CLP_REGRESSION"

# Look at the full test list
cat src/integration/stimulus/L0_regression.yml

# Check complete RTL source filelist
cat src/integration/config/caliptra_top_tb.vf

# Look at source for a specific block (e.g. SHA-256 RTL)
ls src/sha256/rtl/

# Check the submodule (MLDSA/MLKEM hardware, already present)
ls submodules/adams-bridge/src/
```

## Architecture overview

Caliptra is a single SystemVerilog SoC with:
- A **VeeR EL2** RISC-V core (rv32imc) running firmware
- Cryptographic accelerators: SHA-256, SHA-512 (+ masked variant), SHA-3,
  HMAC, HMAC-DRBG, ECC/secp384r1, DOE (AES-CBC), AES-GCM, MLDSA, MLKEM
- **Key Vault** (48 slots x 512 bits), **PCR Vault**, **Data Vault**
- **SoC Interface**: AHB-Lite mailbox, WDT, SHA accelerator, boot FSM
- **AXI4** DMA engine
- **TRNG** subsystem (entropy_src -> CSRNG -> EDN)

See `README.md` and `docs/` for the full specification.
Check `src/integration/config/caliptra_top_tb.vf` for the complete ordered
list of RTL source files.

## Notes on the submodule

`submodules/adams-bridge/` contains the ML-DSA (Dilithium) and ML-KEM
hardware implementation. This submodule is already materialized in `/app` —
you do not need to run `git submodule update`. The RTL files within this
submodule are **not** stripped; only the main `src/` tree is zeroed.
