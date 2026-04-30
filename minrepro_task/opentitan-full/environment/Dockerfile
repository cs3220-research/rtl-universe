# OpenTitan Root of Trust — Harbor benchmark task
#
# Repository: https://github.com/lowRISC/opentitan
# Build system: Bazel (MODULE.bazel, bazelisk.sh bootstrap script)
# Tests: Host-side cc_test (DIF drivers, silicon_creator, base libs) +
#        py_test (utility tools, OTBN simulator)
#        These are all Verilator-accessible flows — no commercial simulator needed.
#
# Test scope (approximate counts from BUILD analysis):
#   Base library unit tests:     12 (bitfield, math, CRC, memory, etc.)
#   Runtime library unit tests:   1 (print)
#   DIF unit tests:              42 (mock-based C driver tests per IP block)
#   Crypto unit tests:            5 (AES, hash, keyblob, AES-GCM)
#   JSON unit tests:              2 (ujson)
#   silicon_creator unit tests:  49 (secure boot: ROM, sigverify, drivers)
#   Python utility tests:        10 (tools: FSM encoder, LFSR, ipgen, etc.)
#   OTBN Python sim test:         1 (Trivium stream cipher)
#   Total:                      ~122 tests
#
# What is STRIPPED (agent must implement):
#   - C source implementations: sw/device/lib/dif/dif_*.c
#   - sw/device/lib/base/*.c (math, memory, CRC utilities)
#   - sw/device/lib/crypto/impl/*.c
#   - sw/device/silicon_creator/lib/*.c and lib/drivers/*.c
#   - sw/device/silicon_creator/lib/sigverify/*.c
#   - Python utility source: util/design/lib/*.py, util/ipgen/ipgen.py, etc.
#   - OTBN Python sim: hw/ip/otbn/dv/otbnsim/sim/trivium.py
#
# What is KEPT (tests + infrastructure):
#   - All *_unittest.cc, *_test.cc test files
#   - All BUILD files, MODULE.bazel, bazelisk.sh
#   - .hjson register descriptions (used to generate C headers at build time)
#   - hw/ip/*/data/*.hjson (the register definitions)
#   - All header files (.h) — tests include them
#   - Python test files (*_test.py, test_*.py)
#   - OTBN ISA YAML data files
#
# Decision: 3-stage Dockerfile WITH warm Bazel cache.
#   Bazel is content-addressed: editing one .c file only recompiles that
#   translation unit and its downstream dependencies. The Clang toolchain,
#   GoogleTest, Python interpreter, and external dependencies are cached
#   independently of the project source. This means after stripping implementation
#   files, the agent's incremental builds will be fast — only the stripped .c
#   files and their downstream tests need recompilation.
#
#   The warm stage compiles all targets on the green source, records counts,
#   then we harden by removing project-specific compiled objects while
#   preserving the toolchain cache. This gives the agent fast Clang and
#   dependency resolution while requiring it to recompile the stripped files.
#
# Submodule status: No .gitmodules found in this repo. Third-party deps
#   are vendored in third_party/ and managed by Bazel MODULE.bazel.
#
# UID/GID: Set _UID/_GID to match the host user running Harbor.
#   Mismatched UIDs cause permission denied on /logs bind-mount.

# ===========================================================================
# Stage: base
#   Install system packages and bootstrap Bazel.
# ===========================================================================
FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

COPY install_deps.sh /tmp/install_deps.sh
RUN chmod +x /tmp/install_deps.sh && /tmp/install_deps.sh

# Create non-root builder user
RUN set -eux; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs; \
    mkdir -p /app && chown -R ${_UID}:${_GID} /app; \
    mkdir -p ${HOME}/.cache && chown -R ${_UID}:${_GID} ${HOME}/.cache

USER ${_USERNAME}
WORKDIR /app

# ===========================================================================
# Stage: count (warm cache)
#   Copy green source, run all host-side Bazel tests, capture pass counts.
#   The warm Bazel cache is then hardened before copying to the final stage.
# ===========================================================================
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/

# Run tests and record counts
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# Harden the Bazel cache: remove project-specific compiled objects
# while preserving the toolchain cache (Clang, Python, GoogleTest)
COPY --chown=builder:builder harden.sh /tmp/harden.sh
RUN chmod +x /tmp/harden.sh && /tmp/harden.sh

# Remove the green source — only the cache remains
RUN rm -rf /app/*

# ===========================================================================
# Stage: final
#   Start fresh with skeleton (stripped) source + warm toolchain cache.
# ===========================================================================
FROM base AS final

# Copy hardened Bazel cache from count stage (toolchain only, no impl objects)
COPY --from=count /home/builder/.cache /home/builder/.cache

# Install skeleton (implementation files zeroed, tests intact)
COPY --chown=builder:builder skeleton/ /app/

# Copy test target lists and counts
COPY --from=count /tmp/_total          /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests      /app/.harbor/all_tests
COPY --from=count /tmp/_e2e_tests      /app/.harbor/e2e_tests
COPY --from=count /tmp/_e2e_total      /app/.harbor/e2e_total

# Fix ownership of .harbor/ dir (COPY --from is always root-owned)
# Then initialize a git repository so the agent can commit freely
USER root
RUN chown -R builder:builder /app/.harbor
USER builder
RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    echo "Final image ready. total_tests=$(cat .harbor/total_tests)"

WORKDIR /app
