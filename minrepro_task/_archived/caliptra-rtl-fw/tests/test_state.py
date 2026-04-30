"""
test_state.py — Structured pytest harness for the caliptra-e2e Harbor task.

Reads results from files produced by run_e2e_tests.py (via test.sh).
"""
import os
import pytest


def _read_file(path, default=""):
    try:
        with open(path) as fh:
            return fh.read().strip()
    except FileNotFoundError:
        return default


def _read_list(path):
    content = _read_file(path)
    if not content:
        return []
    return [line.strip() for line in content.splitlines() if line.strip()]


def test_verilator_build_succeeded():
    """The Verilator simulation binary must have been built successfully."""
    build_log = _read_file("/logs/verifier/verilator_build.log")
    assert build_log, "Verilator build log is empty — build may not have run"
    assert "obj_dir" in build_log or "verilator-build" in build_log.lower(), (
        "Verilator build log does not reference expected artifacts"
    )


def test_some_e2e_tests_passed():
    """At least one E2E integration test must pass."""
    passed_count = int(_read_file("/tmp/_passed_count", "0"))
    assert passed_count > 0, (
        "No E2E tests passed. Check /logs/verifier/test.log for errors."
    )


def test_reward_written():
    """Reward file must exist and contain a valid float in [0, 1]."""
    reward_str = _read_file("/logs/verifier/reward.txt")
    assert reward_str, "reward.txt is empty or missing"
    try:
        reward = float(reward_str)
    except ValueError:
        pytest.fail(f"reward.txt contains non-numeric value: {repr(reward_str)}")
    assert 0.0 <= reward <= 1.0, f"reward {reward} is out of range [0, 1]"


def test_sha256_passes():
    """SHA-256 E2E test must pass (fundamental crypto block)."""
    passed = set(_read_list("/tmp/_passed_tests"))
    assert "smoke_test_sha256" in passed, (
        "smoke_test_sha256 did not pass — SHA-256 RTL may be incorrect"
    )


def test_sha512_passes():
    """SHA-512 E2E test must pass."""
    passed = set(_read_list("/tmp/_passed_tests"))
    assert "smoke_test_sha512" in passed, (
        "smoke_test_sha512 did not pass — SHA-512 RTL may be incorrect"
    )


def test_hmac_passes():
    """HMAC E2E test must pass."""
    passed = set(_read_list("/tmp/_passed_tests"))
    assert "smoke_test_hmac" in passed, (
        "smoke_test_hmac did not pass — HMAC RTL may be incorrect"
    )


def test_mbox_passes():
    """Mailbox E2E test must pass (SoC interface sanity)."""
    passed = set(_read_list("/tmp/_passed_tests"))
    assert "smoke_test_mbox" in passed, (
        "smoke_test_mbox did not pass — SoC interface RTL may be incorrect"
    )


def test_kv_passes():
    """Key Vault E2E test must pass (multi-block crypto+keyvault flow)."""
    passed = set(_read_list("/tmp/_passed_tests"))
    assert "smoke_test_kv" in passed, (
        "smoke_test_kv did not pass — Key Vault RTL may be incorrect"
    )
