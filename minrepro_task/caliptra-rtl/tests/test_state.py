"""
test_state.py — Structured pytest harness for the caliptra-rtl Harbor task.

Reads results from /tmp/_passed_count (produced by run_tests.py via test.sh).
Generates a CTRF-compatible test report for Harbor.
"""
import json
import os
import pytest


def _read_file(path, default=""):
    try:
        with open(path) as f:
            return f.read().strip()
    except FileNotFoundError:
        return default


def _read_list(path):
    content = _read_file(path)
    if not content:
        return []
    return [line.strip() for line in content.splitlines() if line.strip()]


def test_verilator_build_succeeded():
    """The Verilator simulation binary must have been built successfully."""
    build_log = _read_file('/logs/verifier/verilator_build.log')
    assert build_log, "Verilator build log is empty — build may not have run"
    assert "verilator-build" in build_log.lower() or "obj_dir" in build_log.lower(), (
        "Verilator build log does not mention expected artifacts"
    )


def test_some_tests_passed():
    """At least one integration test must pass."""
    passed_count = int(_read_file('/tmp/_passed_count', '0'))
    assert passed_count > 0, (
        "No tests passed. Check /logs/verifier/test.log for errors."
    )


def test_reward_written():
    """Reward file must exist and contain a valid float in [0, 1]."""
    reward_str = _read_file('/logs/verifier/reward.txt')
    assert reward_str, "reward.txt is empty or missing"
    try:
        reward = float(reward_str)
    except ValueError:
        pytest.fail(f"reward.txt contains non-numeric value: {repr(reward_str)}")
    assert 0.0 <= reward <= 1.0, f"reward {reward} is out of range [0, 1]"


def test_sha256_passes():
    """SHA-256 smoke test should pass (basic crypto sanity check)."""
    passed = set(_read_list('/tmp/_passed_tests'))
    assert 'smoke_test_sha256' in passed, (
        "smoke_test_sha256 did not pass — SHA-256 RTL may be incorrect"
    )


def test_sha512_passes():
    """SHA-512 smoke test should pass."""
    passed = set(_read_list('/tmp/_passed_tests'))
    assert 'smoke_test_sha512' in passed, (
        "smoke_test_sha512 did not pass — SHA-512 RTL may be incorrect"
    )


def test_hmac_passes():
    """HMAC smoke test should pass."""
    passed = set(_read_list('/tmp/_passed_tests'))
    assert 'smoke_test_hmac' in passed, (
        "smoke_test_hmac did not pass — HMAC RTL may be incorrect"
    )


def test_veer_core_passes():
    """VeeR RISC-V core smoke test should pass."""
    passed = set(_read_list('/tmp/_passed_tests'))
    assert 'smoke_test_veer' in passed, (
        "smoke_test_veer did not pass — VeeR EL2 core RTL may be incorrect"
    )


def test_mbox_passes():
    """Mailbox smoke test should pass (SoC interface sanity)."""
    passed = set(_read_list('/tmp/_passed_tests'))
    assert 'smoke_test_mbox' in passed, (
        "smoke_test_mbox did not pass — SoC interface RTL may be incorrect"
    )
