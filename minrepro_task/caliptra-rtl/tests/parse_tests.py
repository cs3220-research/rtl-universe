#!/usr/bin/env python3
"""
parse_tests.py — Parse the L0 regression YAML and output a JSON list of tests.

Mirrors the filter logic in run_verilator_l0_regression.py:
  - Skip clock-gating and DMA tests that don't work with Verilator.

Outputs JSON to stdout for consumption by run_tests.py.
"""
import yaml
import re
import json
import os
import sys

caliptra_root = os.environ.get('CALIPTRA_ROOT', '/app')
regress_file = os.path.join(caliptra_root, 'src/integration/stimulus/L0_regression.yml')

# Tests skipped in Verilator per run_verilator_l0_regression.py
skip_pattern = re.compile(
    r'(smoke_test_clk_gating|smoke_test_cg_wdt|smoke_test_mbox_cg|'
    r'smoke_test_kv_cg|smoke_test_doe_cg|smoke_test_dma\b|smoke_test_wdt_rst)'
)

try:
    with open(regress_file) as f:
        data = yaml.load(f, Loader=yaml.FullLoader)
except FileNotFoundError:
    print(f"ERROR: {regress_file} not found", file=sys.stderr)
    print('[]')
    sys.exit(1)

tests = []
for item in data.get("contents", []):
    for key, val in item.items():
        for path in val.get("paths", []):
            m = re.search(r'\.\./test_suites/(\S+)/(\S+)\.yml', path)
            if not m:
                continue
            suite = m.group(1)
            yml_stem = m.group(2)
            if skip_pattern.search(suite):
                print(f"Skipping (clock-gate/dma filter): {suite}", file=sys.stderr)
                continue

            # Read plusargs from the yml file
            yml_path = os.path.join(
                caliptra_root, 'src/integration/test_suites', suite, yml_stem + '.yml'
            )
            plusargs = ["+CLP_REGRESSION"]
            try:
                with open(yml_path) as yf:
                    ydata = yaml.load(yf, Loader=yaml.FullLoader)
                if ydata and ydata.get('plusargs'):
                    plusargs += ydata['plusargs']
            except Exception:
                pass

            tests.append({
                "suite": suite,
                "yml_stem": yml_stem,
                "testname": suite,
                "plusargs": plusargs,
            })

print(json.dumps(tests, indent=2))
print(f"Parsed {len(tests)} tests", file=sys.stderr)
