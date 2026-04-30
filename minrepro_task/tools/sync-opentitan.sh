#!/bin/bash
# sync-skeleton.sh — Generate skeleton/ and warm_src/ for opentitan Harbor tasks.
#
# Run this script from the harbor task directory BEFORE building Docker images.
# It creates environment/skeleton/ and environment/warm_src/ for each task
# variant from the green source at GREEN_SOURCE.
#
# Usage:
#   GREEN_SOURCE=/path/to/opentitan bash tools/sync-skeleton.sh
#
# The GREEN_SOURCE repo must be a complete checkout (no missing files).
# Since opentitan has no .gitmodules, submodule init is not needed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="${SCRIPT_DIR}/.."

GREEN_SOURCE="${GREEN_SOURCE:-/data/saketh/research/cs3220-research/rtl-universe/repos/opentitan}"

if [ ! -d "${GREEN_SOURCE}" ]; then
    echo "ERROR: GREEN_SOURCE not found: ${GREEN_SOURCE}"
    echo "Set GREEN_SOURCE=/path/to/opentitan"
    exit 1
fi

echo "=== sync-skeleton.sh ==="
echo "Green source:  ${GREEN_SOURCE}"
echo "Outputs dir:   ${OUTPUTS_DIR}"
echo ""

# Task variants that share the same skeleton and warm_src
TASK_DIRS=(
    "${OUTPUTS_DIR}/opentitan-full"
    "${OUTPUTS_DIR}/opentitan-e2e"
)

# ── Step 1: Create warm_src (full green source, minus bazel artifacts) ─────────
# We create warm_src once and link/copy it to each task variant.

WARM_SRC_STAGING=$(mktemp -d)
echo "Creating warm_src at ${WARM_SRC_STAGING}..."

rsync -a --delete \
    --exclude='.git/' \
    --exclude='bazel-*/' \
    --exclude='_build/' \
    --exclude='.bin/' \
    --exclude='__pycache__/' \
    --exclude='*.pyc' \
    "${GREEN_SOURCE}/" "${WARM_SRC_STAGING}/"

echo "warm_src staged: $(du -sh "${WARM_SRC_STAGING}" | cut -f1)"

# ── Step 2: Create skeleton (strip implementation, keep tests) ──────────────────
SKELETON_STAGING=$(mktemp -d)
echo "Creating skeleton at ${SKELETON_STAGING}..."

rsync -a --delete \
    --exclude='.git/' \
    --exclude='bazel-*/' \
    --exclude='_build/' \
    --exclude='.bin/' \
    --exclude='__pycache__/' \
    --exclude='*.pyc' \
    "${GREEN_SOURCE}/" "${SKELETON_STAGING}/"

# Strip C source implementation files (keep headers and test files)
# Pattern: strip .c files that are NOT test files (no "test", "unittest", "functest")

strip_c_sources() {
    local base_dir="$1"
    echo "Stripping C sources in ${base_dir}..."

    find "${SKELETON_STAGING}/${base_dir}" -name "*.c" \
        ! -name "*_test.c" \
        ! -name "*_functest.c" \
        ! -name "*unittest*" \
        -type f \
        | while read -r f; do
            > "${f}"  # Truncate to zero bytes (keep the file for BUILD refs)
        done
}

# Strip base library implementations
strip_c_sources "sw/device/lib/base"

# Strip DIF implementations (keep headers and unittests)
find "${SKELETON_STAGING}/sw/device/lib/dif" -maxdepth 1 -name "dif_*.c" -type f \
    | while read -r f; do
        > "${f}"
    done

# Strip runtime implementations
find "${SKELETON_STAGING}/sw/device/lib/runtime" -name "*.c" \
    ! -name "*test*" \
    -type f | while read -r f; do > "${f}"; done

# Strip crypto implementations
find "${SKELETON_STAGING}/sw/device/lib/crypto/impl" -name "*.c" \
    ! -name "*test*" \
    -type f | while read -r f; do > "${f}"; done

# Strip ujson implementation
find "${SKELETON_STAGING}/sw/device/lib/ujson" -name "*.c" \
    ! -name "*test*" \
    -type f | while read -r f; do > "${f}"; done

# Strip silicon_creator lib implementations
for dir in \
    "sw/device/silicon_creator/lib" \
    "sw/device/silicon_creator/lib/drivers" \
    "sw/device/silicon_creator/lib/sigverify" \
    "sw/device/silicon_creator/lib/sigverify/sphincsplus" \
    "sw/device/silicon_creator/lib/boot_svc" \
    "sw/device/silicon_creator/lib/ownership" \
    "sw/device/silicon_creator/lib/cert"
do
    find "${SKELETON_STAGING}/${dir}" -maxdepth 1 -name "*.c" \
        ! -name "*_functest*" \
        -type f | while read -r f; do > "${f}"; done
done

# Strip Python utility implementations (keep test files)
find "${SKELETON_STAGING}/util/design" -maxdepth 1 -name "*.py" \
    ! -name "*test*" ! -name "*_test.py" \
    | while read -r f; do > "${f}"; done
find "${SKELETON_STAGING}/util/design/lib" -name "*.py" \
    ! -name "*test*" \
    | while read -r f; do > "${f}"; done

# Strip ipgen implementation (keep test files)
find "${SKELETON_STAGING}/util/ipgen" -name "ipgen.py" \
    | while read -r f; do > "${f}"; done

# Strip OTBN trivium (keep trivium.py as a whole since it doubles as test+impl)
# NOTE: trivium.py is BOTH the library and the test — it has if __name__ == '__main__'
# Only strip the cipher BODY (not the test part). For simplicity, don't strip it here
# as the test IS the file. The test checks the class output so we need the class.
# Instead we strip only the internal cipher state implementation.
# Actually per SKILL.md: we strip implementation, keep tests.
# trivium.py IS the test (py_test srcs=["trivium.py"] main="trivium.py")
# So we KEEP trivium.py intact in skeleton.
# The "STRIPPED" versions for stripping would be the class internals... complex.
# Decision: keep trivium.py in skeleton (it's the test driver)
# and instead score it as a "freebie" (already passes without agent work)
# OR strip the class body and have agent implement it.
# We'll zero it out and let the agent implement the Trivium cipher.
> "${SKELETON_STAGING}/hw/ip/otbn/dv/otbnsim/sim/trivium.py"

echo "Skeleton created. Verifying key files..."

# Sanity: check that test files are intact
if [ -f "${SKELETON_STAGING}/sw/device/lib/dif/dif_uart_unittest.cc" ]; then
    TESTSIZE=$(wc -c < "${SKELETON_STAGING}/sw/device/lib/dif/dif_uart_unittest.cc")
    echo "  dif_uart_unittest.cc: ${TESTSIZE} bytes (expected > 0)"
fi

# Sanity: check that impl files are zeroed
if [ -f "${SKELETON_STAGING}/sw/device/lib/dif/dif_uart.c" ]; then
    IMPLSIZE=$(wc -c < "${SKELETON_STAGING}/sw/device/lib/dif/dif_uart.c")
    echo "  dif_uart.c: ${IMPLSIZE} bytes (expected 0)"
fi

# ── Step 3: Copy to each task variant ──────────────────────────────────────────
for TASK_DIR in "${TASK_DIRS[@]}"; do
    if [ -d "${TASK_DIR}" ]; then
        echo ""
        echo "Syncing to ${TASK_DIR}..."
        mkdir -p "${TASK_DIR}/environment"
        rsync -a --delete "${SKELETON_STAGING}/" "${TASK_DIR}/environment/skeleton/"
        rsync -a --delete "${WARM_SRC_STAGING}/" "${TASK_DIR}/environment/warm_src/"
        echo "  Done: skeleton=$(du -sh "${TASK_DIR}/environment/skeleton" | cut -f1), warm_src=$(du -sh "${TASK_DIR}/environment/warm_src" | cut -f1)"
    else
        echo "WARNING: Task directory not found: ${TASK_DIR}"
    fi
done

# Cleanup
rm -rf "${WARM_SRC_STAGING}" "${SKELETON_STAGING}"

echo ""
echo "=== sync-skeleton.sh complete ==="
echo "Next: Build Docker images from each task's environment/Dockerfile"
