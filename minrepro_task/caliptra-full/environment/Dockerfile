# Caliptra Root of Trust — Harbor benchmark task.
#
# Build strategy: NO warm cache.
#
# Caliptra uses Verilator (Make-based) for simulation.  The Makefile creates
# obj_dir/ by compiling ALL ~500 RTL files into a single monolithic binary.
# When any RTL file changes (which the agent will do), the entire obj_dir must
# be rebuilt — a warm obj_dir from green source gives the agent zero benefit.
# Instead we use a 2-stage build:
#
#   Stage 1 (count): Copy green source, run regression, capture pass count.
#   Stage 2 (final): Use base image, copy skeleton, record total count.
#
# Stage 1: base toolchain
# Stage 2: count tests on green source (NO cache kept in final image)
# Stage 3: final image with skeleton

# ---------------------------------------------------------------------------
# Stage 1: base — install all needed tools
# ---------------------------------------------------------------------------
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
    apt-get update; \
    apt-get install -y --no-install-recommends \
        autoconf automake autotools-dev \
        bc bison build-essential ccache cmake curl \
        flex gawk git help2man libfl2 libfl-dev \
        libgmp-dev libmpc-dev libmpfr-dev \
        ninja-build numactl perl python3 python3-pip \
        python3-yaml rsync texinfo unzip wget xxd; \
    \
    # Install Verilator from apt (Debian bookworm ships a recent enough version)
    apt-get install -y --no-install-recommends verilator; \
    \
    # Install RISC-V toolchain (pre-built)
    # The Caliptra Makefile requires riscv64-unknown-elf-gcc 12.2.0
    wget -q https://github.com/sifive/freedom-tools/releases/download/v2020.12.0/riscv64-unknown-elf-toolchain-10.2.0-2020.12.8-x86_64-linux-ubuntu14.tar.gz \
        -O /tmp/riscv.tar.gz 2>/dev/null || \
    apt-get install -y --no-install-recommends gcc-riscv64-unknown-elf; \
    \
    # Create non-root user
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /home/${_USERNAME}/.cache; \
    chown ${_USERNAME}:${_USERNAME} /home/${_USERNAME}/.cache; \
    mkdir -p /logs/verifier; \
    chown -R ${_UID}:${_GID} /logs; \
    rm -rf /var/lib/apt/lists/*

USER ${_USERNAME}
WORKDIR /app

# ---------------------------------------------------------------------------
# Stage 2: count tests on green source
# ---------------------------------------------------------------------------
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ---------------------------------------------------------------------------
# Stage 3: final image — skeleton + test count
# ---------------------------------------------------------------------------
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count /tmp/_total /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests /app/.harbor/all_tests

RUN cd /app && git init -q && git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
