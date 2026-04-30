#!/usr/bin/env python3
"""
compute_reward.py — Compute proportional reward for caliptra-e2e verifier.

Reads:
  /tmp/_passed_count        — integer count of passing E2E tests
  /app/.harbor/total_tests  — integer denominator (from Docker build)

Writes:
  /logs/verifier/reward.txt — float in [0.0, 1.0]
"""
import os


def read_int(path, default=0):
    try:
        with open(path) as fh:
            return int(fh.read().strip())
    except (FileNotFoundError, ValueError):
        return default


passed = read_int("/tmp/_passed_count", 0)
total = read_int("/app/.harbor/total_tests", 15)

reward = passed / total if total > 0 else 0.0
reward_str = f"{reward:.6f}"

os.makedirs("/logs/verifier", exist_ok=True)
with open("/logs/verifier/reward.txt", "w") as fh:
    fh.write(reward_str + "\n")

print(f"reward: {reward_str}  (passed={passed}, total={total})")
