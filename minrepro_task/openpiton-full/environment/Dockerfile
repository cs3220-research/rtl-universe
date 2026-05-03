# OpenPiton Harbor task — all-tests mode.
#
# Build system: Icarus Verilog (iverilog/vvp) — no FuseSoC or proprietary tools.
# Tests:        4 ESL (Execution Scheduling Logic) sub-component testbenches
#               + 1 UART serializer testbench, exercised via .vmh vector files.
#
#   ifu_esl_counter  (8 test cases)  sparc_ifu_esl_counter.v
#   ifu_esl_lfsr     (5 test cases)  sparc_ifu_esl_lfsr.v
#   ifu_esl_shiftreg (4 test cases)  sparc_ifu_esl_shiftreg.v
#   uart_serializer  (1 test case)   uart_serializer.v
#   Total: 18 tests
#
# Stage 1 (base):  install iverilog + python3.
# Stage 2 (count): copy green source, run all tests, capture counts;
#                  discard build outputs (no warm cache — Icarus recompiles
#                  everything on each run, so a cache adds zero benefit).
# Stage 3 (final): COPY code-stripped skeleton + test counts from stage 2.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1000
ARG _GID=1000
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
        python3 python3-pip \
        rsync; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p ${HOME}/.cache; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}/.cache; \
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
    npm i -g opencode-ai@latest; \
    npm i -g @openai/codex@latest'


# ---------------------------------------------------------------------------
# Stage 2: count — run the green source tests to establish the denominator.
# Build outputs are discarded; no warm cache (Icarus has no content-addressed
# incremental cache that survives source changes).
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
COPY --from=count --chown=builder:builder /tmp/_total        /app/.harbor/total_tests
COPY --from=count --chown=builder:builder /tmp/_all_tests    /app/.harbor/all_tests
COPY --from=count --chown=builder:builder /tmp/_e2e_targets  /app/.harbor/e2e_targets

RUN set -eux; \
    cd /app; \
    git config --global --add safe.directory /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init

WORKDIR /app
