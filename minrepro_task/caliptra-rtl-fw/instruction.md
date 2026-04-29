# Caliptra Root of Trust — Restore the RTL (E2E variant)

You are in `/app`, the **Caliptra Root of Trust** RTL repository — a hardware
security module that provides cryptographic attestation for SoC boot flows.
Caliptra is an open-source Root-of-Trust IP block developed under the CHIPS
Alliance.

## What has been stripped

All **RTL implementation files** (`.sv` / `.v`) in `src/*/rtl/` have been
zeroed out (file exists but content is empty). See the full list in the
`caliptra-full` task's instruction.md. In summary:

- All IP block RTL in `src/*/rtl/` (sha256, sha512, hmac, aes, ecc, doe,
  axi, keyvault, pcrvault, datavault, soc_ifc, libs, ahb_lite_bus,
  caliptra_prim, caliptra_tlul, csrng, edn, entropy_src, lc_ctrl, kmac,
  mldsa, riscv_core/veer_el2, integration/rtl)

**Kept intact** (do NOT modify):
- All testbench files (`src/*/tb/`, `src/integration/tb/`)
- All `.vf` filelist configs (`src/*/config/`)
- All firmware tests (`src/integration/test_suites/`)
- All firmware libraries
- The Makefile (`tools/scripts/Makefile`)
- The submodules directory (`submodules/adams-bridge/`) — already materialized
- Documentation (`docs/`, `README.md`)

## What is being tested (E2E variant)

The verifier runs the **E2E subset** of the L0 Verilator regression — tests
that exercise the full integrated Caliptra SoC. These tests require multiple
major subsystems (VeeR core + cryptographic accelerators + key vault + SoC
interface) to all work together:

| Test | What it exercises |
|------|-------------------|
| smoke_test_sha256 | SHA-256 accelerator + SoC boot |
| smoke_test_sha512 | SHA-512 accelerator + SoC boot |
| smoke_test_hmac | HMAC accelerator + SoC boot |
| smoke_test_sha3 | SHA-3 / CSHAKE accelerator |
| smoke_test_aes_gcm | AES-GCM accelerator |
| smoke_test_kv | Key Vault (basic slot operations) |
| smoke_test_kv_hmac_flow | Key Vault + HMAC multi-block flow |
| smoke_test_kv_doe | Key Vault + DOE (AES-CBC) |
| smoke_test_kv_uds_reset | Key Vault lifecycle + reset |
| smoke_test_mbox | Mailbox (SoC<->Caliptra interface) |
| smoke_test_trng | TRNG subsystem (entropy+CSRNG+EDN) |
| smoke_test_datavault_basic | Data Vault |
| smoke_test_zeroize_crypto | Zeroize of all crypto blocks |
| smoke_test_mldsa_edge | ML-DSA (requires adams-bridge submodule) |
| smoke_test_mlkem | ML-KEM (requires adams-bridge submodule) |

## Scoring

Reward is **proportional**: `passed_e2e_tests / total_e2e_tests` (float in [0, 1]).
The denominator is stored in `.harbor/total_tests` (set at image build time).

## Environment

- **Verilator** pre-installed via apt
- **riscv64-unknown-elf-gcc** cross-compiler on PATH
- **Python 3** with PyYAML
- `CALIPTRA_ROOT` = `/app`
- `CALIPTRA_PRIM_ROOT` = `/app/src/caliptra_prim_generic`

## Key commands

```bash
export CALIPTRA_ROOT=/app
export CALIPTRA_PRIM_ROOT=/app/src/caliptra_prim_generic
export CALIPTRA_PRIM_MODULE_PREFIX=caliptra_prim_generic

# Rebuild Verilator binary after changing RTL
mkdir -p /tmp/build_dir
make -C /tmp/build_dir \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    verilator-build

# Run a single E2E test
mkdir -p /tmp/run_sha256
cp -r /tmp/build_dir/. /tmp/run_sha256/
make -C /tmp/run_sha256 \
    -f $CALIPTRA_ROOT/tools/scripts/Makefile \
    TESTNAME=smoke_test_sha256 \
    verilator VERILATOR_RUN_ARGS="+CLP_REGRESSION"

# Check the E2E test list
cat .harbor/e2e_tests

# Check complete RTL source filelist
cat src/integration/config/caliptra_top_tb.vf
```

## Architecture overview

Caliptra is a single SystemVerilog SoC:
- **VeeR EL2** RISC-V core (rv32imc) running firmware
- Crypto: SHA-256, SHA-512 (+ masked), SHA-3, HMAC, ECC, DOE, AES-GCM,
  ML-DSA, ML-KEM
- **Key Vault**, **PCR Vault**, **Data Vault**
- **SoC Interface**: AHB-Lite mailbox, WDT, boot FSM
- **AXI4** DMA engine
- **TRNG** subsystem (entropy_src -> CSRNG -> EDN)

See `README.md` and `docs/` for the full specification.
