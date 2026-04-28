#!/usr/bin/env bash
# sync-skeleton.sh — Regenerate skeleton/ and warm_src/ for CVA6 Harbor tasks.
#
# Reads the canonical CVA6 source from CVA6_REPO and writes:
#   <OUTPUTS_DIR>/cva6/environment/skeleton/    — stripped workspace (agent sees this)
#   <OUTPUTS_DIR>/cva6/environment/warm_src/    — green source (for Docker warm stage)
#   <OUTPUTS_DIR>/cva6-smoke/environment/skeleton/
#   <OUTPUTS_DIR>/cva6-smoke/environment/warm_src/
#
# Usage:
#   CVA6_REPO=/path/to/cva6 bash tools/sync-skeleton.sh
#
# Requirements: rsync, python3, bash 4+

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_DIR="${SCRIPT_DIR}/.."

CVA6_REPO="${CVA6_REPO:-/data/saketh/research/cs3220-research/rtl-universe/repos/cva6}"

if [ ! -d "${CVA6_REPO}/core" ]; then
    echo "ERROR: CVA6_REPO='${CVA6_REPO}' does not look like a CVA6 repo (no core/ dir)"
    exit 1
fi

echo "==> CVA6_REPO = ${CVA6_REPO}"
echo "==> OUTPUTS_DIR = ${OUTPUTS_DIR}"

# ---------------------------------------------------------------------------
# Populate submodules that are not checked out in the source repo.
# These are vendored IP (agent does NOT implement them), but they are
# required for the Flist.cva6 to resolve and the Verilator build to work.
# ---------------------------------------------------------------------------
populate_submodules() {
    local dest="$1"
    local HPDCACHE_HASH="acf97c95f2b0bf895143ed5559b04629a474548d"
    local CVFPU_HASH="a74d99a32bd11ce0b05a82b0d6d26f98df3bba65"

    echo "    Populating vendored submodules..."

    # HPDcache
    if [ ! -d "${dest}/core/cache_subsystem/hpdcache/rtl" ]; then
        echo "      Cloning cv-hpdcache..."
        rm -rf "${dest}/core/cache_subsystem/hpdcache"
        git clone --quiet https://github.com/openhwgroup/cv-hpdcache.git \
            "${dest}/core/cache_subsystem/hpdcache"
        cd "${dest}/core/cache_subsystem/hpdcache"
        git checkout "${HPDCACHE_HASH}" --quiet 2>/dev/null || true
        rm -rf "${dest}/core/cache_subsystem/hpdcache/.git"
        cd - > /dev/null
    fi

    # cvfpu
    if [ ! -d "${dest}/core/cvfpu/src" ]; then
        echo "      Cloning cvfpu..."
        rm -rf "${dest}/core/cvfpu"
        git clone --quiet https://github.com/openhwgroup/cvfpu.git "${dest}/core/cvfpu"
        cd "${dest}/core/cvfpu"
        git checkout "${CVFPU_HASH}" --quiet 2>/dev/null || true
        rm -rf "${dest}/core/cvfpu/.git"
        cd - > /dev/null
    fi

    # corev_apu submodules needed by the top-level Makefile verilate target
    if [ ! -d "${dest}/corev_apu/axi_mem_if/src" ]; then
        echo "      Cloning axi_mem_if..."
        rm -rf "${dest}/corev_apu/axi_mem_if"
        git clone --quiet https://github.com/pulp-platform/axi_mem_if.git \
            "${dest}/corev_apu/axi_mem_if"
        rm -rf "${dest}/corev_apu/axi_mem_if/.git"
    fi

    if [ ! -d "${dest}/corev_apu/riscv-dbg/src" ]; then
        echo "      Cloning riscv-dbg..."
        rm -rf "${dest}/corev_apu/riscv-dbg"
        git clone --quiet https://github.com/pulp-platform/riscv-dbg.git \
            "${dest}/corev_apu/riscv-dbg"
        cd "${dest}/corev_apu/riscv-dbg"
        git submodule update --init --recursive --quiet 2>/dev/null || true
        rm -rf "${dest}/corev_apu/riscv-dbg/.git"
        cd - > /dev/null
    fi

    if [ ! -d "${dest}/corev_apu/rv_plic/rtl" ]; then
        echo "      Cloning rv_plic..."
        rm -rf "${dest}/corev_apu/rv_plic"
        git clone --quiet https://github.com/pulp-platform/rv_plic.git \
            "${dest}/corev_apu/rv_plic"
        rm -rf "${dest}/corev_apu/rv_plic/.git"
    fi

    if [ ! -d "${dest}/corev_apu/register_interface/src" ]; then
        echo "      Cloning register_interface..."
        rm -rf "${dest}/corev_apu/register_interface"
        git clone --quiet https://github.com/pulp-platform/register_interface.git \
            "${dest}/corev_apu/register_interface"
        rm -rf "${dest}/corev_apu/register_interface/.git"
    fi

    echo "    Submodules populated."
}

# ---------------------------------------------------------------------------
# Helper: strip implementation files in a skeleton copy
#
# "Strip" means: zero out the file body while keeping it (so file-list
# references in Flist.cva6 remain valid). Package/include files are kept.
# ---------------------------------------------------------------------------
strip_implementation() {
    local skeleton_dir="$1"

    echo "    Stripping core implementation files..."

    # Zero out all synthesizable SV/V implementation files in core/
    # but KEEP:
    #   - core/include/     (package files — intact reference)
    #   - core/cvfpu/       (vendored FPU — not agent's job to recreate)
    #   - core/cache_subsystem/hpdcache/  (vendored HPDcache)
    #   - core/Flist.cva6   (build manifest)

    local KEEP_PATTERNS=(
        "core/include/"
        "core/cvfpu/"
        "core/cache_subsystem/hpdcache/"
        "Flist.cva6"
        "Flist.cva6_gate"
    )

    # Find all .sv and .v files under core/ that are not in keep patterns
    while IFS= read -r -d '' fpath; do
        local rel="${fpath#${skeleton_dir}/}"
        local keep=0
        for pat in "${KEEP_PATTERNS[@]}"; do
            if [[ "$rel" == *"${pat}"* ]]; then
                keep=1
                break
            fi
        done
        if [ "$keep" -eq 0 ]; then
            # Zero out the file (truncate but keep the path)
            > "$fpath"
        fi
    done < <(find "${skeleton_dir}/core" -type f \
        \( -name "*.sv" -o -name "*.v" \) \
        -print0)

    echo "    Core implementation files zeroed."
}

# ---------------------------------------------------------------------------
# Sync function: copy green source, then strip for skeleton
# ---------------------------------------------------------------------------
sync_task() {
    local task_name="$1"
    local task_dir="${OUTPUTS_DIR}/${task_name}"

    echo ""
    echo "==> Syncing task: ${task_name}"

    local warm_dir="${task_dir}/environment/warm_src"
    local skeleton_dir="${task_dir}/environment/skeleton"

    mkdir -p "${warm_dir}" "${skeleton_dir}"

    # Step 1: Copy green source to warm_src/ (full repo, no .git or build artifacts)
    echo "    Copying green source -> warm_src/"
    rsync -a --delete \
        --exclude='.git/' \
        --exclude='work-ver/' \
        --exclude='work-dpi/' \
        --exclude='work-vcs/' \
        --exclude='tools/' \
        --exclude='tmp/' \
        --exclude='verif/tests/riscv-tests/' \
        --exclude='verif/tests/riscv-arch-test/' \
        --exclude='verif/tests/riscv-compliance/' \
        --exclude='*.log' \
        --exclude='*.vcd' \
        --exclude='*.fst' \
        "${CVA6_REPO}/" "${warm_dir}/"

    echo "    warm_src/ synced ($(du -sh "${warm_dir}" | cut -f1))"

    # Step 1b: Populate vendored submodules in warm_src/
    populate_submodules "${warm_dir}"

    # Step 2: Copy warm_src to skeleton/, then strip
    echo "    Creating skeleton/ from warm_src/..."
    rsync -a --delete "${warm_dir}/" "${skeleton_dir}/"

    strip_implementation "${skeleton_dir}"

    echo "    skeleton/ ready ($(du -sh "${skeleton_dir}" | cut -f1))"

    # Step 3: Verify the strip worked — implementation files should be empty
    local non_empty
    non_empty=$(find "${skeleton_dir}/core" -type f \
        \( -name "*.sv" -o -name "*.v" \) \
        ! -path "*/include/*" \
        ! -path "*/cvfpu/*" \
        ! -path "*/hpdcache/*" \
        ! -name "Flist*" \
        -not -empty 2>/dev/null | wc -l)

    if [ "${non_empty}" -gt 0 ]; then
        echo "    WARNING: ${non_empty} non-empty implementation files remain in skeleton"
        echo "    (This may include intentionally kept package-like files)"
    else
        echo "    OK: all implementation files are zeroed in skeleton/"
    fi
}

# ---------------------------------------------------------------------------
# Sync both task variants
# ---------------------------------------------------------------------------
sync_task "cva6"
sync_task "cva6-smoke"

echo ""
echo "==> Done. You can now build the Docker images:"
echo ""
echo "    cd ${OUTPUTS_DIR}/cva6/environment"
echo "    docker build --build-arg _UID=\$(id -u) --build-arg _GID=\$(id -g) -t harbor-cva6 ."
echo ""
echo "    cd ${OUTPUTS_DIR}/cva6-smoke/environment"
echo "    docker build --build-arg _UID=\$(id -u) --build-arg _GID=\$(id -g) -t harbor-cva6-smoke ."
