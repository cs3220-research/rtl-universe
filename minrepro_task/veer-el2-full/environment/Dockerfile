# VeeR EL2 Harbor task — all-tests mode.
#
# Stage 1: base toolchain
#   - Verilator (from apt or source)
#   - RISC-V GCC cross-compiler (riscv64-unknown-elf)
#   - Python 3, pip, cocotb, nox, pyuvm, scipy
#   - Spike (RISC-V ISS) for RISCOF
#   - riscof
#   - make, git, picolibc build deps
#
# Stage 2: pre-warm
#   - Copy green source to /app
#   - Generate VeeR config headers
#   - Build verilated model
#   - Run all block-level cocotb tests via nox (populates sim-build caches)
#   - Record total passing test count
#   - Clear /app contents (keep pip cache and build artifacts)
#
# Stage 3: final
#   - Copy stripped skeleton to /app
#   - Scrub any artifacts that leak green implementation
#   - Drop .harbor/ metadata (test counts)

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
        autoconf automake autotools-dev \
        bc bison build-essential ca-certificates cmake \
        curl device-tree-compiler flex \
        gawk gcc-riscv64-unknown-elf git gnupg \
        help2man libexpat-dev libfl-dev libfl2 \
        libgmp-dev libmpfr-dev libmpc-dev libboost-regex-dev libboost-system-dev \
        libtool make ninja-build pkg-config python3 python3-pip python3-venv \
        python-is-python3 rsync sudo tzdata \
        verilator; \
    # Install Spike (RISC-V ISS) for RISCOF compliance tests
    # Build from source since apt version may be outdated
    git clone --depth 1 https://github.com/riscv-software-src/riscv-isa-sim /tmp/spike-src; \
    mkdir -p /tmp/spike-build; \
    cd /tmp/spike-build; \
    /tmp/spike-src/configure --prefix=/usr/local --with-isa=rv32imac; \
    make -j$(nproc); \
    make install; \
    rm -rf /tmp/spike-src /tmp/spike-build; \
    # Install pk (proxy kernel) for Spike
    git clone --depth 1 https://github.com/riscv-software-src/riscv-pk /tmp/pk-src; \
    mkdir -p /tmp/pk-build; \
    cd /tmp/pk-build; \
    /tmp/pk-src/configure --prefix=/usr/local --host=riscv64-unknown-elf; \
    make -j$(nproc); \
    make install; \
    rm -rf /tmp/pk-src /tmp/pk-build; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs

USER ${_USERNAME}
WORKDIR ${HOME}

# Install Python verification dependencies as user
RUN pip3 install --user --no-cache-dir --break-system-packages \
    nox \
    cocotb==1.8.0 \
    cocotb-bus==0.2.1 \
    cocotb-coverage==1.1.0 \
    cocotb-test==0.2.4 \
    pyuvm==2.9.1 \
    pytest==7.4.1 \
    pytest-html==3.2.0 \
    pytest-timeout==2.1.0 \
    pytest-md==0.2.0 \
    scipy==1.13.1 \
    "git+https://github.com/riscv/riscof"

ENV PATH="${HOME}/.local/bin:${PATH}"

# ---------------------------------------------------------------------------
# Stage 2: pre-warm — build from green source, record test baseline
# ---------------------------------------------------------------------------
FROM base AS warm

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder warm.sh /tmp/warm.sh
RUN chmod +x /tmp/warm.sh && /tmp/warm.sh

# ---------------------------------------------------------------------------
# Stage 3: final image — skeleton at /app, scrubbed build artifacts
# ---------------------------------------------------------------------------
FROM warm AS final

COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # Scrub any sim-build directories that contain compiled green RTL
    # (the Verilator-compiled shared objects encode the implementation)
    find /app/verification/block -name "sim-build*" -type d \
        -exec rm -rf {} + 2>/dev/null || true; \
    find /app/verification/block -name "*.xml" \
        -exec rm -f {} + 2>/dev/null || true; \
    find /app/verification/block -name "*.log" \
        -exec rm -f {} + 2>/dev/null || true; \
    find /tmp -name "warm_sim" -type d \
        -exec rm -rf {} + 2>/dev/null || true; \
    # Set up the skeleton workspace
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    cp /tmp/_total .harbor/total_tests 2>/dev/null || echo 0 > .harbor/total_tests

ENV RV_ROOT=/app
WORKDIR /app
