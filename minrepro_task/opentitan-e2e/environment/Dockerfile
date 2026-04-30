# OpenTitan Root of Trust — Harbor E2E task (silicon_creator subset)
#
# Same Docker image as opentitan-full, but only the E2E subset (silicon_creator
# tests) counts for scoring.
#
# The opentitan-full and opentitan-e2e tasks share the same Dockerfile and
# environment. The difference is only in test.sh (verifier) which reads from
# .harbor/e2e_tests instead of .harbor/all_tests.
#
# See opentitan-full/environment/Dockerfile for detailed build notes.

FROM debian:bookworm AS base

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ARG _UID=1005
ARG _GID=1005
ARG _USERNAME=builder
ENV HOME=/home/${_USERNAME}

COPY install_deps.sh /tmp/install_deps.sh
RUN chmod +x /tmp/install_deps.sh && /tmp/install_deps.sh

RUN set -eux; \
    addgroup --gid ${_GID} ${_USERNAME}; \
    adduser --home ${HOME} --disabled-password --gecos "" \
            --uid ${_UID} --gid ${_GID} ${_USERNAME}; \
    chown ${_USERNAME}:${_USERNAME} ${HOME}; \
    mkdir -p /logs/verifier && chown -R ${_UID}:${_GID} /logs; \
    mkdir -p /app && chown -R ${_UID}:${_GID} /app; \
    mkdir -p ${HOME}/.cache && chown -R ${_UID}:${_GID} ${HOME}/.cache

USER ${_USERNAME}
WORKDIR /app

FROM base AS count

COPY --chown=builder:builder warm_src/ /app/

COPY --chown=builder:builder count_tests.sh /tmp/count_tests.sh
RUN chmod +x /tmp/count_tests.sh && /tmp/count_tests.sh

COPY --chown=builder:builder harden.sh /tmp/harden.sh
RUN chmod +x /tmp/harden.sh && /tmp/harden.sh

RUN rm -rf /app/*

FROM base AS final

COPY --from=count /home/builder/.cache /home/builder/.cache

COPY --chown=builder:builder skeleton/ /app/

COPY --from=count /tmp/_total          /app/.harbor/total_tests
COPY --from=count /tmp/_all_tests      /app/.harbor/all_tests
COPY --from=count /tmp/_e2e_tests      /app/.harbor/e2e_tests
COPY --from=count /tmp/_e2e_total      /app/.harbor/e2e_total

USER root
RUN chown -R builder:builder /app/.harbor
USER builder
RUN set -eux; \
    cd /app; \
    git init -q; \
    git add -A; \
    git -c user.email=x@x -c user.name=x commit -q -m init; \
    echo "Final image ready. e2e_total=$(cat .harbor/e2e_total)"

WORKDIR /app
