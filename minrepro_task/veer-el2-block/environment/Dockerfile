# VeeR EL2 RISC-V Core — Harbor benchmark task (E2E PyUVM test only).
#
# This variant scores the top-level PyUVM IRQ test that exercises the complete
# VeeR EL2 pipeline (Fetch → Decode → Execute → LSU → Write-back) with a
# full-chip Verilator simulation.
#
# Build strategy: 2-stage (no warm cache).
#   - Verilator recompiles the full design from all source files for this test.
#     A warm build cache would be invalidated by any agent RTL change.
#   - We run the green source to confirm the test passes and fix the total (1).
#
# Submodule note: verification/top/requirements.txt references
# ${RV_ROOT}/third_party/cocotb but that submodule is NOT initialized.
# We override by installing cocotb==1.8.0 from pip instead, which is
# compatible. The count_e2e_tests.sh script runs pip install -r requirements.txt
# but the ${RV_ROOT} reference will fail gracefully and pip uses the already-
# installed 1.8.0 version.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

RUN apt-get update && apt-get install -y \
    autoconf \
    build-essential \
    ca-certificates \
    curl \
    git \
    help2man \
    libfl2 \
    libfl-dev \
    libgoogle-perftools-dev \
    make \
    numactl \
    perl \
    perl-doc \
    python3 \
    python3-pip \
    python3-dev \
    verilator \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --gid ${_GID} ${_USERNAME} && \
    adduser --uid ${_UID} --gid ${_GID} --disabled-password --gecos "" ${_USERNAME} && \
    mkdir -p ${HOME}/.cache && \
    chown -R ${_UID}:${_GID} ${HOME}

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs

COPY install_python_deps.sh /tmp/install_python_deps.sh
RUN chmod +x /tmp/install_python_deps.sh && /tmp/install_python_deps.sh

USER ${_USERNAME}
WORKDIR ${HOME}

# Pre-install OpenCode dependencies (NVM + Node 22 + opencode-ai).
# Runs at docker-build time with full network access. The sandboxed Harbor
# agent adapter skips runtime install and activates DNS sinkhole after.
RUN bash -c '\
    set -ex; \
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.2/install.sh | bash; \
    export NVM_DIR="$HOME/.nvm"; \
    . "$NVM_DIR/nvm.sh"; \
    nvm install 22; \
    npm i -g opencode-ai@latest'


# ---------------------------------------------------------------------------
# Stage: count — run green E2E test, capture count (always 1).
# ---------------------------------------------------------------------------
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/

COPY --chown=builder:builder count_e2e_tests.sh /tmp/count_e2e_tests.sh

USER root
RUN chmod +x /tmp/count_e2e_tests.sh
USER ${_USERNAME}

RUN cd /app && export RV_ROOT=/app && /tmp/count_e2e_tests.sh


# ---------------------------------------------------------------------------
# Stage: final — clean toolchain + skeleton + count from count stage.
# ---------------------------------------------------------------------------
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/

COPY --from=count /tmp/_total /tmp/_harbor_total
COPY --from=count /tmp/_all_tests /tmp/_harbor_all_tests
COPY --from=count /tmp/_e2e_tests /tmp/_harbor_e2e_tests

COPY --chown=builder:builder harden_e2e.sh /tmp/harden_e2e.sh

USER root
RUN chmod +x /tmp/harden_e2e.sh
USER ${_USERNAME}

RUN cd /app && \
    git init -q && \
    git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init && \
    mkdir -p .harbor && \
    cp /tmp/_harbor_total .harbor/total_tests && \
    cp /tmp/_harbor_all_tests .harbor/all_tests && \
    cp /tmp/_harbor_e2e_tests .harbor/e2e_tests && \
    /tmp/harden_e2e.sh

WORKDIR /app
