# Caliptra Root of Trust — Harbor benchmark task (caliptra-full).
#
# Build strategy: NO warm cache.
#
# Caliptra uses Verilator (Make-based) for simulation. The Makefile compiles
# all ~500 RTL files into a single monolithic binary (obj_dir/Vcaliptra_top_tb).
# Any RTL change invalidates the entire obj_dir — a warm cache gives the agent
# zero benefit. Instead we use a 2-stage build:
#
#   Stage 1 (count): Copy green source, run L0 regression, capture pass count.
#   Stage 2 (final): Fresh base image, copy skeleton + test count.
#
# SUBMODULE NOTE: The repo has one git submodule:
#   submodules/adams-bridge  (MLDSA / ML-KEM hardware from CHIPS Alliance)
# The submodule is already materialized in warm_src/ and skeleton/ (copied from
# repos/caliptra-rtl which has the submodule initialized). No git submodule
# init is needed at Docker build time.
#
# NO inline heredocs, inline Python, or complex shell in any RUN command.
# All logic is in separate script files (count_tests.sh, parse_l0_tests.py,
# run_l0_tests.py) that are COPY-ed into the image before use.

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
        python3-yaml rsync texinfo unzip wget xxd \
        verilator \
        gcc-riscv64-unknown-elf binutils-riscv64-unknown-elf \
        picolibc-riscv64-unknown-elf; \
    pip3 install pyyaml --break-system-packages; \
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
# Stage 2: count — run L0 regression on green source, capture pass count
# ---------------------------------------------------------------------------
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
COPY --chown=builder:builder parse_l0_tests.py /tmp/parse_l0_tests.py
COPY --chown=builder:builder run_l0_tests.py /tmp/run_l0_tests.py

RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ---------------------------------------------------------------------------
# Stage 3: final — skeleton + captured test count
# ---------------------------------------------------------------------------
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count /tmp/_total /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests /app/.harbor/all_tests

RUN cd /app && git init -q && git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
