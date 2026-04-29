# CVA6 RISC-V Core — Harbor benchmark task
#
# Repository: https://github.com/openhwgroup/cva6
# Build system: Make + Bender (dependency management) + Verilator
# Tests: RISC-V ISA test suite (asm, amo, mul, fp) + benchmarks
#        run through a Verilator simulation of the CVA6 core.
#
# Test counts (from ci/*.list files):
#   asm tests:        110
#   amo tests:         38
#   mul tests:         26
#   fp tests:          46
#   benchmarks:         8
#   Total:            228
#
# Decision: 2-stage (no warm cache).
#   Verilator elaboration of the full CVA6 core (~200k lines SV) takes
#   several minutes but the resulting Variane_testharness binary encodes
#   the full RTL implementation. Keeping it in the final image would be
#   a Category 2 hardening leak — agents could run tests without
#   implementing any RTL. Instead, we skip the warm stage and require
#   the agent to build Verilator from scratch.
#
#   The count stage compiles the binary once on the green source,
#   runs all 228 tests, and records the count; then the final stage
#   starts fresh from base with only the skeleton.
#
# Submodule strategy:
#   The repo has 18 git submodules (see .gitmodules). We handle them
#   by including the vendor/ directory and the critical submodule paths
#   directly in warm_src/ and skeleton/. The Docker build does NOT
#   run "git submodule update" at image-build time because there is no
#   network; instead, the skeleton/ and warm_src/ are already populated
#   with submodule contents (via sync-skeleton.sh on the host).
#
# Spike ISS strategy:
#   Spike is used only as a DPI library (libriscv, libfesvr, libdisasm,
#   libyaml-cpp). We install the pre-built Spike from the Ubuntu 22.04
#   package (spike via apt). For the library files needed by the Verilator
#   testbench DPI, we build Spike once from source in a dedicated early
#   stage so the layer is cached and failures are isolated. We pin to the
#   exact commit referenced in ci/install-spike.sh.
#
# NOTE: Set _UID/_GID to match the host user running Harbor.
#       Mismatched UIDs cause permission errors on bind-mounted /logs.

# ===========================================================================
# Stage: spike-builder
#   Build Spike ISS from source at the pinned commit.
#   This produces libriscv.so, libfesvr.so, libdisasm.so, libyaml-cpp.so
#   which are needed by the Verilator testbench DPI.
#   By placing this in a separate stage, the heavy compilation is cached
#   independently of the rest of the image.
# ===========================================================================
FROM debian:bookworm AS spike-builder

ENV DEBIAN_FRONTEND=noninteractive

COPY build_spike.sh /tmp/build_spike.sh
RUN chmod +x /tmp/build_spike.sh && /tmp/build_spike.sh

# ===========================================================================
# Stage: base
#   Install all toolchain dependencies.
# ===========================================================================
FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

COPY install_base.sh /tmp/install_base.sh
RUN chmod +x /tmp/install_base.sh && /tmp/install_base.sh

# Install Bender (PULP dependency manager)
COPY install_bender.sh /tmp/install_bender.sh
RUN chmod +x /tmp/install_bender.sh && /tmp/install_bender.sh

# Copy Spike libraries from spike-builder stage
COPY --from=spike-builder /opt/spike /opt/spike

# Create the non-root builder user and key directories
RUN set -eux; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs; \
    mkdir -p /app && chown -R ${_UID}:${_GID} /app

USER ${_USERNAME}
WORKDIR /app

# ===========================================================================
# Stage: count
#   Copy green source, build Verilator model, run all tests, record count.
#   This stage is heavy but its artifacts are NOT copied to final image
#   (prevents leaking the compiled simulation binary and RTL objects).
# ===========================================================================
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ===========================================================================
# Final stage: skeleton + test metadata only (no simulation binary)
# ===========================================================================
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/

# Grab test metadata from count stage
COPY --from=count /tmp/_total          /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests      /app/.harbor/all_tests
COPY --from=count /tmp/_asm_tests      /app/.harbor/asm_tests
COPY --from=count /tmp/_amo_tests      /app/.harbor/amo_tests
COPY --from=count /tmp/_mul_tests      /app/.harbor/mul_tests
COPY --from=count /tmp/_fp_tests       /app/.harbor/fp_tests
COPY --from=count /tmp/_bench_tests    /app/.harbor/bench_tests

# Initialize a git repository for the skeleton so the agent can commit freely
RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    echo "Final image ready. total_tests=$(cat .harbor/total_tests)"

WORKDIR /app
