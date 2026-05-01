# Ibex Harbor task — all-tests mode.
#
# Build system: FuseSoC + Verilator 4.210
# Tests:        tb_cs_registers (CSR testbench) +
#               hello_test, dit_test, dummy_instr_test, pmp_smoke_test
#               (software tests on simple-system Verilator sim)
#
# Stage 1 (base): install Verilator 4.210, RISC-V toolchain, FuseSoC,
#                 srecord and other build deps.
# Stage 2 (count): copy green source, run all tests, capture passing count;
#                  discard artifacts (no warm cache — Verilator recompiles
#                  everything on any source change).
# Stage 3 (final): COPY code-stripped skeleton + test counts from stage 2.
#
# NOTE: Do NOT build this image in CI — it downloads ~200 MB of toolchain
#       tarballs from GitHub/GCS.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

# ---- Versioned constants (keep in sync with ci/vars.env) ----
ENV VERILATOR_VERSION=v4.210
ENV RISCV_TOOLCHAIN_TAR_VERSION=20220210-1
ENV RISCV_TOOLCHAIN_TAR_VARIANT=lowrisc-toolchain-gcc-rv32imcb

RUN set -eux; \
    ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime; \
    echo "${TZ}" > /etc/timezone; \
    echo 'APT::Get::Assume-Yes "true";' > /etc/apt/apt.conf.d/90assumeyes; \
    apt-get update; \
    apt-get install -y -qq \
        build-essential ca-certificates curl git \
        autoconf flex bison \
        libelf-dev zlib1g-dev \
        python3 python3-pip python3-setuptools python3-venv python3-yaml \
        python3-dev \
        device-tree-compiler srecord \
        wget xz-utils \
        rsync; \
    # Install FuseSoC and Python dependencies
    pip3 install --break-system-packages \
        "fusesoc==2.4.3" \
        packaging pyyaml mako junit-xml \
        pathlib3x typing-utils "typeguard~=2.13" \
        portalocker "pydantic>=2" "svg.py" \
        hjson "mistletoe>=0.7.2" "premailer<3.9.0"; \
    # Create non-root builder user (UID/GID match Harbor host)
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p ${HOME}/.cache; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}/.cache; \
    rm -rf /var/lib/apt/lists/*

# Install pre-built Verilator 4.210 tarball
RUN set -eux; \
    wget -q "https://storage.googleapis.com/verilator-builds/verilator-${VERILATOR_VERSION}.tar.gz" \
        -O /tmp/verilator.tar.gz; \
    mkdir -p /tools/verilator; \
    tar -C /tools/verilator -xzf /tmp/verilator.tar.gz; \
    rm /tmp/verilator.tar.gz; \
    # Verilator tarball extracts to /tools/verilator/<version>/bin/verilator
    ln -s /tools/verilator/${VERILATOR_VERSION}/bin/verilator /usr/local/bin/verilator; \
    ln -s /tools/verilator/${VERILATOR_VERSION}/bin/verilator_bin /usr/local/bin/verilator_bin

# Install RISC-V cross-compilation toolchain
RUN set -eux; \
    curl -Ls -o /tmp/rv32-toolchain.tar.xz \
        "https://github.com/lowRISC/lowrisc-toolchains/releases/download/${RISCV_TOOLCHAIN_TAR_VERSION}/${RISCV_TOOLCHAIN_TAR_VARIANT}-${RISCV_TOOLCHAIN_TAR_VERSION}.tar.xz"; \
    mkdir -p /tools/riscv; \
    tar -C /tools/riscv -xf /tmp/rv32-toolchain.tar.xz --strip-components=1; \
    rm /tmp/rv32-toolchain.tar.xz

ENV PATH="/tools/riscv/bin:/tools/verilator/${VERILATOR_VERSION}/bin:${PATH}"

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs

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
# Stage 2: count — run the green source tests to establish the denominator.
# The build outputs are discarded; no warm cache.
# ---------------------------------------------------------------------------
FROM base AS count

USER root
RUN mkdir -p /app && chown builder:builder /app
USER builder

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh


# ---------------------------------------------------------------------------
# Stage 3: final — install skeleton, import test counts, init git.
# ---------------------------------------------------------------------------
FROM base AS final

USER root
RUN mkdir -p /app /app/.harbor && chown -R builder:builder /app
USER builder

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count --chown=builder:builder /tmp/_total       /app/.harbor/total_tests
COPY --from=count --chown=builder:builder /tmp/_all_tests   /app/.harbor/all_tests
COPY --from=count --chown=builder:builder /tmp/_e2e_targets /app/.harbor/e2e_targets

RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
