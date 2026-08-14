# NVDLA Harbor task — E2E mode (network inference traces only)
#
# Build system: custom tmake / Make + Verilator
# Scored tests: 6 end-to-end network inference traces
#   sanity3, sanity3_cvsram, conv_8x8_fc_int16,
#   googlenet_conv2_3x3_int16, pdp_max_pooling_int16, sdp_relu_int16
#
# These traces load real neural-network input/weight data, drive the full
# NVDLA pipeline, and compare output feature maps against golden references.
# They require a fully-functional implementation to pass.
#
# Stages: same as nvdla-full (base → count → final).
# The only difference from nvdla-full is which tests are scored.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=3298962
ARG _GID=2626
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

RUN set -eux; \
    ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime; \
    echo "${TZ}" > /etc/timezone; \
    apt-get update; \
    apt-get install -y \
        build-essential \
        ca-certificates \
        curl \
        git \
        wget \
        make \
        perl \
        clang \
        python3 \
        python3-pip \
        autoconf \
        bison \
        flex \
        help2man \
        libfl2 \
        libfl-dev \
        libgoogle-perftools-dev \
        numactl \
        zlib1g \
        zlib1g-dev \
        ccache \
        mold; \
    rm -rf /var/lib/apt/lists/*

ENV VERILATOR_TAG=v5.020

RUN set -eux; \
    git clone --depth 1 --branch "${VERILATOR_TAG}" \
        https://github.com/verilator/verilator.git /tmp/verilator-src; \
    cd /tmp/verilator-src; \
    autoconf; \
    ./configure --prefix=/tools/verilator; \
    make -j"$(nproc)"; \
    make install; \
    rm -rf /tmp/verilator-src

ENV PATH="/tools/verilator/bin:${PATH}"

RUN set -eux; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p ${HOME}/.cache; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}/.cache; \
    mkdir -p /logs/verifier; \
    chown -R ${_UID}:${_GID} /logs

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
    npm i -g opencode-ai@latest; \
    npm i -g @openai/codex@latest'


# ---------------------------------------------------------------------------
# Stage 2: count — build simulator, run E2E tests, capture count.
# ---------------------------------------------------------------------------
FROM base AS count

USER root
RUN mkdir -p /app && chown ${_UID}:${_GID} /app
USER ${_USERNAME}

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh


# ---------------------------------------------------------------------------
# Stage 3: final — stripped skeleton + E2E test counts.
# ---------------------------------------------------------------------------
FROM base AS final

USER root
RUN mkdir -p /app /app/.harbor && chown -R ${_UID}:${_GID} /app
USER ${_USERNAME}

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count --chown=${_USERNAME}:${_USERNAME} /tmp/_total       /app/.harbor/total_tests
COPY --from=count --chown=${_USERNAME}:${_USERNAME} /tmp/_all_tests   /app/.harbor/all_tests
COPY --from=count --chown=${_USERNAME}:${_USERNAME} /tmp/_e2e_targets /app/.harbor/e2e_targets

RUN set -eux; \
    cd /app; \
    git config --global --add safe.directory /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
