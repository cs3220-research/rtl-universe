#!/bin/bash
# run_benchmark.sh — Run a Harbor benchmark with standard settings
#
# Usage:
#   ./run_benchmark.sh <task-path> [model] [job-name]
#
# Examples:
#   ./run_benchmark.sh minrepro_task/coralnpu sonnet
#   ./run_benchmark.sh minrepro_task/coralnpu-e2e opus my-e2e-run

set -euo pipefail

TASK_PATH="${1:?Usage: run_benchmark.sh <task-path> [model] [job-name]}"
MODEL="${2:-sonnet}"
JOB_NAME="${3:-}"

# Set up credentials mount for OAuth token refresh
CREDS_DIR=/tmp/harbor-claude-creds
mkdir -p "$CREDS_DIR"
cp ~/.claude/.credentials.json "$CREDS_DIR/.credentials.json"
chmod 700 "$CREDS_DIR"
chmod 600 "$CREDS_DIR/.credentials.json"

# Build harbor run command
CMD=(
  uvx harbor run
  --path "$TASK_PATH"
  --agent claude-code
  --model "$MODEL"
  -n 1 -y
  --mounts-json "[\"$CREDS_DIR:/home/builder/.claude\"]"
  --ae CLAUDE_CONFIG_DIR=/home/builder/.claude
  --artifact /app
)

if [ -n "$JOB_NAME" ]; then
  CMD+=(--job-name "$JOB_NAME")
fi

echo "Running Harbor benchmark:"
echo "  Task: $TASK_PATH"
echo "  Model: $MODEL"
echo "  Credentials: mounted with refresh token"
echo "  Artifact: /app (captured after run)"
echo ""

exec "${CMD[@]}"
