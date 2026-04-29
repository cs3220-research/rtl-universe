# Caliptra Root of Trust — Harbor benchmark task (caliptra-e2e).
#
# Build strategy: NO warm cache (same as caliptra-full).
#
# This is the E2E variant: only the top-level integration smoke tests that
# exercise the full Caliptra SoC are scored. Tests are selected to require
# multiple major subsystems (crypto + key vault + VeeR + SoC interface) to
# work together. A reward of 1.0 requires restoring all stripped RTL.
#
# SUBMODULE NOTE: The repo has one git submodule:
#   submodules/adams-bridge  (MLDSA / ML-KEM hardware)
# The submodule is already materialized in warm_src/ and skeleton/.
#
# NO inline heredocs, inline Python, or complex shell in any RUN command.

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
# Stage 2: count — run E2E subset on green source, capture pass count
# ---------------------------------------------------------------------------
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
COPY --chown=builder:builder run_e2e_tests.py /tmp/run_e2e_tests.py

RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ---------------------------------------------------------------------------
# Stage 3: final — skeleton + captured test count
# ---------------------------------------------------------------------------
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count /tmp/_total /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests /app/.harbor/all_tests
COPY --from=count /tmp/_e2e_tests /app/.harbor/e2e_tests

RUN cd /app && git init -q && git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
