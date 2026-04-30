#!/usr/bin/env python3
"""
parse_l0_tests.py — Parse the L0_regression.yml and emit a JSON list of tests.

Mirrors the skip logic from run_verilator_l0_regression.py:
  - Excludes clock-gating tests (not supported by Verilator due to PC tracking issues)
  - Excludes smoke_test_dma (requires special DMA VIP setup)

Writes JSON to /tmp/_test_list.json.
"""
import yaml
import re
import json
import os
import sys

caliptra_root = os.environ.get("CALIPTRA_ROOT", "/app")
regress_file = os.path.join(
    caliptra_root, "src/integration/stimulus/L0_regression.yml"
)

# Tests skipped in Verilator per run_verilator_l0_regression.py
SKIP_PATTERN = re.compile(
    r"(smoke_test_clk_gating|smoke_test_cg_wdt|smoke_test_mbox_cg"
    r"|smoke_test_kv_cg|smoke_test_doe_cg|smoke_test_dma\b|smoke_test_wdt_rst)"
)

try:
    with open(regress_file) as fh:
        data = yaml.load(fh, Loader=yaml.FullLoader)
except FileNotFoundError:
    print(f"ERROR: {regress_file} not found", file=sys.stderr)
    print("[]")
    sys.exit(1)

tests = []
for item in data.get("contents", []):
    for key, val in item.items():
        for path in val.get("paths", []):
            m = re.search(r"\.\./test_suites/(\S+)/(\S+)\.yml", path)
            if not m:
                continue
            suite = m.group(1)
            yml_stem = m.group(2)
            if SKIP_PATTERN.search(suite):
                print(f"Skipping (filter): {suite}", file=sys.stderr)
                continue

            # Read plusargs from the per-test YAML
            yml_path = os.path.join(
                caliptra_root,
                "src/integration/test_suites",
                suite,
                yml_stem + ".yml",
            )
            plusargs = ["+CLP_REGRESSION"]
            try:
                with open(yml_path) as yf:
                    ydata = yaml.load(yf, Loader=yaml.FullLoader)
                if ydata and ydata.get("plusargs"):
                    plusargs += ydata["plusargs"]
            except Exception:
                pass

            tests.append(
                {
                    "suite": suite,
                    "yml_stem": yml_stem,
                    "testname": suite,
                    "plusargs": plusargs,
                }
            )

output_path = "/tmp/_test_list.json"
with open(output_path, "w") as out:
    json.dump(tests, out, indent=2)

print(f"Parsed {len(tests)} tests -> {output_path}", file=sys.stderr)
print(json.dumps(tests, indent=2))
