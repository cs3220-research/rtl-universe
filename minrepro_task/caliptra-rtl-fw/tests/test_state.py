"""
test_state.py — Structured pytest harness for the caliptra-rtl-fw Harbor task.

Reads results from /tmp/_passed_count (produced by run_tests.py via test.sh).
This is the harder variant where both RTL and firmware C files are stripped.
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


def test_some_tests_passed():
    """At least one integration test must pass."""
    passed_count = int(_read_file('/tmp/_passed_count', '0'))
    assert passed_count > 0, (
        "No tests passed. Check /logs/verifier/test.log for build/firmware errors."
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


def test_basic_memory_test_passes():
    """hello_world_iccm or similar basic test should pass (tests RISC-V boot)."""
    passed = set(_read_list('/tmp/_passed_tests'))
    basic_tests = {'hello_world_iccm', 'iccm_lock', 'memCpy_ROM_to_dccm', 'c_intr_handler'}
    passed_basic = passed & basic_tests
    assert passed_basic, (
        f"None of the basic boot tests passed. Expected one of: {basic_tests}. "
        f"Got: {passed or 'none'}"
    )


def test_crypto_test_passes():
    """At least one cryptographic test should pass."""
    passed = set(_read_list('/tmp/_passed_tests'))
    crypto_tests = {
        'smoke_test_sha256', 'smoke_test_sha512', 'smoke_test_hmac',
        'smoke_test_aes_gcm', 'smoke_test_kv'
    }
    passed_crypto = passed & crypto_tests
    assert passed_crypto, (
        f"No cryptographic tests passed. Expected one of: {crypto_tests}. "
        f"Got: {passed or 'none'}"
    )
