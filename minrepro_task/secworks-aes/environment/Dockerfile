# secworks-aes Harbor task — all testbenches mode.
#
# Build system: FuseSoC + Icarus Verilog (iverilog)
# Testbenches:  tb_aes, tb_aes_core, tb_aes_key_mem,
#               tb_aes_encipher_block, tb_aes_decipher_block
#
# Stage 1: base toolchain (iverilog, python3/pip, fusesoc)
# Stage 2: pre-warm — register core with FuseSoC, run all testbenches once to
#           establish the authoritative pass/fail count.
# Stage 3: wipe /app, COPY code-stripped skeleton, scrub any warm artifacts.
#
# The warm stage builds nothing persistently cacheable (iverilog re-compiles
# in seconds) so there is no build-cache to prime; the value here is capturing
# the baseline test count and verifying the green source is fully passing.

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
        rsync sudo; \
    # Install FuseSoC via pip
    pip3 install --break-system-packages fusesoc; \
    # Create the non-root builder user
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs

USER ${_USERNAME}
WORKDIR ${HOME}

# ---------------------------------------------------------------------------
# Stage 2: pre-warm — register the green source with FuseSoC and run all
# five testbenches to establish the passing test count.
# ---------------------------------------------------------------------------
FROM base AS warm

USER root
RUN mkdir -p /home/builder/.cache/fusesoc && chown -R builder:builder /home/builder/.cache
USER builder
COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder warm.sh /tmp/warm.sh
RUN chmod +x /tmp/warm.sh && /tmp/warm.sh


# ---------------------------------------------------------------------------
# Stage 3: final image — stripped skeleton at /app.
# ---------------------------------------------------------------------------
FROM warm AS final

COPY --chown=builder:builder skeleton/ /app/

RUN set -eux; \
    # ── HARDENING: scrub warm-run artifacts ──
    # FuseSoC build artefacts live under build/ by default in the workdir
    rm -rf /app/build 2>/dev/null || true; \
    # VCD waveform dumps from the warm run
    find /app -name "*.vcd" -delete 2>/dev/null || true; \
    # Any compiled simulation binaries
    find /app -name "*.sim" -delete 2>/dev/null || true; \
    # Warm run logs
    rm -f /tmp/tb_aes*.log; \
    \
    # ── Set up skeleton workspace ──
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    mv /tmp/_all_tests  .harbor/all_tests; \
    mv /tmp/_total      .harbor/total_tests; \
    cp .harbor/total_tests .harbor/warm_total

WORKDIR /app
