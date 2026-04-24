from pathlib import Path

REWARD = Path("/logs/verifier/reward.txt")


def test_reward_is_valid_scalar():
    value = float(REWARD.read_text().strip())
    assert 0.0 <= value <= 1.0, f"reward out of range: {value}"


def test_all_e2e_targets_pass():
    value = float(REWARD.read_text().strip())
    assert value == 1.0, f"e2e passed fraction: {value:.4f}"
