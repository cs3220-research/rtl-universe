#!/bin/bash
# Supervisor for scripts/launcher.py.
# - Adopts existing launcher if one is already running (idempotent restart).
# - Restarts launcher if it crashes (5s delay).
# - SIGKILLs and restarts launcher if /tmp/launcher.log goes stale >5min (hang detection).
# - Stop via: touch /tmp/launcher.stop  OR  kill the supervisor PID.

cd "$(dirname "$0")/.."
set -a
source .env 2>/dev/null
set +a
export OPENROUTER_API_KEY

STALE_THRESHOLD=300   # 5 min — kill if no log update
LAUNCHER_PID=""

start_launcher() {
  # Adopt any existing launcher first (idempotent restart)
  EXISTING=$(pgrep -f "python.*scripts/launcher.py" | head -1)
  if [ -n "$EXISTING" ]; then
    echo "[supervisor $(date +%H:%M:%S)] adopting existing launcher PID=$EXISTING" >> /tmp/launcher.log
    LAUNCHER_PID=$EXISTING
    return
  fi
  echo "[supervisor $(date +%H:%M:%S)] starting launcher" >> /tmp/launcher.log
  python3 scripts/launcher.py >> /tmp/launcher.log 2>&1 &
  LAUNCHER_PID=$!
}

while true; do
  if [ -f /tmp/launcher.stop ]; then
    echo "[supervisor $(date +%H:%M:%S)] STOP file present — exiting" >> /tmp/launcher.log
    [ -n "$LAUNCHER_PID" ] && kill $LAUNCHER_PID 2>/dev/null
    exit 0
  fi

  # SAFETY: pause launcher if root partition is too full.
  # Other lab users share /, so bloated /tmp/harbor-claude-creds-* dirs
  # (Claude Code can write hundreds of GB of session data) must not crash the host.
  ROOT_USE=$(df --output=pcent / | tail -1 | tr -d ' %')
  if [ "${ROOT_USE:-0}" -ge 90 ]; then
    if [ ! -f /tmp/launcher.pause ]; then
      echo "[supervisor $(date +%H:%M:%S)] / is ${ROOT_USE}% full — pausing launcher (touch /tmp/launcher.pause)" >> /tmp/launcher.log
      touch /tmp/launcher.pause
    fi
  elif [ "${ROOT_USE:-0}" -lt 80 ] && [ -f /tmp/launcher.pause ]; then
    # Only auto-resume if pause was set by us due to disk
    echo "[supervisor $(date +%H:%M:%S)] / dropped to ${ROOT_USE}% — resuming launcher" >> /tmp/launcher.log
    rm -f /tmp/launcher.pause
  fi

  if [ -z "$LAUNCHER_PID" ] || ! kill -0 $LAUNCHER_PID 2>/dev/null; then
    start_launcher
    sleep 5
    continue
  fi

  # Hang detection: launcher.log mtime vs now
  if [ -f /tmp/launcher.log ]; then
    LAST_WRITE=$(stat -c %Y /tmp/launcher.log)
    NOW=$(date +%s)
    AGE=$(( NOW - LAST_WRITE ))
    if [ "$AGE" -gt "$STALE_THRESHOLD" ]; then
      echo "[supervisor $(date +%H:%M:%S)] launcher log stale ${AGE}s — SIGKILL pid=$LAUNCHER_PID" >> /tmp/launcher.log
      kill -9 $LAUNCHER_PID 2>/dev/null
      sleep 2
      LAUNCHER_PID=""
      continue
    fi
  fi

  sleep 30
done
