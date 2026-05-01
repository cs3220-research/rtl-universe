# PULP common_cells Harbor task
#
# Build system: Bender (dependency management) + Verilator (lint + ECC C++ harness)
# Tests:
#   - Per-module Verilator lint checks (one pass/fail per RTL module)
#   - ECC functional tests: ecc_encode and ecc_decode with C++ harnesses
#
# Decision: no warm cache.
#   Verilator lint-only runs in <1 s per module. The ECC C++ harnesses compile
#   in ~10 s. Total cold build for the verifier is well under 3 minutes.
#   There is no content-addressed cache that survives source changes here;
#   Verilator recompiles from scratch whenever a source file changes.
#   So warming would not accelerate the agent's iterations.
#
# Strategy: 2-stage build.
#   Stage 1 (base): install tools (Bender, Verilator, build-essential, git).
#   Stage 2 (count): copy green source, run bender checkout, run count_tests.sh
#     to record total passing tests.
#   Final stage (base again): copy skeleton, grab counts from count stage.
#
# NOTE: Set _UID/_GID to match the host user that runs Harbor.
#       A mismatch causes permission errors on the bind-mounted /logs directory.

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
        python3 python3-pip \
        verilator \
        rsync; \
    # Install Bender (PULP dependency manager)
    BENDER_VERSION="0.28.2"; \
    BENDER_URL="https://github.com/pulp-platform/bender/releases/download/v${BENDER_VERSION}/bender-${BENDER_VERSION}-x86_64-linux-gnu-debian12.tar.gz"; \
    curl -fsSL "$BENDER_URL" -o /tmp/bender.tar.gz; \
    tar xzf /tmp/bender.tar.gz -C /usr/local/bin bender; \
    rm /tmp/bender.tar.gz; \
    bender --version; \
    # Create the non-root builder user
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs
RUN mkdir -p /app && chown -R ${_UID}:${_GID} /app

USER ${_USERNAME}
WORKDIR /app

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

# ===========================================================================
# Stage 2: count — copy green source, fetch Bender deps, count passing tests
# ===========================================================================
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

# ===========================================================================
# Final stage: stripped skeleton + test metadata
# ===========================================================================
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/

# Copy the Bender project-local dependency cache from the count stage.
# "bender checkout" creates /app/.bender/ with git pack objects and
# /app/.bender/git/checkouts/ with the actual dependency source trees.
# Copying this directory means the agent can run "bender checkout" in /app
# and immediately get the source symlinks without any network access.
COPY --from=count --chown=builder:builder /app/.bender /app/.bender

# Grab test metadata from count stage
COPY --from=count /tmp/_total      /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests  /app/.harbor/all_tests

# Initialize git repository for the skeleton
RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    echo "Final image ready. total_tests=$(cat .harbor/total_tests)"

WORKDIR /app
