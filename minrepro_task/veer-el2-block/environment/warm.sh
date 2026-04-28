#!/bin/bash
set -eux
export RV_ROOT=/app
cd /app

git init -q
git add -A
git -c user.email=x@x -c user.name=x commit -q -m init

# Install block-level requirements
pip3 install --user --break-system-packages --quiet -r /app/verification/block/requirements.txt 2>/dev/null || true

# Run all block-level tests via nox
cd /app/verification/block
nox -t tests 2>&1 | tee /tmp/warm_nox.log | tail -50 || true

# Count passing test functions from cocotb results.xml files
python3 - << 'PYEOF' | tee /tmp/_total
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

# Record block names
echo "dccm dcls dec dec_ib dec_pmp_ctl dec_tl dec_tlu_ctl dma dmi exu_alu exu_div exu_mul iccm ifu_compress ifu_mem_ctl lib_ahb_to_axi4 lib_axi4_to_ahb lsu_tl pic pic_gw pmp pmp_random" \
    | tr ' ' '\n' > /tmp/_block_names

# Drop green source but keep caches
find /app -mindepth 1 -maxdepth 1 \
    ! -name '.local' ! -name '.cache' \
    -exec rm -rf {} + 2>/dev/null || true
