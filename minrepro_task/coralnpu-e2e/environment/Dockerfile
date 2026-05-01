# Coral NPU Harbor task — all-tests mode.
#
# Stage 1: base toolchain (bazel 7.4.1, JDK 17, clang-19, python, srecord).
# Stage 2: pre-warm bazel output_base from the full green source at /app.
# Stage 3: wipe /app contents, COPY the code-stripped skeleton to /app, init git.
#
# Critical: stages 2 and 3 both put the workspace at /app so bazel's
# output_base (keyed on workspace path) is shared. The agent's first
# `bazel test` reuses the pre-built Verilator, firtool, and TFLM host tools;
# only Chisel → Verilog genrules and anything that consumes the agent's
# modified sources need to re-run.

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
        apt-transport-https autoconf build-essential ca-certificates ccache \
        curl fuse3 gawk git gnupg libelf-dev libftdi1-dev libmpfr-dev \
        libusb-1.0-0-dev lsb-release openjdk-17-jdk-headless openjdk-17-jre-headless \
        python-is-python3 python3 python3-pip python3-setuptools python3-venv \
        srecord sudo tzdata unzip xxd zip rsync; \
    update-ca-certificates; \
    apt-get install -y -qq clang-19 clang-tools-19 lld-19 clang-format-19 clang-tidy-19; \
    ln -s /usr/bin/clang-19       /usr/bin/clang; \
    ln -s /usr/bin/clang++-19     /usr/bin/clang++; \
    ln -s /usr/bin/clang-cpp-19   /usr/bin/clang-cpp; \
    ln -s /usr/bin/clang-format-19 /usr/bin/clang-format; \
    ln -s /usr/bin/clang-tidy-19  /usr/bin/clang-tidy; \
    ln -s /usr/bin/ld.lld-19      /usr/bin/ld.lld; \
    ln -s /usr/bin/llvm-symbolizer-19 /usr/bin/llvm-symbolizer; \
    curl -fsSL https://bazel.build/bazel-release.pub.gpg \
      | gpg --dearmor > /usr/share/keyrings/bazel-archive-keyring.gpg; \
    echo "deb [arch=$(dpkg-architecture -q DEB_HOST_ARCH) signed-by=/usr/share/keyrings/bazel-archive-keyring.gpg] https://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/bazel.list; \
    apt-get update; \
    apt-get install -y -qq bazel bazel-7.4.1; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    ln -sf /lib/x86_64-linux-gnu/libmpfr.so.6.2.0 /lib/x86_64-linux-gnu/libmpfr.so.4; \
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
# Stage 2: pre-warm bazel at the canonical workspace path (/app).
# ---------------------------------------------------------------------------
FROM base AS warm

COPY --chown=builder:builder warm_src/ /app/
RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    bazel fetch //... 2>&1 | tail -20; \
    # Full warm-source build + test. Populates the output_base action cache
    # with Verilator/firtool/TFLM host tools and all the cocotb sim binaries.
    # Also establishes the authoritative baseline test set (what bazel test
    # //... actually runs after default tag filtering) by capturing the
    # PASSED target labels from the log.
    bazel test //... --keep_going --test_output=errors 2>&1 | tee /tmp/warm.log | tail -20 || true; \
    grep -E "^//[^ ]+ +PASSED in " /tmp/warm.log | awk '{print $1}' | sort -u > /tmp/_all_tests; \
    wc -l < /tmp/_all_tests > /tmp/_total; \
    # E2E targets: intersect all-tests with the e2e package set.
    bazel query '(//tests/cocotb/... except //tests/cocotb/tlul/...) union //tests/verilator_sim:core_mini_axi_non_incr_tests union //tests/verilator_sim:backdoor_load_test' 2>/dev/null | sort -u > /tmp/_e2e_raw; \
    comm -12 /tmp/_all_tests /tmp/_e2e_raw > /tmp/_e2e_targets; \
    # Drop the green source — the output_base under ~/.cache/bazel survives.
    find /app -mindepth 1 -maxdepth 1 -not -name '.cache' -exec rm -rf {} +


# ---------------------------------------------------------------------------
# Stage 3: final image — warm cache at /home/builder/.cache/bazel, skeleton at /app.
# ---------------------------------------------------------------------------
FROM warm AS final

COPY --chown=builder:builder skeleton/ /app/
RUN set -eux; \
    # ── Scrub warm-build artifacts that leak the green implementation ──
    # Sandbox stash: contains generated Verilog from the warm Chisel build
    find ~/.cache/bazel -path "*/sandbox/sandbox_stash" -type d -exec rm -rf {} + 2>/dev/null || true; \
    # Test logs from the warm run (show pass/fail patterns, assertion messages)
    find ~/.cache/bazel -path "*/testlogs" -type d -exec rm -rf {} + 2>/dev/null || true; \
    # Project-specific build outputs (generated .sv, .jar, runfiles) — keep external tool binaries
    for d in hdl tests sw examples fpga build coralnpu_test_utils hw_sim; do \
        find ~/.cache/bazel -path "*/bin/$d" -type d -exec rm -rf {} + 2>/dev/null || true; \
    done; \
    # ── Set up the skeleton workspace ──
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    mkdir -p .harbor; \
    mv /tmp/_all_tests .harbor/all_tests; \
    mv /tmp/_total .harbor/total_tests; \
    mv /tmp/_e2e_targets .harbor/e2e_targets

WORKDIR /app
