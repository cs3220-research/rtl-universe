#!/usr/bin/env python3
"""
Parse nox status.json to count passing sessions and build the all_tests list.

nox writes status.json when nox.options.report = "status.json" is set.
Each entry in the JSON array is a session dict with at least:
  {"session": "<name>", "result": "success" | "failed" | ...}

Outputs:
  /tmp/_all_tests  -- one passing session name per line
  /tmp/_e2e_tests  -- subset: sessions whose blockName matches e2e blocks
  /tmp/_total      -- count of /tmp/_all_tests
  /tmp/_e2e_total  -- count of /tmp/_e2e_tests
"""
import json
import os
import sys

STATUS_FILE = "status.json"
ALL_TESTS_FILE = "/tmp/_all_tests"
E2E_TESTS_FILE = "/tmp/_e2e_tests"
TOTAL_FILE = "/tmp/_total"
E2E_TOTAL_FILE = "/tmp/_e2e_total"

# The top-level (E2E) block tests: dcls exercises the full core pipeline.
# Also treat any session whose blockName starts with dcls as E2E.
E2E_BLOCKS = {"dcls"}

if not os.path.exists(STATUS_FILE):
    print(f"ERROR: {STATUS_FILE} not found. nox may not have run or failed early.", file=sys.stderr)
    sys.exit(1)

with open(STATUS_FILE) as f:
    data = json.load(f)

passing = []
e2e_passing = []

for entry in data:
    session_name = entry.get("session", "")
    result = entry.get("result", "")
    if result == "success":
        passing.append(session_name)
        # Check if this session is E2E-category
        # Session names look like: "pic_verify-all-test_reset"
        # blockName is the function prefix before _verify
        for block in E2E_BLOCKS:
            if session_name.startswith(block + "_verify") or session_name.startswith(block + "-"):
                e2e_passing.append(session_name)
                break

with open(ALL_TESTS_FILE, "w") as f:
    f.write("\n".join(passing) + ("\n" if passing else ""))

with open(E2E_TESTS_FILE, "w") as f:
    f.write("\n".join(e2e_passing) + ("\n" if e2e_passing else ""))

with open(TOTAL_FILE, "w") as f:
    f.write(str(len(passing)) + "\n")

with open(E2E_TOTAL_FILE, "w") as f:
    f.write(str(len(e2e_passing)) + "\n")

print(f"Parsed status.json: {len(passing)} passing sessions, {len(e2e_passing)} E2E sessions")
for s in passing:
    print(f"  PASS: {s}")
