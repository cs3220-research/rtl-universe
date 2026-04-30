#!/bin/bash
# harden.sh — Create skeleton from green warm_src by truncating implementation files.
# Run inside /app (the warm_src copy) BEFORE building the Docker final stage.
# This script is referenced in sync-skeleton.sh; it is NOT run inside Docker.
#
# What gets stripped (truncated to empty):
#   - sparc_ifu_esl_counter.v   (16-bit counter, ~40 lines)
#   - sparc_ifu_esl_lfsr.v      (16-bit LFSR, ~50 lines)
#   - sparc_ifu_esl_shiftreg.v  (shift register, ~45 lines)
#   - uart_serializer.v         (UART packet serializer)
#
# What is kept intact:
#   - All verif/env/ testbench files (testbenches + .vmh test vectors)
#   - All .core FuseSoC descriptor files
#   - All Flist.* file lists
#   - All design/include/ headers
#   - All .flist files

set -eux

PITON=/app/piton

# Truncate implementation Verilog files (strip content, keep empty files so
# paths referenced in .core / Flist remain valid)
: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_counter.v"
: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_lfsr.v"
: > "$PITON/design/chip/tile/sparc/ifu/rtl/sparc_ifu_esl_shiftreg.v"
: > "$PITON/design/common/uart_pkttrace_dump/rtl/uart_serializer.v"

echo "Hardening complete: 4 implementation files truncated."
