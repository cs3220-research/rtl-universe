---
name: harbor-task-gen
description: >
  Convert an RTL/hardware repository into a Harbor benchmark task. Use this skill whenever
  the user wants to create a coding benchmark from a hardware repo, generate Harbor tasks,
  build agent evaluation infrastructure, or convert a Chisel/Verilog/VHDL project into a
  test-the-agent challenge. Also trigger when the user mentions "harbor task", "benchmark
  task", "skeleton", "agent evaluation", or wants to strip implementation from an RTL repo
  while keeping tests.
---

# Harbor Task Generator

Convert an RTL/hardware repository with an existing test suite into a Harbor
benchmark task that measures how well a coding agent can restore stripped
implementation code.

## How It Works

The core idea: take a working repo, **strip the implementation** (RTL source,
firmware, glue logic) while **keeping the tests**, then package everything so
Harbor can:

1. Build a Docker image with a pre-warmed build cache
2. Drop an agent into the stripped workspace
3. Let the agent write code to make tests pass
4. Score proportionally: `passed_tests / total_tests`

The agent sees test specs, BUILD files, documentation, and toolchain — but zero
implementation. Partial credit is awarded for any tests that pass.

## Pipeline Overview

```
Industry Repo ──► Analyze ──► Skeleton ──► Dockerfile ──► task.toml
                                                            │
                                    instruction.md ◄────────┤
                                    test.sh (verifier) ◄────┤
                                    solve.sh (solution) ◄───┘
                                         │
                                    Sanity Check ──► Hardening Audit ──► Done
```

---

## Phase 1: Analyze the Repository

Before stripping anything, understand the repo thoroughly.

### 1.1 Identify the Build System

RTL repos commonly use:
- **Bazel** (with rules like `chisel.bzl`, `coco_tb.bzl`, Verilator rules)
- **CMake** + Verilator/VCS
- **Make** with custom flows
- **FuseSoC** / **Edalize**
- **sbt** for pure Chisel projects

Find the build entry point and understand how tests are invoked:
```bash
# Bazel
bazel query 'kind(".*_test", //...)' | wc -l

# CMake
cmake --build build && ctest --test-dir build

# Make
make test
```

### 1.2 Map the Test Infrastructure

Categorize every test target:

| Category | Example | Typical Count |
|----------|---------|---------------|
| **Unit tests** (Chisel/ScalaTest) | `hdl/chisel/src/common:fma_test` | Tens |
| **Component testbenches** (Verilator C++) | `tests/verilator_sim:dbus2axi_tb` | Handful |
| **E2E cocotb simulations** | `tests/cocotb:core_mini_axi_sim_*` | Hundreds |
| **Firmware/software tests** | `sw/opt/litert-micro/test:conv_sim_test` | Tens |
| **FPGA utility tests** | `fpga:check_pins_test` | Few |

Record the **full green test count** — this becomes the scoring denominator.

### 1.3 Identify What to Strip

**Strip** (remove from skeleton, agent must recreate):
- RTL source: Chisel `.scala` (NOT `*Test.scala` / `*Spec.scala`), Verilog `.sv`/`.v`
- Firmware: C/C++ under `sw/` (NOT test files)
- Examples and FPGA integration glue
- Hand-written Verilog modules

**Keep** (stays in skeleton):
- All test files (`*Test*`, `*Spec*`, `*_test.*`, `*_tb.*`)
- Test utilities and helpers
- BUILD files, WORKSPACE, `.bazelrc`
- Documentation (`doc/`, `README.md`)
- Bazel rules (`rules/*.bzl`)
- Third-party dependencies (`third_party/`)
- Toolchain configs
- Platform definitions

### 1.4 Estimate Resource Requirements

Run the full test suite on the green source and measure:
- **Build time** (cold + warm cache)
- **Peak memory** during Verilator compilation
- **Disk usage** of the build cache (bazel `output_base` can be 20-50GB)
- **Test execution time** (for verifier timeout)

---

## Phase 2: Create the Skeleton

### 2.1 Directory Structure

```
minrepro_task/
├── .skeleton/           # Canonical stripped workspace
├── tools/
│   └── sync-skeleton.sh # Regenerates task environments from sources
├── <task-name>/
│   ├── task.toml
│   ├── instruction.md
│   ├── README.md
│   ├── environment/
│   │   ├── Dockerfile
│   │   ├── skeleton/    # (synced from .skeleton/)
│   │   └── warm_src/    # (synced from green source)
│   ├── tests/
│   │   ├── test.sh      # Verifier entrypoint
│   │   └── test_state.py
│   └── solution/
│       └── solve.sh     # Reference solution
└── <task-name-e2e>/     # Optional: E2E-only variant
    └── ...
```

### 2.2 Strip Implementation Files

Create `.skeleton/` by copying the full repo and removing implementation:

```bash
rsync -a --delete \
  --exclude='.git/' --exclude='bazel-*' \
  "$GREEN_SOURCE"/ ".skeleton"/

# Remove implementation Scala (keep tests)
find .skeleton/hdl/chisel/src -name "*.scala" \
  ! -name "*Test*" ! -name "*Spec*" ! -name "*TestUtils*" \
  -delete

# Remove implementation Verilog
find .skeleton/hdl/verilog -name "*.sv" -o -name "*.v" | \
  while read f; do > "$f"; done  # truncate, don't delete (keeps BUILD refs)

# Remove firmware source (keep test files)
find .skeleton/sw -name "*.cc" -o -name "*.c" -o -name "*.h" | \
  grep -v "test" | grep -v "Test" | while read f; do > "$f"; done

# Remove examples
find .skeleton/examples -type f -name "*.cc" -o -name "*.py" | \
  while read f; do > "$f"; done
```

Adapt the stripping patterns to the specific repo. The principle: if a BUILD
file references a source file as a `src`, strip it. If it's in `test_srcs` or
a test rule, keep it.

### 2.3 Create sync-skeleton.sh

This script regenerates `environment/skeleton/` and `environment/warm_src/` for
each task variant from the canonical sources. See the reference implementation
in `minrepro_task/tools/sync-skeleton.sh`.

---

## Phase 3: Create the Dockerfile

The Dockerfile uses a **3-stage build** to pre-warm the build cache:

### Stage 1: Base Toolchain

Install all build dependencies. For a typical RTL repo:
```dockerfile
FROM debian:bookworm AS base

# System packages: build tools, Java (for bazel), Python, HDL tools
RUN apt-get update && apt-get install -y \
    build-essential curl git openjdk-17-jdk-headless \
    python3 python3-pip python3-venv clang lld \
    <repo-specific-deps> && \
    # Install bazel (or other build system)
    ...

# Create non-root user matching host UID for bind-mount compatibility
ARG _UID=1005
ARG _GID=1005
RUN addgroup --gid ${_GID} builder && \
    adduser --uid ${_UID} --gid ${_GID} --disabled-password builder
RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs
USER builder
```

**Important:** Set `_UID` / `_GID` to match the host user that will run
Harbor. Mismatched UIDs cause permission denied errors on bind-mounted log
directories.

### Stage 2: Pre-warm Build Cache

Copy the full green source and run the complete test suite. This populates
the build cache so the agent's first build reuses pre-compiled tools
(Verilator, firtool, TFLM, etc.):

```dockerfile
FROM base AS warm
COPY --chown=builder:builder warm_src/ /app/
RUN cd /app && \
    git init -q && git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init && \
    # Run full build + test to warm cache
    bazel test //... --keep_going --test_output=errors 2>&1 \
      | tee /tmp/warm.log | tail -20 || true && \
    # Capture baseline test set
    grep -E "^//[^ ]+ +PASSED in " /tmp/warm.log \
      | awk '{print $1}' | sort -u > /tmp/_all_tests && \
    wc -l < /tmp/_all_tests > /tmp/_total && \
    # Clear /app but keep build cache
    find /app -mindepth 1 -maxdepth 1 -not -name '.cache' \
      -exec rm -rf {} +
```

### Stage 3: Final Image with Hardening

Copy the skeleton and **scrub warm-build artifacts** that could leak the
green implementation to the agent:

```dockerfile
FROM warm AS final
COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # ── HARDENING: Scrub warm-build artifacts ──
    # Sandbox stash: generated Verilog/binaries from warm Chisel build
    find ~/.cache/bazel -path "*/sandbox/sandbox_stash" -type d \
      -exec rm -rf {} + 2>/dev/null || true; \
    # Test logs from warm run (leak pass/fail patterns)
    find ~/.cache/bazel -path "*/testlogs" -type d \
      -exec rm -rf {} + 2>/dev/null || true; \
    # Project-specific build outputs (generated .sv, .jar, .elf)
    # Keep external tool binaries (Verilator, firtool, etc.)
    for d in hdl tests sw examples fpga build; do \
        find ~/.cache/bazel -path "*/bin/$d" -type d \
          -exec rm -rf {} + 2>/dev/null || true; \
    done; \
    # ── Set up skeleton workspace ──
    cd /app; \
    git init -q; git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    mv /tmp/_all_tests .harbor/all_tests; \
    mv /tmp/_total .harbor/total_tests

WORKDIR /app
```

### Why Hardening Matters

Without scrubbing, agents will discover and exploit cached build artifacts:
- **Sandbox stash**: Contains generated Verilog from the warm Chisel build.
  Agents can read these to reverse-engineer the correct implementation.
- **Test logs**: Reveal which tests pass/fail and assertion messages.
- **Build outputs**: Generated `.sv`, `.jar`, compiled `.elf` files from the
  green source give away the answers.
- **External deps** (`external/`): These are open-source third-party code
  (Verilator, TFLite-Micro reference kernels). Kept for build speed; accessing
  them is borderline but not direct cheating.

In our testing, an unhardened task scored 95.8% while the same hardened task
scored ~9% — the difference was almost entirely cache exploitation.

---

## Phase 4: Create Task Configuration

### task.toml

```toml
version = "1.0"

[verifier]
# Must be long enough for bazel test //... with warm cache.
# E2E cocotb sims can take 100s+ each. Measure on green source and 3x.
timeout_sec = 10800.0

[agent]
# 24h default — agents often need 10+ hours for complex RTL tasks.
timeout_sec = 86400.0

[environment]
# Generous for Verilator compilation + large bazel cache
build_timeout_sec = 5400.0
cpus = 8
memory_mb = 32768
storage_mb = 81920
```

### instruction.md

Write clear instructions for the agent. Include:
1. What the repo is and what it does
2. What's been stripped (and what's been kept)
3. How scoring works (proportional, partial credit)
4. Useful commands to run tests
5. Pointers to documentation and BUILD files

See `minrepro_task/coralnpu/instruction.md` for a reference.

### test.sh (Verifier)

The verifier runs after the agent finishes and computes a reward:

```bash
#!/bin/bash
set -euo pipefail
cd /app

# Run all tests
bazel test //... --keep_going --test_output=errors 2>&1 \
  | tee /logs/verifier/bazel.log | tail -20 || true

# Count passing tests
PASSED=$(grep -cE "^//[^ ]+ +(\(cached\) )?PASSED in " \
  /logs/verifier/bazel.log || echo 0)
TOTAL=$(cat .harbor/total_tests)

# Compute reward
REWARD=$(python3 -c "print($PASSED / $TOTAL)")
echo "$REWARD" > /logs/verifier/reward.txt
```

### solve.sh (Reference Solution)

The reference solution copies the green source back in:

```bash
#!/bin/bash
# Run on Harbor host — copies warm_src into container's /app
docker cp environment/warm_src/. "$CONTAINER_ID:/app/"
docker exec "$CONTAINER_ID" bash -c 'cd /app && git add -A && \
  git -c user.email=x@x -c user.name=x commit -q -m solve'
```

---

## Phase 5: Task Variants

Consider creating multiple task variants with different scoring subsets:

| Variant | Scored Targets | Purpose |
|---------|---------------|---------|
| `<name>` (all) | All `*_test` targets | Full difficulty benchmark |
| `<name>-e2e` | Only E2E/integration tests | Focuses on system-level correctness |
| `<name>-unit` | Only unit tests | Easier; tests module-level code |

For E2E variants, capture the scored target set during the warm stage:

```bash
bazel query '<e2e-target-query>' | sort -u > /tmp/_e2e_targets
comm -12 /tmp/_all_tests /tmp/_e2e_raw > /tmp/_e2e_targets
```

---

## Phase 6: Sanity Check

Before considering the task done, verify both extremes:

### 6.1 Unsolved Score (should be ~0)

Run the verifier on the bare skeleton without any agent modifications:
```bash
docker run --rm <image> bash -c 'cd /app && bazel test //... --keep_going 2>&1 | tail -5'
```

A small number of tests (1-3) may pass if they test host-side utilities that
don't depend on implementation code. Document these as freebies.

### 6.2 Solved Score (should be 1.0)

Run `solve.sh` to copy the green source in, then run the verifier:
```bash
# Copy green source into container
docker cp warm_src/. <container>:/app/
# Run verifier
docker exec <container> bash /tests/test.sh
cat /logs/verifier/reward.txt  # Should be 1.000000
```

### 6.3 Quick Agent Smoke Test

Run a short agent trial (1h timeout) to verify the infrastructure works:
```bash
CREDS_DIR=/tmp/harbor-claude-creds
mkdir -p "$CREDS_DIR"
cp ~/.claude/.credentials.json "$CREDS_DIR/.credentials.json"
chmod 700 "$CREDS_DIR" && chmod 600 "$CREDS_DIR/.credentials.json"

uvx harbor run \
  --path <task-dir> \
  --agent claude-code --model sonnet \
  -n 1 -y \
  --agent-timeout-multiplier 0.04 \
  --mounts-json '["/tmp/harbor-claude-creds:/home/builder/.claude"]' \
  --ae CLAUDE_CONFIG_DIR=/home/builder/.claude \
  --artifact /app
```

Verify: agent starts, authenticates, can read files, can run build commands,
verifier produces a reward.

---

## Phase 7: Standardized Run Infrastructure

Create a run script for easy benchmarking:

```bash
#!/bin/bash
# run_benchmark.sh — Run a Harbor benchmark with standard settings
TASK_PATH="${1:?Usage: run_benchmark.sh <task-path> [model]}"
MODEL="${2:-sonnet}"

# Refresh credentials
CREDS_DIR=/tmp/harbor-claude-creds
mkdir -p "$CREDS_DIR"
cp ~/.claude/.credentials.json "$CREDS_DIR/.credentials.json"
chmod 700 "$CREDS_DIR" && chmod 600 "$CREDS_DIR/.credentials.json"

uvx harbor run \
  --path "$TASK_PATH" \
  --agent claude-code \
  --model "$MODEL" \
  -n 1 -y \
  --mounts-json "[\"$CREDS_DIR:/home/builder/.claude\"]" \
  --ae CLAUDE_CONFIG_DIR=/home/builder/.claude \
  --artifact /app
```

---

## Anti-Cheat Checklist

Before shipping a task, verify these hardening measures:

- [ ] `sandbox/sandbox_stash/` scrubbed in Dockerfile Stage 3
- [ ] `testlogs/` scrubbed in Dockerfile Stage 3
- [ ] Project-specific `bin/` outputs scrubbed (generated Verilog, JARs, ELFs)
- [ ] No green source files remain in the image (check with `find /app`)
- [ ] `.harbor/` directory only contains test target lists and counts
- [ ] Docker image doesn't contain the full green commit history
- [ ] External deps (`external/`) are third-party only, not project code
- [ ] `warm_src/` is NOT included in the final Docker stage
- [ ] Solution script (`solve.sh`) runs on the host, not baked into the image

---

## Troubleshooting

**UID mismatch (permission denied on /logs)**:
Set `_UID`/`_GID` in the Dockerfile to match the host user running Harbor.
Check with `id` on the host.

**OAuth token expiry during long runs**:
Mount `~/.claude/.credentials.json` into the container (has refresh token)
and set `CLAUDE_CONFIG_DIR` via `--ae`. Don't use `CLAUDE_CODE_OAUTH_TOKEN`
env var — it doesn't support refresh.

**Verifier timeout**:
Measure `bazel test //...` time on the green source with warm cache and set
the verifier timeout to at least 3x that value.

**Agent exits early**:
Claude Code in `--print` mode decides when to stop. The agent may declare
victory after partial progress. This is a known behavioral issue — not a
task configuration problem.

**`skeleton/` or `warm_src/` missing**:
Run `tools/sync-skeleton.sh` before building the Docker image.
