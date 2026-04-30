# CVA6 RISC-V Core — Harbor benchmark task (full variant (all 228 tests))
#
# This is identical to the cva6 full task Dockerfile but the final image
# also copies the E2E subset count files. The full variant (all 228 tests) scores only the
# most fundamental integration tests: basic integer ISA tests (rv64ui-p-*)
# and machine-mode tests (rv64mi-p-*) that exercise the full pipeline
# with no virtual memory (bare-metal execution).
#
# E2E subset:
#   rv64ui-p-* (bare-metal user integer): 46 tests
#   rv64mi-p-* (machine-mode): 7 tests
#   rv64si-p-* (supervisor-mode): 6 tests
#   rv64uc-p-* (compressed, bare-metal): 1 test
#   rv64ua-p-* (atomic AMO, bare-metal): 19 tests
#   Total E2E: ~79 tests
#
# For the full 228-test variant, see the cva6/ task directory.
#
# NOTE: Both tasks share the same Docker image; only test.sh differs.

FROM debian:bookworm AS spike-builder

ENV DEBIAN_FRONTEND=noninteractive

COPY build_spike.sh /tmp/build_spike.sh
RUN chmod +x /tmp/build_spike.sh && /tmp/build_spike.sh

FROM debian:bookworm AS verilator-builder

ENV DEBIAN_FRONTEND=noninteractive

COPY build_verilator.sh /tmp/build_verilator.sh
RUN chmod +x /tmp/build_verilator.sh && /tmp/build_verilator.sh

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

COPY install_base.sh /tmp/install_base.sh
RUN chmod +x /tmp/install_base.sh && /tmp/install_base.sh

# Copy Verilator 5.008 from builder stage
COPY --from=verilator-builder /opt/verilator /opt/verilator
ENV PATH="/opt/verilator/bin:${PATH}"

COPY install_bender.sh /tmp/install_bender.sh
RUN chmod +x /tmp/install_bender.sh && /tmp/install_bender.sh

COPY --from=spike-builder /opt/spike /opt/spike

RUN set -eux; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs; \
    mkdir -p /app && chown -R ${_UID}:${_GID} /app

USER ${_USERNAME}
WORKDIR /app

FROM base AS count

COPY --chown=builder:builder warm_src/ /app/
COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

FROM base AS final

COPY --chown=builder:builder skeleton/ /app/

COPY --from=count /tmp/_total          /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests      /app/.harbor/all_tests
COPY --from=count /tmp/_asm_tests      /app/.harbor/asm_tests
COPY --from=count /tmp/_amo_tests      /app/.harbor/amo_tests
COPY --from=count /tmp/_mul_tests      /app/.harbor/mul_tests
COPY --from=count /tmp/_fp_tests       /app/.harbor/fp_tests
COPY --from=count /tmp/_bench_tests    /app/.harbor/bench_tests
COPY --from=count /tmp/_e2e_tests      /app/.harbor/e2e_tests
COPY --from=count /tmp/_e2e_total      /app/.harbor/e2e_total_tests

RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    echo "Final image ready. total_tests=$(cat .harbor/total_tests) e2e_total=$(cat .harbor/e2e_total_tests)"

WORKDIR /app
