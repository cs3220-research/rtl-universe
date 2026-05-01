# secworks-aes Harbor task — all testbenches mode.
#
# Build system: FuseSoC + Icarus Verilog (iverilog)
# Testbenches:  tb_aes, tb_aes_core, tb_aes_key_mem,
#               tb_aes_encipher_block, tb_aes_decipher_block
#
# Build strategy: 2-stage (no warm cache).
#   iverilog recompiles in seconds and has no content-addressed cache that
#   survives source changes. All tools are pre-installed via apt/pip, so
#   there is no build graph to warm. Total cold build time is <1 min.
#   Stage 1 (count): run green source to capture total test count.
#   Stage 2 (final): start fresh from base, drop stripped skeleton at /app.

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
        build-essential ca-certificates curl git \
        iverilog \
        python3 python3-pip python3-venv \
        rsync; \
    pip3 install --break-system-packages fusesoc; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /home/${_USERNAME}/.cache && chown -R ${_UID}:${_GID} /home/${_USERNAME}/.cache; \
    rm -rf /var/lib/apt/lists/*

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
# Stage 1: count — run all testbenches on the green source to record the
# authoritative total test-case count.  This stage is thrown away; nothing
# from its build artifacts leaks into the final image.
# ---------------------------------------------------------------------------
FROM base AS count

USER root
RUN mkdir -p /home/builder/.cache/fusesoc && chown -R builder:builder /home/builder/.cache
USER builder

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ---------------------------------------------------------------------------
# Stage 2: final — clean base image with stripped skeleton at /app.
# No warm cache is copied in; agents start from a cold build.
# ---------------------------------------------------------------------------
FROM base AS final

USER root
RUN mkdir -p /home/builder/.cache/fusesoc && chown -R builder:builder /home/builder/.cache
USER builder

COPY --chown=builder:builder skeleton/ /app/
COPY --from=count /tmp/_total /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests /app/.harbor/all_tests

RUN set -eux; \
    cd /app; \
    fusesoc library add aes /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
