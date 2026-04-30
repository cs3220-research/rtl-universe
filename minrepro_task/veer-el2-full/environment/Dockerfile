# VeeR EL2 RISC-V Core — Harbor benchmark task (all block tests).
#
# Build strategy: 2-stage (no warm cache).
#   - All tools are apt/pip pre-installed binaries. Verilator recompiles the
#     design from scratch for every change, so a warm Verilator build cache
#     would be invalidated as soon as the agent touches any RTL file.
#   - We do run the green source to capture the total passing test count
#     (stored in /app/.harbor/total_tests). That count stage is discarded;
#     the final image starts from the clean toolchain base.
#
# Submodules: The repo has .gitmodules (third_party/picolibc,
# third_party/riscv-dv, third_party/cocotb). The block tests only use
# installed cocotb from pip and design/* RTL — they do NOT reference the
# third_party submodule directories at runtime. So submodule init is NOT
# required for the block tests.
#
# The E2E pyuvm test (verification/top/test_pyuvm) DOES use
# third_party/cocotb but only if RV_ROOT and requirements.txt point at it.
# In our setup we install cocotb==1.8.0 from pip instead, which is
# compatible with Verilator --timing and avoids submodule dependency.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

# Install system packages.
# verilator: from Debian bookworm (4.228) — meets the >=4.106 requirement.
# perl: required by configs/veer.config (Perl script).
# python3-pip: needed for pip3 install --break-system-packages.
# make, gcc: for cocotb Makefile-based flow.
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
    libbit-vector-perl \
    perl \
    perl-doc \
    python3 \
    python3-pip \
    python3-dev \
    verilator \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user matching Harbor host UID.
RUN addgroup --gid ${_GID} ${_USERNAME} && \
    adduser --uid ${_UID} --gid ${_GID} --disabled-password --gecos "" ${_USERNAME} && \
    mkdir -p ${HOME}/.cache && \
    chown -R ${_UID}:${_GID} ${HOME}

RUN mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs

# Copy and run Python dep installer as root (installs to system Python).
COPY install_python_deps.sh /tmp/install_python_deps.sh
RUN chmod +x /tmp/install_python_deps.sh && /tmp/install_python_deps.sh

USER ${_USERNAME}
WORKDIR ${HOME}


# ---------------------------------------------------------------------------
# Stage: count — run green source, capture passing test count.
# This stage is discarded; only the count files (/tmp/_total, etc.) survive
# via COPY --from=count in the final stage.
# ---------------------------------------------------------------------------
FROM base AS count

COPY --chown=builder:builder warm_src/ /app/

# Copy helper scripts into /tmp so they are accessible during RUN.
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
COPY --chown=builder:builder parse_nox_results.py /tmp/parse_nox_results.py

USER root
RUN chmod +x /tmp/count_tests.sh && chmod +x /tmp/parse_nox_results.py
USER ${_USERNAME}

# Run the test counting script from the workspace root.
RUN cd /app && /tmp/count_tests.sh


# ---------------------------------------------------------------------------
# Stage: final — clean toolchain + skeleton + counts from count stage.
# ---------------------------------------------------------------------------
FROM base AS final

COPY --chown=builder:builder skeleton/ /app/

# Pull in test counts captured by the count stage.
COPY --from=count /tmp/_total /tmp/_harbor_total
COPY --from=count /tmp/_all_tests /tmp/_harbor_all_tests
COPY --from=count /tmp/_e2e_tests /tmp/_harbor_e2e_tests

# Copy harden script.
COPY --chown=builder:builder harden.sh /tmp/harden.sh

USER root
RUN chmod +x /tmp/harden.sh
USER ${_USERNAME}

# Set up /app as a git repo and install harbor metadata.
RUN cd /app && \
    git init -q && \
    git add -A && \
    git -c user.email=x@x -c user.name=x commit -q -m init && \
    mkdir -p .harbor && \
    cp /tmp/_harbor_total .harbor/total_tests && \
    cp /tmp/_harbor_all_tests .harbor/all_tests && \
    cp /tmp/_harbor_e2e_tests .harbor/e2e_tests && \
    /tmp/harden.sh

WORKDIR /app
