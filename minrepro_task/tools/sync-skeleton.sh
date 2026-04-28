#!/bin/bash
# Regenerate environment/skeleton and environment/warm_src for each Harbor task.
#
#   environment/skeleton/  <- minrepro_task/.skeleton/            (code-stripped)
#   environment/warm_src/  <- minrepro/coralnpu/                  (full green source)
#
# Both are stripped of .git to avoid leaking history to the agent or into the
# docker build context.
#
# Run this whenever the canonical skeleton or minrepro source changes.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"               # minrepro_task/
SKELETON_SRC="$ROOT/.skeleton"
WARM_SRC="$(cd "$ROOT/../minrepro/coralnpu" && pwd)"

if [ ! -d "$SKELETON_SRC" ]; then
  echo "error: canonical skeleton not found at $SKELETON_SRC" >&2
  exit 1
fi
if [ ! -d "$WARM_SRC" ]; then
  echo "error: warm source not found at $WARM_SRC" >&2
  exit 1
fi

for task in coralnpu-full coralnpu-e2e; do
  dest="$ROOT/$task/environment"
  mkdir -p "$dest/skeleton" "$dest/warm_src"

  echo "==> $task: syncing skeleton"
  rsync -a --delete \
    --exclude='.git/' --exclude='.claude/' --exclude='bazel-bin' --exclude='bazel-out' \
    --exclude='bazel-src' --exclude='bazel-testlogs' \
    "$SKELETON_SRC"/ "$dest/skeleton"/

  echo "==> $task: syncing warm_src"
  rsync -a --delete \
    --exclude='.git/' --exclude='.claude/' --exclude='bazel-bin' --exclude='bazel-out' \
    --exclude='bazel-src' --exclude='bazel-testlogs' \
    "$WARM_SRC"/ "$dest/warm_src"/
done

echo "done."
