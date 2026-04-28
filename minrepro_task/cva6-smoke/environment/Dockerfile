# CVA6 Harbor task — smoke-test mode (10 integer ASM tests).
#
# Same toolchain as the full cva6 task. Only the scored test set differs.
# See cva6/environment/Dockerfile for full commentary.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC

ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

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

RUN addgroup --gid ${_GID} ${_USERNAME} && \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME} && \
    chown ${_USERNAME}:${_USERNAME} ${HOME}

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs
RUN mkdir -p /tools && chown -R ${_UID}:${_GID} /tools

USER ${_USERNAME}
WORKDIR ${HOME}

# Verilator v5.008 from source
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
    cd /tmp; rm -rf verilator-src

# RISC-V toolchain (pre-built)
RUN set -eux; \
    cd /tmp; \
    TOOLCHAIN_URL="https://github.com/riscv-collab/riscv-gnu-toolchain/releases/download/2024.02.02/riscv64-elf-ubuntu-22.04-gcc-nightly-2024.02.02-nightly.tar.gz"; \
    wget -q "${TOOLCHAIN_URL}" -O toolchain.tar.gz; \
    mkdir -p /tools/riscv; \
    tar xzf toolchain.tar.gz -C /tools/riscv --strip-components=1; \
    rm toolchain.tar.gz

RUN pip3 install --user --no-cache-dir pyyaml pytest mako

ENV RISCV=/tools/riscv
ENV VERILATOR_INSTALL_DIR=/tools/verilator
ENV PATH="/tools/verilator/bin:/tools/riscv/bin:${HOME}/.local/bin:${PATH}"

# ===========================================================================
# Stage 2: Pre-warm
# ===========================================================================
FROM base AS warm

COPY --chown=builder:builder warm_src/ /app/

RUN set -eux; \
    cd /app; \
    git init -q; git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init

# Populate git submodules needed for the Verilator APU testharness build
RUN set -eux; \
    cd /app; \
    git submodule update --init --recursive \
        core/cache_subsystem/hpdcache core/cvfpu \
        corev_apu/axi_mem_if corev_apu/riscv-dbg corev_apu/rv_plic \
        corev_apu/register_interface 2>&1 | tail -10 || true; \
    HPDCACHE_HASH="acf97c95f2b0bf895143ed5559b04629a474548d"; \
    [ -f core/cache_subsystem/hpdcache/rtl/hpdcache.Flist ] || { \
        rm -rf core/cache_subsystem/hpdcache; \
        git clone https://github.com/openhwgroup/cv-hpdcache.git \
            core/cache_subsystem/hpdcache; \
        cd core/cache_subsystem/hpdcache && \
            git checkout "${HPDCACHE_HASH}" 2>/dev/null || true && cd /app; }; \
    CVFPU_HASH="a74d99a32bd11ce0b05a82b0d6d26f98df3bba65"; \
    [ -f core/cvfpu/src/fpnew_pkg.sv ] || { \
        rm -rf core/cvfpu; \
        git clone https://github.com/openhwgroup/cvfpu.git core/cvfpu; \
        cd core/cvfpu && git checkout "${CVFPU_HASH}" 2>/dev/null || true && cd /app; }; \
    [ -d corev_apu/axi_mem_if/src ] || \
        git clone https://github.com/pulp-platform/axi_mem_if.git corev_apu/axi_mem_if; \
    [ -d corev_apu/riscv-dbg/src ] || { \
        git clone https://github.com/pulp-platform/riscv-dbg.git corev_apu/riscv-dbg; \
        cd corev_apu/riscv-dbg && git submodule update --init --recursive 2>/dev/null || true && cd /app; }; \
    [ -d corev_apu/rv_plic/rtl ] || \
        git clone https://github.com/pulp-platform/rv_plic.git corev_apu/rv_plic; \
    [ -d corev_apu/register_interface/src ] || \
        git clone https://github.com/pulp-platform/register_interface.git \
            corev_apu/register_interface; \
    echo "submodules ready"

# Build Spike ISS
RUN set -eux; \
    git clone --depth=1 https://github.com/riscv-software-src/riscv-isa-sim /tmp/spike-src; \
    cd /tmp/spike-src; mkdir build && cd build; \
    ../configure --prefix=/tools/spike; \
    make -j$(nproc) yaml-cpp-static yaml-cpp; \
    make -j$(nproc); make install; \
    cd / && rm -rf /tmp/spike-src

# Build riscv-tests
RUN set -eux; \
    mkdir -p /app/verif/tests; \
    TESTS_REPO="https://github.com/riscv/riscv-tests.git"; \
    TESTS_HASH="f92842f91644092960ac7946a61ec2895e543cec"; \
    git clone "${TESTS_REPO}" /app/verif/tests/riscv-tests; \
    cd /app/verif/tests/riscv-tests; \
    git checkout "${TESTS_HASH}"; \
    [ -f /app/verif/regress/riscv-tests.patch ] && \
        git apply /app/verif/regress/riscv-tests.patch || true; \
    git submodule update --init --recursive; \
    [ -f /app/verif/regress/riscv-tests-env.patch ] && \
        git apply --directory=env /app/verif/regress/riscv-tests-env.patch || true; \
    autoconf; \
    ./configure --prefix=/app/tmp/riscv-tests/build --with-xlen=64; \
    make -j$(nproc) isa benchmarks || true; \
    make install || true

# Build Verilator model from green source
RUN set -eux; \
    export CVA6_REPO_DIR=/app; \
    export RISCV=/tools/riscv; \
    export VERILATOR_INSTALL_DIR=/tools/verilator; \
    export SPIKE_INSTALL_DIR=/tools/spike; \
    export PATH="/tools/verilator/bin:/tools/riscv/bin:${PATH}"; \
    cd /app; \
    make verilate target=cv64a6_imafdc_sv39 NUM_JOBS=$(nproc) \
        2>&1 | tee /tmp/verilate_warm.log | tail -20

# Run smoke tests and record total
RUN set -eux; \
    HARNESS=/app/work-ver/Variane_testharness; \
    RISCV_TEST_DIR=/app/tmp/riscv-tests/build/isa; \
    PASSED=0; \
    SMOKE_TESTS="rv64ui-p-add rv64ui-p-addi rv64ui-p-and rv64ui-p-or rv64ui-p-sub rv64ui-p-jal rv64ui-p-jalr rv64ui-p-beq rv64ui-p-lw rv64ui-p-sw"; \
    for t in ${SMOKE_TESTS}; do \
        "${HARNESS}" "${RISCV_TEST_DIR}/${t}" >/dev/null 2>&1 && \
            { echo "PASS: $t"; PASSED=$((PASSED+1)); } || echo "FAIL: $t"; \
    done; \
    echo "${PASSED}" > /tmp/_total; \
    echo "smoke total_tests=${PASSED}"

# Move test binaries out, clear /app
RUN set -eux; \
    cp -r /app/tmp/riscv-tests/build /tmp/riscv-tests-build || true; \
    find /app -mindepth 1 -maxdepth 1 \
        ! -name '.local' ! -name '.cache' \
        -exec rm -rf {} + 2>/dev/null || true

# ===========================================================================
# Stage 3: Final
# ===========================================================================
FROM warm AS final

COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # Scrub compiled RTL (encodes green implementation)
    rm -rf /app/work-ver/ /app/work-dpi/ 2>/dev/null || true; \
    find /app -name "*.log" -not -path "*/verif/tests/*" \
        -exec rm -f {} + 2>/dev/null || true; \
    # Restore vendored submodule content needed for the agent's Verilator build
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
    [ -d /app/corev_apu/axi_mem_if/src ] || \
        git clone --quiet https://github.com/pulp-platform/axi_mem_if.git \
            /app/corev_apu/axi_mem_if; \
    [ -d /app/corev_apu/riscv-dbg/src ] || { \
        git clone --quiet https://github.com/pulp-platform/riscv-dbg.git \
            /app/corev_apu/riscv-dbg && \
        cd /app/corev_apu/riscv-dbg && \
            git submodule update --init --recursive 2>/dev/null || true && \
            cd /app; }; \
    [ -d /app/corev_apu/rv_plic/rtl ] || \
        git clone --quiet https://github.com/pulp-platform/rv_plic.git \
            /app/corev_apu/rv_plic; \
    [ -d /app/corev_apu/register_interface/src ] || \
        git clone --quiet https://github.com/pulp-platform/register_interface.git \
            /app/corev_apu/register_interface; \
    # Restore pre-built test binaries
    mkdir -p /app/tmp/riscv-tests; \
    cp -r /tmp/riscv-tests-build /app/tmp/riscv-tests/build 2>/dev/null || true; \
    cd /app; \
    git init -q; git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    cp /tmp/_total .harbor/total_tests 2>/dev/null || echo 10 > .harbor/total_tests

ENV CVA6_REPO_DIR=/app
ENV RISCV=/tools/riscv
ENV VERILATOR_INSTALL_DIR=/tools/verilator
ENV SPIKE_INSTALL_DIR=/tools/spike
ENV PATH="/tools/verilator/bin:/tools/riscv/bin:/home/builder/.local/bin:${PATH}"
ENV NUM_JOBS=8

WORKDIR /app
