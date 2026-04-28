# CVA6 Harbor task — all-tests mode (228 CI regression tests).
#
# Stage 1 (base): Install toolchain, Verilator v5.008 (from source), RISC-V
#   GCC, Spike ISS, Python riscv-dv dependencies.
#
# Stage 2 (warm): Copy green source, build the Verilator model, build
#   riscv-tests binaries, run the full CI test suite, record the passing count.
#
# Stage 3 (final): Copy stripped skeleton, scrub compiled green artifacts,
#   keep the /tools directory and pre-built test binaries, drop .harbor/ metadata.
#
# NOTE: Set _UID/_GID to match the host user that runs Harbor.
#       Mismatch causes permission errors on bind-mounted /logs.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC

ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

# ---------------------------------------------------------------------------
# System packages
# ---------------------------------------------------------------------------
RUN set -eux; \
    ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime; \
    echo "${TZ}" > /etc/timezone; \
    echo 'APT::Get::Assume-Yes "true";' > /etc/apt/apt.conf.d/90assumeyes; \
    apt-get update; \
    apt-get install -y -qq \
        autoconf automake autotools-dev bc bison build-essential \
        ca-certificates cmake curl device-tree-compiler flex \
        gawk git gnupg help2man libboost-regex-dev libboost-system-dev \
        libexpat-dev libfl-dev libfl2 libgmp-dev libmpc-dev libmpfr-dev \
        libtool make ninja-build pkg-config python3 python3-pip \
        python3-venv python-is-python3 rsync sudo tzdata wget; \
    rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Create non-root builder user
# ---------------------------------------------------------------------------
RUN addgroup --gid ${_GID} ${_USERNAME} && \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME} && \
    chown ${_USERNAME}:${_USERNAME} ${HOME}

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs
RUN mkdir -p /tools && chown -R ${_UID}:${_GID} /tools

USER ${_USERNAME}
WORKDIR ${HOME}

# ---------------------------------------------------------------------------
# Build Verilator v5.008 from source (matching CVA6 CI requirement)
# ---------------------------------------------------------------------------
RUN set -eux; \
    export VERILATOR_INSTALL_DIR=/tools/verilator; \
    cd /tmp; \
    git clone https://github.com/verilator/verilator.git verilator-src; \
    cd verilator-src; \
    git checkout v5.008; \
    autoconf; \
    ./configure --prefix="${VERILATOR_INSTALL_DIR}"; \
    make -j$(nproc); \
    make install; \
    cd /tmp; \
    rm -rf verilator-src; \
    echo "Verilator installed: $("${VERILATOR_INSTALL_DIR}/bin/verilator" --version)"

# ---------------------------------------------------------------------------
# Build RISC-V GCC toolchain (rv64gc Linux ABI) — or install pre-built
# We use a pre-built release from riscv-collab for speed.
# ---------------------------------------------------------------------------
RUN set -eux; \
    cd /tmp; \
    TOOLCHAIN_URL="https://github.com/riscv-collab/riscv-gnu-toolchain/releases/download/2024.02.02/riscv64-elf-ubuntu-22.04-gcc-nightly-2024.02.02-nightly.tar.gz"; \
    wget -q "${TOOLCHAIN_URL}" -O toolchain.tar.gz; \
    mkdir -p /tools/riscv; \
    tar xzf toolchain.tar.gz -C /tools/riscv --strip-components=1; \
    rm toolchain.tar.gz; \
    echo "Toolchain: $(/tools/riscv/bin/riscv64-unknown-elf-gcc --version | head -1)"

# ---------------------------------------------------------------------------
# Install Python dependencies (riscv-dv)
# ---------------------------------------------------------------------------
RUN pip3 install --break-system-packages --user --no-cache-dir \
    pyyaml \
    pytest \
    mako

# ---------------------------------------------------------------------------
# Set environment for subsequent stages
# ---------------------------------------------------------------------------
ENV RISCV=/tools/riscv
ENV VERILATOR_INSTALL_DIR=/tools/verilator
ENV PATH="/tools/verilator/bin:/tools/riscv/bin:${HOME}/.local/bin:${PATH}"

# ===========================================================================
# Stage 2: Pre-warm — build from green source, compile tests, record baseline
# ===========================================================================
FROM base AS warm

# Copy green source (populated by sync-skeleton.sh)
COPY --chown=builder:builder warm_src/ /app/

RUN set -eux; \
    export CVA6_REPO_DIR=/app; \
    export RISCV=/tools/riscv; \
    export VERILATOR_INSTALL_DIR=/tools/verilator; \
    export SPIKE_INSTALL_DIR=/tools/spike; \
    export NUM_JOBS=$(nproc); \
    export PATH="/tools/verilator/bin:/tools/riscv/bin:${PATH}"; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init;

# ---------------------------------------------------------------------------
# Populate git submodules that are not present in the shallow clone.
# The verilate (top-level APU testharness) needs:
#   core/: hpdcache, cvfpu
#   corev_apu/: axi_mem_if, riscv-dbg, rv_plic, register_interface
#   corev_apu/fpga/src/: axi2apb, apb_timer, axi_slice
# ---------------------------------------------------------------------------
RUN set -eux; \
    cd /app; \
    git submodule update --init --recursive \
        core/cache_subsystem/hpdcache \
        core/cvfpu \
        corev_apu/axi_mem_if \
        corev_apu/riscv-dbg \
        corev_apu/rv_plic \
        corev_apu/register_interface \
        corev_apu/fpga/src/axi2apb \
        corev_apu/fpga/src/apb_timer \
        corev_apu/fpga/src/axi_slice \
        corev_apu/tb/common_verification \
        2>&1 | tail -20 || { \
        echo "NOTE: some submodules failed; will clone manually..."; \
    }; \
    # Manually clone any that git submodule update couldn't reach
    # (may happen if the repo isn't a full git clone with submodule refs)
    HPDCACHE_HASH="acf97c95f2b0bf895143ed5559b04629a474548d"; \
    [ -f core/cache_subsystem/hpdcache/rtl/hpdcache.Flist ] || { \
        rm -rf core/cache_subsystem/hpdcache; \
        git clone https://github.com/openhwgroup/cv-hpdcache.git \
            core/cache_subsystem/hpdcache; \
        cd core/cache_subsystem/hpdcache && \
            git checkout "${HPDCACHE_HASH}" 2>/dev/null || true; \
        cd /app; }; \
    CVFPU_HASH="a74d99a32bd11ce0b05a82b0d6d26f98df3bba65"; \
    [ -f core/cvfpu/src/fpnew_pkg.sv ] || { \
        rm -rf core/cvfpu; \
        git clone https://github.com/openhwgroup/cvfpu.git core/cvfpu; \
        cd core/cvfpu && git checkout "${CVFPU_HASH}" 2>/dev/null || true; \
        cd /app; }; \
    [ -d corev_apu/axi_mem_if/src ] || { \
        git clone https://github.com/pulp-platform/axi_mem_if.git corev_apu/axi_mem_if; }; \
    [ -d corev_apu/riscv-dbg/src ] || { \
        git clone https://github.com/pulp-platform/riscv-dbg.git corev_apu/riscv-dbg; \
        cd corev_apu/riscv-dbg && git submodule update --init --recursive 2>/dev/null || true; \
        cd /app; }; \
    [ -d corev_apu/rv_plic/rtl ] || { \
        git clone https://github.com/pulp-platform/rv_plic.git corev_apu/rv_plic; }; \
    [ -d corev_apu/register_interface/src ] || { \
        git clone https://github.com/pulp-platform/register_interface.git \
            corev_apu/register_interface; }; \
    echo "Submodule population complete"

# ---------------------------------------------------------------------------
# Build Spike ISS
# ---------------------------------------------------------------------------
RUN set -eux; \
    export SPIKE_INSTALL_DIR=/tools/spike; \
    git clone --depth=1 https://github.com/riscv-software-src/riscv-isa-sim /tmp/spike-src; \
    cd /tmp/spike-src; \
    mkdir build && cd build; \
    ../configure --prefix="${SPIKE_INSTALL_DIR}"; \
    make -j$(nproc) yaml-cpp-static yaml-cpp; \
    make -j$(nproc); \
    make install; \
    cd /; \
    rm -rf /tmp/spike-src; \
    echo "Spike: $(${SPIKE_INSTALL_DIR}/bin/spike --version 2>&1 | head -1)"

# ---------------------------------------------------------------------------
# Build riscv-tests from repo (used by the verifier)
# ---------------------------------------------------------------------------
RUN set -eux; \
    export CVA6_REPO_DIR=/app; \
    cd /app; \
    # Initialize git submodule for riscv-tests, or clone if not present
    mkdir -p verif/tests; \
    if [ ! -d verif/tests/riscv-tests/.git ]; then \
        TESTS_REPO="https://github.com/riscv/riscv-tests.git"; \
        TESTS_HASH="f92842f91644092960ac7946a61ec2895e543cec"; \
        git clone "${TESTS_REPO}" verif/tests/riscv-tests; \
        cd verif/tests/riscv-tests; \
        git checkout "${TESTS_HASH}"; \
        if [ -f /app/verif/regress/riscv-tests.patch ]; then \
            git apply /app/verif/regress/riscv-tests.patch || true; \
        fi; \
        git submodule update --init --recursive; \
        if [ -f /app/verif/regress/riscv-tests-env.patch ]; then \
            git apply --directory=env /app/verif/regress/riscv-tests-env.patch || true; \
        fi; \
        cd /app; \
    fi; \
    # Build the test binaries
    mkdir -p tmp/riscv-tests; \
    cd verif/tests/riscv-tests; \
    autoconf; \
    ./configure \
        --prefix=/app/tmp/riscv-tests/build \
        --with-xlen=64; \
    make -j$(nproc) isa benchmarks || true; \
    make install || true; \
    echo "riscv-tests built"

# ---------------------------------------------------------------------------
# Build the Verilator model from green source
# ---------------------------------------------------------------------------
RUN set -eux; \
    export CVA6_REPO_DIR=/app; \
    export RISCV=/tools/riscv; \
    export VERILATOR_INSTALL_DIR=/tools/verilator; \
    export SPIKE_INSTALL_DIR=/tools/spike; \
    export PATH="/tools/verilator/bin:/tools/riscv/bin:${PATH}"; \
    export NUM_JOBS=$(nproc); \
    cd /app; \
    make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=${NUM_JOBS} \
        2>&1 | tee /tmp/verilate_warm.log | tail -30 || { \
        echo "ERROR: verilate failed on green source"; \
        cat /tmp/verilate_warm.log | tail -50; \
        exit 1; \
    }; \
    echo "Variane_testharness built: $(ls -la /app/work-ver/Variane_testharness)"

# ---------------------------------------------------------------------------
# Run the full CI test suite and record the passing count
# ---------------------------------------------------------------------------
RUN set -eux; \
    export CVA6_REPO_DIR=/app; \
    export PATH="/tools/verilator/bin:/tools/riscv/bin:${PATH}"; \
    cd /app; \
    HARNESS=/app/work-ver/Variane_testharness; \
    RISCV_TEST_DIR=/app/tmp/riscv-tests/build/isa; \
    RISCV_BENCH_DIR=/app/tmp/riscv-tests/build/benchmarks; \
    PASSED=0; \
    run_test() { \
        local name="$1"; local elf="$2"; \
        [ -f "$elf" ] && "${HARNESS}" "${elf}" >/dev/null 2>&1 && { \
            echo "PASS: ${name}"; return 0; } || { echo "FAIL: ${name}"; return 1; }; \
    }; \
    for LIST in /app/ci/riscv-asm-tests.list /app/ci/riscv-amo-tests.list \
                /app/ci/riscv-mul-tests.list /app/ci/riscv-fp-tests.list; do \
        while IFS= read -r t || [ -n "$t" ]; do \
            [ -z "$t" ] || [[ "$t" == \#* ]] && continue; \
            run_test "$t" "${RISCV_TEST_DIR}/$t" && PASSED=$((PASSED+1)) || true; \
        done < "$LIST"; \
    done; \
    while IFS= read -r t || [ -n "$t" ]; do \
        [ -z "$t" ] || [[ "$t" == \#* ]] && continue; \
        run_test "$t" "${RISCV_BENCH_DIR}/$t" && PASSED=$((PASSED+1)) || true; \
    done < /app/ci/riscv-benchmarks.list; \
    echo "Warm run: ${PASSED} tests passed" | tee /tmp/_warm_result; \
    echo "${PASSED}" > /tmp/_total; \
    echo "total_tests=${PASSED}"

# ---------------------------------------------------------------------------
# Clear /app contents (keep tools), preserve riscv-tests binaries
# ---------------------------------------------------------------------------
RUN set -eux; \
    # Move pre-built test binaries out of /app so we can clear /app
    cp -r /app/tmp/riscv-tests/build /tmp/riscv-tests-build || true; \
    # Clear /app (agent gets a clean skeleton)
    find /app -mindepth 1 -maxdepth 1 \
        ! -name '.local' ! -name '.cache' \
        -exec rm -rf {} + 2>/dev/null || true

# ===========================================================================
# Stage 3: Final image — stripped skeleton, scrubbed artifacts
# ===========================================================================
FROM warm AS final

# Copy the stripped skeleton (populated by sync-skeleton.sh)
COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # ---- HARDENING: Scrub compiled RTL artifacts from the warm stage ----
    # Remove the compiled Verilator model (encodes the green implementation)
    rm -rf /app/work-ver/ 2>/dev/null || true; \
    rm -rf /app/work-dpi/ 2>/dev/null || true; \
    # Remove any sim log files that reveal pass/fail patterns
    find /app -name "*.log" -not -path "*/verif/tests/*" \
        -exec rm -f {} + 2>/dev/null || true; \
    # ---- Restore submodule content needed for the agent's Verilator build ----
    # cvfpu and hpdcache are vendored IP the agent does NOT write;
    # they must be present for the Flist.cva6 to resolve correctly.
    # Re-clone them into the skeleton since skeleton/ didn't have them populated.
    HPDCACHE_HASH="acf97c95f2b0bf895143ed5559b04629a474548d"; \
    if [ ! -d /app/core/cache_subsystem/hpdcache/rtl ]; then \
        git clone https://github.com/openhwgroup/cv-hpdcache.git \
            /app/core/cache_subsystem/hpdcache; \
        cd /app/core/cache_subsystem/hpdcache; \
        git checkout "${HPDCACHE_HASH}" 2>/dev/null || true; \
        rm -rf .git; \
    fi; \
    CVFPU_HASH="a74d99a32bd11ce0b05a82b0d6d26f98df3bba65"; \
    if [ ! -d /app/core/cvfpu/src ]; then \
        git clone https://github.com/openhwgroup/cvfpu.git /app/core/cvfpu; \
        cd /app/core/cvfpu; \
        git checkout "${CVFPU_HASH}" 2>/dev/null || true; \
        rm -rf .git; \
    fi; \
    # corev_apu submodules (needed by the top-level Makefile verilate target)
    [ -d /app/corev_apu/axi_mem_if/src ] || \
        git clone --quiet https://github.com/pulp-platform/axi_mem_if.git \
            /app/corev_apu/axi_mem_if; \
    [ -d /app/corev_apu/riscv-dbg/src ] || { \
        git clone --quiet https://github.com/pulp-platform/riscv-dbg.git \
            /app/corev_apu/riscv-dbg; \
        cd /app/corev_apu/riscv-dbg && \
            git submodule update --init --recursive 2>/dev/null || true && \
            cd /app; }; \
    [ -d /app/corev_apu/rv_plic/rtl ] || \
        git clone --quiet https://github.com/pulp-platform/rv_plic.git \
            /app/corev_apu/rv_plic; \
    [ -d /app/corev_apu/register_interface/src ] || \
        git clone --quiet https://github.com/pulp-platform/register_interface.git \
            /app/corev_apu/register_interface; \
    # ---- Restore pre-built test binaries (needed by the verifier) ----
    mkdir -p /app/tmp/riscv-tests; \
    cp -r /tmp/riscv-tests-build /app/tmp/riscv-tests/build 2>/dev/null || true; \
    # ---- Set up workspace ----
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    cp /tmp/_total .harbor/total_tests 2>/dev/null || echo 228 > .harbor/total_tests; \
    echo "Final image ready. total_tests=$(cat .harbor/total_tests)"

ENV CVA6_REPO_DIR=/app
ENV RISCV=/tools/riscv
ENV VERILATOR_INSTALL_DIR=/tools/verilator
ENV SPIKE_INSTALL_DIR=/tools/spike
ENV PATH="/tools/verilator/bin:/tools/riscv/bin:/home/builder/.local/bin:${PATH}"
ENV NUM_JOBS=8

WORKDIR /app
