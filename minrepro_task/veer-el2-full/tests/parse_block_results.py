#!/usr/bin/env python3
"""
Parse nox status.json from block test run and write passing count.

Reads:  /logs/verifier/status.json
Writes: /logs/verifier/passed.txt   (one session name per line)
        /logs/verifier/passed_count (integer)
"""
import json
import sys

STATUS_FILE = "/logs/verifier/status.json"
PASSED_FILE = "/logs/verifier/passed.txt"
COUNT_FILE = "/logs/verifier/passed_count"

try:
    with open(STATUS_FILE) as f:
        data = json.load(f)
except (FileNotFoundError, json.JSONDecodeError) as e:
    print(f"ERROR reading {STATUS_FILE}: {e}", file=sys.stderr)
    with open(COUNT_FILE, "w") as f:
        f.write("0\n")
    with open(PASSED_FILE, "w") as f:
        pass
    sys.exit(0)

passing = []
for entry in data:
    session_name = entry.get("session", "")
    result = entry.get("result", "")
    if result == "success":
        passing.append(session_name)

with open(PASSED_FILE, "w") as f:
    f.write("\n".join(passing) + ("\n" if passing else ""))

with open(COUNT_FILE, "w") as f:
    f.write(str(len(passing)) + "\n")

print(f"Parsed: {len(passing)} passing sessions")
for s in passing:
    print(f"  PASS: {s}")
