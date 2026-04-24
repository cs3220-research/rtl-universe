# Coral NPU — Restore the RTL and Firmware (E2E-only)

You are in `/app`, a bazel-based monorepo for the **Coral NPU** — a 32-bit
RISC-V ML accelerator with a scalar core, a 128-bit SIMD vector pipeline, and a
matrix MAC engine (see `doc/overview.md`, `doc/integration_guide.md`, and
`README.md` for the architecture).

The **test infrastructure is intact**, but all the code it exercises has been
removed:

- Chisel RTL under `hdl/chisel/src/` (test specs `*Test.scala` / `*Spec.scala` kept).
- Hand-written Verilog under `hdl/verilog/`.
- Firmware C/C++ under `sw/` (test files kept).
- `examples/` and the FPGA integration sources under `fpga/ip/`, `fpga/rtl/`, `fpga/sw/`.

**Your task**: restore enough of the missing sources so that the end-to-end
test subset passes.

## Scoring (E2E subset only)

Only the **~189 end-to-end test targets** count toward your reward:

- All `//tests/cocotb/...` **except** the `//tests/cocotb/tlul/...` subpackage
  (those are TL-UL bridge unit tests, which are component-level).
- `//tests/verilator_sim:core_mini_axi_non_incr_tests`
- `//tests/verilator_sim:backdoor_load_test`

The remaining ~116 targets (Chisel unit tests, cache/bus testbenches, fpga
utilities, `nexus_loader`, etc.) are not scored. You are free to ignore them,
but they are all still present in the BUILD files and will be scheduled by
`bazel test //...` unless you filter.

Reward is **proportional**: `e2e_passed / e2e_total` as a float in `[0, 1]`.

## Environment

- `bazel 7.4.1`, `openjdk-17`, `clang-19`, `python3.11`, `srecord`, `xxd` are
  pre-installed.
- The bazel output_base is pre-warmed (external repos + Verilator + firtool +
  TFLite-micro are already built).

## Useful commands

```bash
# The verifier uses exactly this query to pick the scored targets:
bazel query '( //tests/cocotb/... except //tests/cocotb/tlul/... ) union
             //tests/verilator_sim:core_mini_axi_non_incr_tests union
             //tests/verilator_sim:backdoor_load_test'
```

Run that set directly with:

```bash
TARGETS=$(bazel query '(//tests/cocotb/... except //tests/cocotb/tlul/...) union
  //tests/verilator_sim:core_mini_axi_non_incr_tests union
  //tests/verilator_sim:backdoor_load_test')
bazel test $TARGETS --keep_going
```
