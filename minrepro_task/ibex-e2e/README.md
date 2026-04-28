# ibex-e2e Harbor Task

**Repo:** lowRISC Ibex RISC-V Core (https://github.com/lowRISC/ibex)
**Build system:** FuseSoC + Verilator
**Test count:** 4 (end-to-end software simulation tests)

This is the **e2e variant** of the ibex task. It scores only on
end-to-end software tests that exercise the full Ibex core in simulation.
The `ibex` task additionally includes the CS-registers unit testbench.

## Test Targets

| # | Target | What it tests |
|---|--------|---------------|
| 1 | `hello_test` | Basic execution: I/O, timer interrupt, WFI |
| 2 | `dit_test` | Data-independent timing CSR feature |
| 3 | `dummy_instr_test` | Dummy instruction insertion security feature |
| 4 | `pmp_smoke_test` | Physical Memory Protection |
