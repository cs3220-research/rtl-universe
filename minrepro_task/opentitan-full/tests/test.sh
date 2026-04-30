#!/bin/bash
# test.sh — Verifier for the opentitan-full Harbor task.
#
# Runs all host-side Bazel tests (cc_test + py_test) against the agent's
# implementation in /app. Counts passing tests and computes a proportional
# reward.
#
# Test scope: ~122 host-side tests (DIF drivers, silicon_creator, base libs,
# Python utilities, OTBN sim). No commercial simulator required.
#
# Writes reward (float in [0,1]) to /logs/verifier/reward.txt.

set -u
mkdir -p /logs/verifier
cd /app

# ── Denominator ────────────────────────────────────────────────────────────────
if [ -r /app/.harbor/total_tests ]; then
    TOTAL=$(cat /app/.harbor/total_tests)
else
    TOTAL=122
fi

# ── Build all test targets then run them ───────────────────────────────────────
{
echo "=== OpenTitan Full Verifier ==="
echo "TOTAL tests: ${TOTAL}"
echo "Running all host-side Bazel tests..."
echo ""

# Read target list from .harbor/all_tests if available
if [ -r /app/.harbor/all_tests ]; then
    mapfile -t TARGETS < /app/.harbor/all_tests
else
    echo "WARNING: .harbor/all_tests not found, using hardcoded list"
    TARGETS=(
        "//sw/device/lib/base:crc32_unittest"
        "//sw/device/lib/base:global_mock_unittest"
        "//sw/device/lib/base:math_builtins_unittest"
        "//sw/device/lib/base:math_unittest"
        "//sw/device/lib/base:memory_unittest"
        "//sw/device/lib/base:hardened_unittest"
        "//sw/device/lib/base:random_order_unittest"
        "//sw/device/lib/base:hardened_memory_unittest"
        "//sw/device/lib/base:mock_csr_unittest"
        "//sw/device/lib/base:mmio_unittest"
        "//sw/device/lib/base:status_unittest"
        "//sw/device/lib/base:status_report_unittest_c"
        "//sw/device/lib/runtime:print_unittest"
        "//sw/device/lib/dif:adc_ctrl_unittest"
        "//sw/device/lib/dif:aes_unittest"
        "//sw/device/lib/dif:aon_timer_unittest"
        "//sw/device/lib/dif:csrng_unittest"
        "//sw/device/lib/dif:dma_unittest"
        "//sw/device/lib/dif:edn_unittest"
        "//sw/device/lib/dif:entropy_src_unittest"
        "//sw/device/lib/dif:flash_ctrl_unittest"
        "//sw/device/lib/dif:gpio_unittest"
        "//sw/device/lib/dif:hmac_unittest"
        "//sw/device/lib/dif:i2c_unittest"
        "//sw/device/lib/dif:keymgr_unittest"
        "//sw/device/lib/dif:kmac_unittest"
        "//sw/device/lib/dif:lc_ctrl_unittest"
        "//sw/device/lib/dif:mbx_unittest"
        "//sw/device/lib/dif:otbn_unittest"
        "//sw/device/lib/dif:pattgen_unittest"
        "//sw/device/lib/dif:pwm_unittest"
        "//sw/device/lib/dif:rom_ctrl_unittest"
        "//sw/device/lib/dif:rv_core_ibex_unittest"
        "//sw/device/lib/dif:rv_dm_unittest"
        "//sw/device/lib/dif:rv_timer_unittest"
        "//sw/device/lib/dif:soc_dbg_ctrl_unittest"
        "//sw/device/lib/dif:spi_device_unittest"
        "//sw/device/lib/dif:spi_host_unittest"
        "//sw/device/lib/dif:sram_ctrl_unittest"
        "//sw/device/lib/dif:sysrst_ctrl_unittest"
        "//sw/device/lib/dif:uart_unittest"
        "//sw/device/lib/dif:usbdev_unittest"
        "//sw/device/lib/crypto/impl:aes_unittest"
        "//sw/device/lib/crypto/impl:hash_unittest"
        "//sw/device/lib/crypto/impl:integrity_unittest"
        "//sw/device/lib/crypto/impl:keyblob_unittest"
        "//sw/device/lib/crypto/impl/aes_gcm:aes_gcm_unittest"
        "//sw/device/lib/ujson:ujson_unittest"
        "//sw/device/lib/ujson:ujson_rust_unittest"
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
        "//util:build_info_test"
        "//util/design:sparse-fsm-encode-test"
        "//util/design/lib:common_test"
        "//util/ipgen:test_render"
        "//util/fpga:bitstream_bisect_test"
        "//util/py/packages/lib:wrapper_test"
        "//util/py/packages/lib:ot_logging_test"
        "//util/py/packages/lib:run_test"
        "//util/py/packages/lib:bazel_test"
        "//util/py/packages/lib:register_usage_report_test"
        "//hw/ip/otbn/dv/otbnsim/sim:trivium_test"
    )
fi

# Run all tests with Bazel, keep going on failures
./bazelisk.sh test \
    --keep_going \
    --test_output=short \
    "${TARGETS[@]}" \
    2>&1 | tee /logs/verifier/test.log || true

echo ""
echo "=== Parsing results ==="

} 2>&1

# Count passing tests from Bazel output
# Bazel prints "//path:target  PASSED in X.Xs" for each passing test
PASSED=$(grep -c "  PASSED in" /logs/verifier/test.log 2>/dev/null || echo "0")

echo "Summary: passed=${PASSED} total=${TOTAL}"

# Compute reward
python3 -c "
passed = int('${PASSED:-0}')
total  = int('${TOTAL:-122}')
reward = min(1.0, passed / total) if total > 0 else 0.0
print(f'{reward:.6f}')
" > /logs/verifier/reward.txt

echo "reward: $(cat /logs/verifier/reward.txt)  (passed=${PASSED}, total=${TOTAL})"
