from pathlib import Path

REWARD = Path("/logs/verifier/reward.txt")

def test_reward_is_valid_scalar():
    value = float(REWARD.read_text().strip())
    assert 0.0 <= value <= 1.0

def test_all_targets_pass():
    value = float(REWARD.read_text().strip())
    assert value == 1.0
