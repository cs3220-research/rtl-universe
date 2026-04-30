#!/bin/bash
# test.sh — Verifier for the opentitan-e2e Harbor task.
#
# E2E subset: silicon_creator (secure boot) host-side unit tests.
#
# These tests exercise the OpenTitan ROM boot stack end-to-end in software:
# the flash controller driver, UART driver, HMAC driver, OTP controller,
# key manager, sigverify, boot data parsing, manifest validation, and
# shutdown sequencing. They run as host-side GoogleTest binaries via Bazel —
# no RTL compilation or commercial simulator required.
#
# Writes reward (float in [0,1]) to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# ── Denominator ────────────────────────────────────────────────────────────────
if [ -r /app/.harbor/e2e_total ]; then
    TOTAL=$(cat /app/.harbor/e2e_total)
else
    TOTAL=34
fi

{
echo "=== OpenTitan E2E Verifier (silicon_creator subset) ==="
echo "TOTAL tests: ${TOTAL}"
echo ""

# Read E2E target list from .harbor/e2e_tests if available
if [ -r /app/.harbor/e2e_tests ]; then
    mapfile -t TARGETS < /app/.harbor/e2e_tests
else
    echo "WARNING: .harbor/e2e_tests not found, using hardcoded E2E list"
    TARGETS=(
        "//sw/device/silicon_creator/lib:boot_data_unittest"
        "//sw/device/silicon_creator/lib:boot_log_unittest"
        "//sw/device/silicon_creator/lib:epmp_unittest"
        "//sw/device/silicon_creator/lib:error_unittest"
        "//sw/device/silicon_creator/lib:manifest_unittest"
        "//sw/device/silicon_creator/lib:shutdown_unittest"
        "//sw/device/silicon_creator/lib:dbg_print_unittest"
        "//sw/device/silicon_creator/lib/drivers:alert_unittest"
        "//sw/device/silicon_creator/lib/drivers:ast_unittest"
        "//sw/device/silicon_creator/lib/drivers:clkmgr_unittest"
        "//sw/device/silicon_creator/lib/drivers:epmp_unittest"
        "//sw/device/silicon_creator/lib/drivers:flash_ctrl_unittest"
        "//sw/device/silicon_creator/lib/drivers:gpio_unittest"
        "//sw/device/silicon_creator/lib/drivers:hmac_unittest"
        "//sw/device/silicon_creator/lib/drivers:kmac_unittest"
        "//sw/device/silicon_creator/lib/drivers:lifecycle_unittest"
        "//sw/device/silicon_creator/lib/drivers:keymgr_unittest"
        "//sw/device/silicon_creator/lib/drivers:otp_unittest"
        "//sw/device/silicon_creator/lib/drivers:pinmux_unittest"
        "//sw/device/silicon_creator/lib/drivers:pwrmgr_unittest"
        "//sw/device/silicon_creator/lib/drivers:rstmgr_unittest"
        "//sw/device/silicon_creator/lib/drivers:rnd_unittest"
        "//sw/device/silicon_creator/lib/drivers:spi_device_unittest"
        "//sw/device/silicon_creator/lib/drivers:uart_unittest"
        "//sw/device/silicon_creator/lib/sigverify:mod_exp_ibex_unittest"
        "//sw/device/silicon_creator/lib/sigverify:flash_exec_unittest"
        "//sw/device/silicon_creator/lib/sigverify:sigverify_unittest"
        "//sw/device/silicon_creator/lib/sigverify/sphincsplus:verify_unittest"
        "//sw/device/silicon_creator/lib/boot_svc:boot_svc_header_unittest"
        "//sw/device/silicon_creator/lib/boot_svc:boot_svc_msg_unittest"
        "//sw/device/silicon_creator/lib/ownership:owner_block_unittest"
        "//sw/device/silicon_creator/lib/ownership:ownership_key_unittest"
        "//sw/device/silicon_creator/lib/ownership:ownership_unittest"
        "//sw/device/silicon_creator/lib/cert:x509_unittest"
    )
fi

# Run E2E tests with Bazel
./bazelisk.sh test \
    --keep_going \
    --test_output=short \
    "${TARGETS[@]}" \
    2>&1 | tee /logs/verifier/test.log || true

echo ""
echo "=== Parsing results ==="

} 2>&1

# Count passing tests
PASSED=$(grep -c "  PASSED in" /logs/verifier/test.log 2>/dev/null || echo "0")

echo "Summary: passed=${PASSED} total=${TOTAL}"

# Compute reward
python3 -c "
passed = int('${PASSED:-0}')
total  = int('${TOTAL:-34}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
