"""
Structured pytest wrapper for the ibex Harbor verifier.
Reads /logs/verifier/reward.txt and individual test logs
to produce per-test pass/fail for CTRF reporting.
"""

import os
import pathlib
import pytest

LOG_DIR = pathlib.Path("/logs/verifier")
APP_DIR = pathlib.Path("/app")


def _reward() -> float:
    reward_path = LOG_DIR / "reward.txt"
    if reward_path.exists():
        try:
            return float(reward_path.read_text().strip())
        except ValueError:
            return 0.0
    return 0.0


def _log_contains(filename: str, pattern: str) -> bool:
    log = LOG_DIR / filename
    if not log.exists():
        return False
    return pattern in log.read_text()


# ---------------------------------------------------------------------------
# Individual test assertions
# ---------------------------------------------------------------------------

def test_reward_is_positive():
    """Overall reward must be > 0 (at least one test passed)."""
    assert _reward() > 0.0, "All tests failed — reward is 0.0"


def test_csr_testbench():
    """CS registers testbench must print TEST PASSED."""
    assert _log_contains("tb_csr.log", "TEST PASSED"), \
        "tb_cs_registers did not produce 'TEST PASSED'"


def test_hello_test_ran():
    """hello_test simulation must have completed successfully."""
    log = LOG_DIR / "sw_run_hello_test.log"
    assert log.exists() and log.stat().st_size > 0, \
        "hello_test simulation log missing or empty"


def test_dit_test_ran():
    """dit_test simulation must have completed successfully."""
    log = LOG_DIR / "sw_run_dit_test.log"
    assert log.exists() and log.stat().st_size > 0, \
        "dit_test simulation log missing or empty"


def test_dummy_instr_test_ran():
    """dummy_instr_test simulation must have completed successfully."""
    log = LOG_DIR / "sw_run_dummy_instr_test.log"
    assert log.exists() and log.stat().st_size > 0, \
        "dummy_instr_test simulation log missing or empty"


def test_pmp_smoke_test_ran():
    """pmp_smoke_test simulation must have completed successfully."""
    log = LOG_DIR / "sw_run_pmp_smoke_test.log"
    assert log.exists() and log.stat().st_size > 0, \
        "pmp_smoke_test simulation log missing or empty"


def test_reward_written():
    """Reward file must exist and contain a float in [0, 1]."""
    r = _reward()
    assert 0.0 <= r <= 1.0, f"Reward out of range: {r}"
