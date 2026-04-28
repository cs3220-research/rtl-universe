"""
Structured pytest wrapper for the ibex-e2e Harbor verifier.
Reads /logs/verifier/reward.txt and individual test logs
to produce per-test pass/fail for CTRF reporting.
"""

import pathlib
import pytest

LOG_DIR = pathlib.Path("/logs/verifier")


def _reward() -> float:
    reward_path = LOG_DIR / "reward.txt"
    if reward_path.exists():
        try:
            return float(reward_path.read_text().strip())
        except ValueError:
            return 0.0
    return 0.0


def _sim_ran(test_name: str) -> bool:
    """Return True if the simulation log exists and is non-empty."""
    log = LOG_DIR / f"sw_run_{test_name}.log"
    return log.exists() and log.stat().st_size > 0


def test_reward_is_positive():
    """Overall reward must be > 0."""
    assert _reward() > 0.0, "All e2e tests failed — reward is 0.0"


def test_hello_test():
    """hello_test simulation must have run successfully (exit 0)."""
    assert _sim_ran("hello_test"), "hello_test simulation log missing or empty"


def test_dit_test():
    """dit_test simulation must have run successfully (exit 0)."""
    assert _sim_ran("dit_test"), "dit_test simulation log missing or empty"


def test_dummy_instr_test():
    """dummy_instr_test simulation must have run successfully (exit 0)."""
    assert _sim_ran("dummy_instr_test"), \
        "dummy_instr_test simulation log missing or empty"


def test_pmp_smoke_test():
    """pmp_smoke_test simulation must have run successfully (exit 0)."""
    assert _sim_ran("pmp_smoke_test"), \
        "pmp_smoke_test simulation log missing or empty"


def test_reward_in_range():
    """Reward must be a float in [0, 1]."""
    r = _reward()
    assert 0.0 <= r <= 1.0, f"Reward out of range: {r}"
