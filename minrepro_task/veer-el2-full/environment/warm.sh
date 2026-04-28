#!/bin/bash
set -eux
export RV_ROOT=/app
cd /app

git init -q
git add -A
git -c user.email=x@x -c user.name=x commit -q -m init

# Generate VeeR config headers (needed for all compilations)
mkdir -p /tmp/warm_sim
cd /tmp/warm_sim
$RV_ROOT/configs/veer.config -set build_axi4

# Build the verilated model from the top-level Makefile
make -f $RV_ROOT/tools/Makefile verilator-build 2>&1 | tail -30 || true

# Run all block-level cocotb tests via nox
cd $RV_ROOT/verification/block
pip3 install --user --break-system-packages --no-cache-dir -r requirements.txt 2>/dev/null || true
nox -t tests 2>&1 | tee /tmp/warm_nox.log | tail -40 || true

# Count passing test functions from nox results.xml files
python3 - << 'PYEOF' 2>/dev/null | tee /tmp/_total
import os, glob
from xml.etree import ElementTree as ET
passed = 0
for xml_file in glob.glob('/app/verification/block/**/*.xml', recursive=True):
    try:
        tree = ET.parse(xml_file)
        for ts in tree.iter('testsuite'):
            for tc in ts.iter('testcase'):
                failures = list(tc.iter('failure')) + list(tc.iter('error'))
                if not failures:
                    passed += 1
    except Exception:
        pass
print(passed)
PYEOF

# Record nox session names
nox -l 2>/dev/null | grep "^*" > /tmp/_nox_sessions || true

# Drop green source but keep caches
find /app -mindepth 1 -maxdepth 1 \
    ! -name '.local' ! -name '.cache' \
    -exec rm -rf {} + 2>/dev/null || true
