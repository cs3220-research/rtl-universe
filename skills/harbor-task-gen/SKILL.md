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
- Build system config (BUILD files, Makefiles, CMakeLists, `.core` files,
  `Bender.yml`, WORKSPACE, `.bazelrc`, etc.)
- Documentation (`doc/`, `README.md`)
- Build rules and scripts (`rules/*.bzl`, `Makefile`, etc.)
- Third-party / vendored dependencies
- Toolchain and platform configs

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

### Deciding the Docker Build Strategy

The Dockerfile needs to accomplish two things: (1) install tools so the agent
can build and test, and (2) capture the green test count for scoring. Whether
you also need to **warm a build cache** depends on how the build system works.

A warm stage pre-populates a build cache so the agent's first build reuses
previously compiled artifacts. This is valuable only when the build system has
a **content-addressed cache that survives source changes** — where editing one
`.sv` file recompiles only that module's downstream targets, not the entire
project. The key question: does the build system's cache key depend on the
specific file content, or does any source change invalidate everything?

**Warm the cache when:**
- The cache is keyed per-file or per-action, so unchanged dependencies stay
  cached even as the agent edits implementation files (e.g., bazel's
  `output_base`, cmake with ccache)
- Compiling toolchain dependencies from source is part of the build graph
  rather than pre-installed via apt (e.g., building Verilator or firtool
  from source as a bazel external)
- Rebuilding from cold would waste >10 minutes of agent time

**Skip the warm stage when:**
- The build system recompiles everything when any source changes (no
  incremental rebuild), so the warm cache would be immediately invalidated
- All tools are pre-installed binaries (apt/pip) rather than built during
  the project build
- Total cold build time is already fast (<5 min)

### Stage 1: Base Toolchain (always needed)

```dockerfile
FROM debian:bookworm AS base

RUN apt-get update && apt-get install -y \
    build-essential curl git python3 python3-pip \
    <repo-specific-tools> && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user matching host UID for bind-mount compatibility
ARG _UID=1005
ARG _GID=1005
RUN addgroup --gid ${_GID} builder && \
    adduser --uid ${_UID} --gid ${_GID} --disabled-password builder && \
    mkdir -p /home/builder/.cache && chown builder:builder /home/builder/.cache
RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs
USER builder
```

**Important:** Set `_UID` / `_GID` to match the host user that will run
Harbor. Mismatched UIDs cause permission denied errors on bind-mounted log
directories. Also create `~/.cache` explicitly — some tools (FuseSoC, pip)
need it and it may not exist for a fresh user.

### Capturing the Green Test Count

Whether or not you warm the cache, you need to run the green source to count
passing tests. Use a temporary build stage:

```dockerfile
FROM base AS count
COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh
```

Put the test-counting logic in `count_tests.sh` (a separate script file),
not inline in the Dockerfile RUN command. Docker `RUN` uses `/bin/sh`, which
has different escaping rules from bash — `$()` subshells, `grep -oP` Perl
regex, and loop constructs break in subtle ways when inlined. **Extracting
complex shell into `.sh` files is the single most important thing you can do
to avoid Docker build failures.**

### With Warm Cache (3-stage)

When warming is worthwhile, the count stage doubles as the warm stage:

```dockerfile
FROM base AS warm
COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder warm.sh /tmp/warm.sh
RUN chmod +x /tmp/warm.sh && /tmp/warm.sh
# warm.sh: runs tests, captures count to /tmp/_total, clears /app but
# keeps ~/.cache (the build cache)

FROM warm AS final
COPY --chown=builder:builder skeleton/ /app/
COPY --chown=builder:builder harden.sh /tmp/harden.sh
RUN chmod +x /tmp/harden.sh && /tmp/harden.sh
```

### Without Warm Cache (2-stage)

When warming adds no value, just count and discard:

```dockerfile
FROM base AS count
COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

FROM base AS final
COPY --chown=builder:builder skeleton/ /app/
COPY --from=count /tmp/_total /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests /app/.harbor/all_tests
RUN cd /app && git init -q && git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init
WORKDIR /app
```

### Hardening: What to Scrub and Why

Agents can cheat by reading artifacts from the warm build that encode the
green implementation. There are three categories of leaky artifacts to
identify and scrub for **any** build system:

**Category 1: Generated/elaborated source.** Any file the build produces
that is a transformed representation of the implementation. If deleting the
green source and rebuilding would regenerate these files, they encode the
answers. Examples: Chisel→Verilog output, Verilator→C++ elaboration,
preprocessed RTL file lists, concatenated netlists. Look in the build
system's output directories for generated source files.

**Category 2: Compiled objects and cached actions.** Binary artifacts from
compiling the green source: `.o` files, `.a` archives, linked executables,
simulation binaries, action caches. Less directly useful to agents but
reveal structure. Look for directories that grow large during the warm build
and contain files named after project modules.

**Category 3: Logs and metadata.** Test logs, build event logs, coverage
reports from the warm run. These reveal which tests pass/fail and what
assertion messages look like. Look for log files created during test execution.

**The hardening rule:** after the warm stage, delete everything in categories
1–3 while preserving pre-compiled external tools (simulators, compilers, ISS
binaries) that don't encode project-specific information.

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

The verifier runs after the agent finishes and computes a reward. Two
important patterns:

1. **Always `mkdir -p /logs/verifier` at the start.** Harbor bind-mounts
   this directory from the host at runtime, which can override permissions
   set during Docker build. Defensive mkdir ensures the directory exists
   and is writable regardless of mount state.

2. **Put complex parsing logic in a helper script** rather than relying on
   inline regex. The verifier must parse test output to count passes — this
   varies by build system. A separate `parse_results.sh` or inline Python
   is more reliable than fragile grep chains.

```bash
#!/bin/bash
set -euo pipefail
mkdir -p /logs/verifier
cd /app

# Run all tests (adapt the command to your build system)
<build-system-test-command> 2>&1 | tee /logs/verifier/test.log || true

# Count passing tests (adapt parsing to your test output format)
PASSED=$(<parse pass count from /logs/verifier/test.log>)
TOTAL=$(cat .harbor/total_tests)

# Compute reward
REWARD=$(python3 -c "print(${PASSED:-0} / ${TOTAL:-1})")
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

Always create **two variants** for every repo:

1. **`<name>-full`** — all tests scored. This is the definitive benchmark.
   Agents get partial credit for any tests that pass.

2. **`<name>-e2e`** — only end-to-end / integration tests scored. These are
   the tests that exercise the system as a whole rather than individual
   modules in isolation. This variant focuses on whether the agent can produce
   a working system, not just correct components.

Both variants use the same Docker image and skeleton — only `test.sh` and
`instruction.md` differ (they filter which tests count toward the score).

### How to define the E2E subset

The E2E subset should include tests that **run the integrated design** and
would fail if any major subsystem is broken, while excluding tests that
exercise a single module in isolation:

- **If the repo has distinct test frameworks** (e.g., unit tests via
  ScalaTest/cocotb per-module + integration tests via full-chip Verilator
  simulation), the E2E set is the integration tests.

- **If all tests use the same framework** (e.g., all FuseSoC testbenches),
  the E2E set is the top-level testbench(es) that instantiate the full
  design, excluding sub-module testbenches.

- **If the repo tests via firmware execution** (e.g., RISC-V cores running
  ISA tests through the full pipeline), those are inherently E2E — include
  all of them. Exclude any pure RTL lint or formal checks.

The E2E subset should have at least 3 scored tests and should represent the
tests that matter most for "does this design actually work."

### Capturing the subset

During the count/warm stage, capture both the full test list and the E2E
subset:

```bash
# Full list (all passing tests)
<run all tests, parse pass list> > /tmp/_all_tests
wc -l < /tmp/_all_tests > /tmp/_total

# E2E subset (filtered)
<query or filter for the E2E subset> > /tmp/_e2e_targets
wc -l < /tmp/_e2e_targets > /tmp/_e2e_total
```

The `-e2e` variant's `test.sh` reads `.harbor/e2e_targets` and scores
against `.harbor/e2e_total` instead of the full set.

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

## Pre-Ship Checklist

Before shipping a task, verify:

**Structural:**
- [ ] Dockerfile builds without errors
- [ ] skeleton/ is populated (implementation files zeroed/truncated, tests intact)
- [ ] Complex shell logic is in `.sh` script files, not inline Dockerfile RUN
- [ ] Green test count is captured and stored in `.harbor/total_tests`

**Verifier:**
- [ ] test.sh starts with `mkdir -p /logs/verifier`
- [ ] test.sh writes reward (float in [0,1]) to `/logs/verifier/reward.txt`
- [ ] Test-output parsing works for the specific build system's format

**Hardening:**
- [ ] Category 1 artifacts scrubbed (generated/elaborated source from build)
- [ ] Category 2 artifacts scrubbed (compiled objects, linked binaries)
- [ ] Category 3 artifacts scrubbed (test logs, coverage reports)
- [ ] No green source files remain in the final image
- [ ] `.harbor/` contains only test target lists and counts
- [ ] `warm_src/` is NOT in the final Docker stage
- [ ] Solution script (`solve.sh`) runs on the host, not baked into the image

**Scoring:**
- [ ] Unsolved skeleton produces reward ~0
- [ ] Solved (green source restored) produces reward 1.0

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
