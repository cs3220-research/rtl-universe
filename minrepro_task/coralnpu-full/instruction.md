# Coral NPU — Restore the RTL and Firmware

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

**Your task**: restore as many of the missing sources as you can so that
`bazel test //...` passes as many tests as possible.

## Important: Partial Credit and Persistence

You are scored **proportionally** — every single test you get to pass earns
credit. You do NOT need to complete the entire project. Even restoring a
handful of modules that pass their unit tests is valuable progress.

**Do not give up or stop early because the task looks large.** Work
incrementally: pick a module, read its test spec, implement it, verify it
passes, then move to the next one. You have up to 24 hours. Use all of it.
The best strategy is to start with the simplest, most self-contained modules
(e.g., `hdl/chisel/src/common/` has many small independent modules with
clear test specs) and work outward.

## Environment

- `bazel 7.4.1`, `openjdk-17`, `clang-19`, `python3.11`, `srecord`, `xxd`
  are pre-installed.
- The bazel output_base is pre-warmed (external repos + Verilator + firtool +
  TFLite-micro are already built). Your first `bazel test` invocation should
  start executing cocotb sims within a few minutes rather than rebuilding the
  toolchain.
- `/app` is a fresh git repo with one `init` commit — feel free to use it.

## Scoring

Reward is **proportional**: `passed_test_targets / total_test_targets` (float
in `[0, 1]`). Getting a subset of targets to build and pass still earns partial
credit. The denominator is the count of `*_test` targets bazel discovers under
`//...` after default tag filtering (`-vcs,-synthesis,-power`). On a green
checkout this is 305.

## Useful commands

```bash
bazel test //...                              # run everything
bazel query 'kind(".*_test", //...)' | wc -l  # list all test targets
bazel test //hdl/chisel/src/common:fma_test   # run a single Chisel unit test
bazel test //tests/cocotb:core_mini_axi_sim_cocotb_core_mini_axi_isa_test  # a single cocotb target
```

Check `rules/` for the project-specific bazel rules (`chisel.bzl`,
`coco_tb.bzl`, `coralnpu_v2.bzl`) — BUILD files already reference the targets
you need to produce.
