"""
test_state.py — Harbor test state reader for nvdla-full.

Parses /logs/verifier/reward.txt and returns the scalar reward.
Called by the Harbor framework after test.sh completes.
"""

import pathlib


def get_reward() -> float:
    reward_file = pathlib.Path("/logs/verifier/reward.txt")
    try:
        return float(reward_file.read_text().strip())
    except Exception:
        return 0.0
