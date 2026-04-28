# VeeR EL2 Harbor task — block-level cocotb tests only.
#
# Leaner than the full task: no top-level Verilator model, no RISCOF, no Spike.
# Only block-level cocotb tests in verification/block/ are scored.
#
# Stage 1: base toolchain (Verilator, Python, cocotb, nox, scipy)
# Stage 2: pre-warm — run all nox block tests, record passing count
# Stage 3: copy stripped skeleton, scrub compiled sim-builds

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
        autoconf automake \
        bison build-essential ca-certificates cmake \
        curl flex gawk git \
        help2man libfl-dev libfl2 \
        libtool make pkg-config python3 python3-pip python3-venv \
        python-is-python3 rsync sudo tzdata \
        verilator; \
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
    scipy==1.13.1

ENV PATH="${HOME}/.local/bin:${PATH}"

# ---------------------------------------------------------------------------
# Stage 2: pre-warm — run all block tests, record baseline
# ---------------------------------------------------------------------------
FROM base AS warm

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder warm.sh /tmp/warm.sh
RUN chmod +x /tmp/warm.sh && /tmp/warm.sh

# ---------------------------------------------------------------------------
# Stage 3: final image
# ---------------------------------------------------------------------------
FROM warm AS final

COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # Scrub compiled Verilator sim-build directories (contain green RTL objects)
    find /app/verification/block -name "sim-build*" -type d \
        -exec rm -rf {} + 2>/dev/null || true; \
    # Scrub cocotb result files from warm run (leak pass/fail patterns)
    find /app/verification/block -name "*.xml" \
        -exec rm -f {} + 2>/dev/null || true; \
    find /app/verification/block -name "*.log" \
        -exec rm -f {} + 2>/dev/null || true; \
    # Set up skeleton workspace
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    cp /tmp/_total .harbor/total_tests 2>/dev/null || echo 0 > .harbor/total_tests; \
    cp /tmp/_block_names .harbor/block_names 2>/dev/null || true

ENV RV_ROOT=/app
WORKDIR /app
