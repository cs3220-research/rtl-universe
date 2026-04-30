#!/bin/bash
# harden.sh — Create skeleton from green warm_src by truncating implementation files.
# Identical to openpiton-full/environment/harden.sh — both variants strip the same files.

set -eux

PITON=/app/piton

: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v"
: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v"
: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v"
: > "$PITON/design/common/uart_pkttrace_dump/rtl/uart_serializer.v"

echo "Hardening complete: 4 implementation files truncated."
