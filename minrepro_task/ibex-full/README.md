# ibex Harbor Task

**Repo:** lowRISC Ibex RISC-V Core (https://github.com/lowRISC/ibex)
**Build system:** FuseSoC + Verilator
**Test count:** 5 (1 CSR testbench + 4 SW simulation tests)

## Variants

- `ibex` — all 5 tests (full task)
- `ibex-e2e` — 4 end-to-end software simulation tests only

## Test Targets

| # | Target | What it tests |
|---|--------|---------------|
| 1 | `tb_cs_registers` | CSR register file (read/write/side-effects) |
| 2 | `hello_test` | Basic execution: putchar/puts, timer interrupt |
| 3 | `dit_test` | Data-independent timing (DIT) CSR feature |
| 4 | `dummy_instr_test` | Dummy instruction insertion (security hardening) |
| 5 | `pmp_smoke_test` | Physical Memory Protection (PMP) |

## Build System Notes

Verilator recompiles the full core on every source change — there is no
persistent incremental build cache. The Docker image installs all tools via
apt and pre-downloaded tarballs; the warm stage only runs tests to count
green passing targets and does not cache build outputs.

## Toolchain Versions

- Verilator 4.210
- FuseSoC 2.4.3
- RISC-V toolchain: lowrisc-toolchain-gcc-rv32imcb-20220210-1
- Ibex Spike cosim: 6d5b660 (not used in this task variant)
