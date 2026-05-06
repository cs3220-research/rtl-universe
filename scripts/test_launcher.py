#!/usr/bin/env python3
"""Tests for launcher's decision logic — catch bugs before they ship."""
import os, sys, time, tempfile, shutil
from pathlib import Path
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(__file__))
import launcher

PASS = 0
FAIL = 0

def assert_eq(actual, expected, label):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        print(f"  PASS  {label}")
    else:
        FAIL += 1
        print(f"  FAIL  {label}: expected {expected!r}, got {actual!r}")

def make_trial(jobs_dir, jobname, task, reward=None, exception=False, log_size=0):
    trial = jobs_dir / jobname / f"{task}__test"
    trial.mkdir(parents=True, exist_ok=True)
    (trial / "agent").mkdir(exist_ok=True)
    (trial / "verifier").mkdir(exist_ok=True)
    if log_size > 0:
        (trial / "agent" / "codex.txt").write_bytes(b"x" * log_size)
    if reward is not None:
        (trial / "verifier" / "reward.txt").write_text(f"{reward:.6f}\n")
    if exception:
        (trial / "exception.txt").write_text("Test exception\n")
    return trial

def test_is_done_filters_false_positives():
    print("\n=== is_done() false-positive filter ===")
    with tempfile.TemporaryDirectory() as td:
        jobs = Path(td) / "jobs"
        jobs.mkdir()
        with patch.object(launcher, 'JOBS', jobs):
            make_trial(jobs, "codex-gpt55-xhigh-foo", "foo", reward=0.5, log_size=200_000)
            assert_eq(launcher.is_done(jobs / "codex-gpt55-xhigh-foo", "foo"), True,
                      "real run with big log + reward")
            make_trial(jobs, "codex-gpt55-xhigh-bar", "bar", reward=0.0, exception=True, log_size=6500)
            assert_eq(launcher.is_done(jobs / "codex-gpt55-xhigh-bar", "bar"), False,
                      "auth-fail (tiny log + exception) NOT done")
            make_trial(jobs, "codex-gpt55-xhigh-baz", "baz", reward=0.0, log_size=150_000)
            assert_eq(launcher.is_done(jobs / "codex-gpt55-xhigh-baz", "baz"), True,
                      "legit zero-score (big log, no exception) done")
            make_trial(jobs, "codex-gpt55-xhigh-qux", "qux", reward=None, log_size=50000)
            assert_eq(launcher.is_done(jobs / "codex-gpt55-xhigh-qux", "qux"), False,
                      "no reward not done")

def test_recently_failed_uses_launcher_start_ts():
    print("\n=== recently_failed() respects LAUNCHER_START_TS ===")
    with tempfile.TemporaryDirectory() as td:
        jobs = Path(td) / "jobs"
        jobs.mkdir()
        # Set LAUNCHER_START_TS to 60s ago so freshly-created files are clearly newer
        with patch.object(launcher, 'JOBS', jobs), \
             patch.object(launcher, 'LAUNCHER_START_TS', time.time() - 60):
            trial = make_trial(jobs, "codex-gpt55-xhigh-foo", "foo", exception=True)
            old_time = time.time() - 7200
            os.utime(trial / "exception.txt", (old_time, old_time))
            assert_eq(launcher.recently_failed("gpt55", "foo"), False,
                      "old exception NOT recently failed")
            make_trial(jobs, "codex-gpt55-xhigh-bar", "bar", exception=True)
            assert_eq(launcher.recently_failed("gpt55", "bar"), True,
                      "fresh exception recently failed")

def test_matching_job_dirs_handles_suffixes():
    print("\n=== _matching_job_dirs() retry suffixes ===")
    with tempfile.TemporaryDirectory() as td:
        jobs = Path(td) / "jobs"
        jobs.mkdir()
        for name in ["codex-gpt55-xhigh-ibex-e2e",
                     "codex-gpt55-xhigh-ibex-e2e-r2",
                     "codex-gpt55-xhigh-ibex-full"]:
            (jobs / name).mkdir()
        with patch.object(launcher, 'JOBS', jobs):
            dirs = launcher._matching_job_dirs("gpt55", "ibex-e2e")
            names = sorted(d.name for d in dirs)
            assert_eq(names,
                      ["codex-gpt55-xhigh-ibex-e2e", "codex-gpt55-xhigh-ibex-e2e-r2"],
                      "matches exact + -r2, NOT ibex-full")

def test_recently_launched_guard():
    print("\n=== _RECENTLY_LAUNCHED race-guard ===")
    launcher._RECENTLY_LAUNCHED.clear()
    real_entry = next(m for m in launcher.MODELS if m[0] == "gpt55")
    with patch.object(launcher.subprocess, 'Popen'), \
         patch.object(launcher.subprocess, 'run'), \
         patch.object(launcher, 'env_or_die', return_value="fake-key"):
        launcher.launch(real_entry, "ibex-e2e")
        first_ts = launcher._RECENTLY_LAUNCHED.get("codex-gpt55-xhigh-ibex-e2e")
        assert_eq(first_ts is not None, True, "first launch records timestamp")
        launcher.launch(real_entry, "ibex-e2e")
        second_ts = launcher._RECENTLY_LAUNCHED.get("codex-gpt55-xhigh-ibex-e2e")
        assert_eq(second_ts, first_ts, "2nd launch within 2min skipped (race-guard)")

def test_kill_stuck_jobs_no_log_shadow():
    print("\n=== kill_stuck_jobs() no log() shadow ===")
    with patch.object(launcher, '_harbor_proc_info', return_value=[]):
        try:
            result = launcher.kill_stuck_jobs()
            assert_eq(result, [], "empty harbor list returns []")
        except TypeError as e:
            global FAIL
            FAIL += 1
            print(f"  FAIL  kill_stuck_jobs crashed: {e}")

def test_covered_aggregates():
    print("\n=== covered() aggregates across job dirs ===")
    with tempfile.TemporaryDirectory() as td:
        jobs = Path(td) / "jobs"
        jobs.mkdir()
        make_trial(jobs, "codex-gpt55-xhigh-ibex-e2e", "ibex-e2e",
                   reward=0.0, exception=True, log_size=6000)
        make_trial(jobs, "codex-gpt55-xhigh-ibex-e2e-r2", "ibex-e2e",
                   reward=1.0, log_size=200_000)
        with patch.object(launcher, 'JOBS', jobs):
            assert_eq(launcher.covered("gpt55", "ibex-e2e"), True,
                      "at least one legit run covered")
    with tempfile.TemporaryDirectory() as td2:
        jobs2 = Path(td2) / "jobs"
        jobs2.mkdir()
        make_trial(jobs2, "codex-gpt55-xhigh-ibex-e2e", "ibex-e2e",
                   reward=0.0, exception=True, log_size=6000)
        with patch.object(launcher, 'JOBS', jobs2):
            assert_eq(launcher.covered("gpt55", "ibex-e2e"), False,
                      "only broken runs NOT covered")

if __name__ == "__main__":
    test_is_done_filters_false_positives()
    test_recently_failed_uses_launcher_start_ts()
    test_matching_job_dirs_handles_suffixes()
    test_recently_launched_guard()
    test_kill_stuck_jobs_no_log_shadow()
    test_covered_aggregates()
    print(f"\n=== {PASS} pass, {FAIL} fail ===")
    sys.exit(1 if FAIL else 0)
