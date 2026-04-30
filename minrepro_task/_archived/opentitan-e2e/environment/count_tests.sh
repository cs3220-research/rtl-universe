#!/bin/bash
# count_tests.sh — Run green-source Bazel tests and record pass counts.
#
# This script:
#  1. Runs all targeted host-side tests (cc_test + py_test) with bazel
#  2. Parses the output to count passing tests
#  3. Records full count and E2E subset count to /tmp/_total, etc.
#
# Runs inside Docker during image build (FROM count AS count stage).
# Uses the warm_src/ green source.

set -euo pipefail
cd /app

# Ensure bazel output goes to a known location
export HOME=/home/builder
export BAZEL_CACHE="${HOME}/.cache/bazel"

# Full set of host-side test targets that don't require RTL compilation or
# commercial simulators. All are bazel cc_test or py_test targets that run
# on the host CPU.
FULL_TARGETS=(
    # Base library unit tests (test C utility functions: bitfield, math, CRC, etc.)
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

    # Runtime library unit tests
    "//sw/device/lib/runtime:print_unittest"

    # DIF (Device Interface Function) unit tests (mock-based, no RTL needed)
    # These test the C driver layer against mock register models
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

    # Crypto library unit tests
    "//sw/device/lib/crypto/impl:aes_unittest"
    "//sw/device/lib/crypto/impl:hash_unittest"
    "//sw/device/lib/crypto/impl:integrity_unittest"
    "//sw/device/lib/crypto/impl:keyblob_unittest"
    "//sw/device/lib/crypto/impl/aes_gcm:aes_gcm_unittest"

    # JSON serialization unit tests
    "//sw/device/lib/ujson:ujson_unittest"
    "//sw/device/lib/ujson:ujson_rust_unittest"

    # Silicon creator (secure boot) unit tests
    # These test the ROM-level C code: boot data, manifest, sigverify, etc.
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

    # Python utility tests (test host-side tools: FSM encoder, LFSR, etc.)
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

    # OTBN Python simulator tests
    "//hw/ip/otbn/dv/otbnsim/sim:trivium_test"
)

echo "=== Running full test suite on green source ==="
echo "Targets: ${#FULL_TARGETS[@]}"

LOG=/tmp/count_tests_full.log

# Run all targets, keep going even if some fail (green src should pass all)
./bazelisk.sh test \
    --keep_going \
    --test_output=short \
    "${FULL_TARGETS[@]}" \
    2>&1 | tee "${LOG}" || true

# Parse passing test count from Bazel output
# Bazel prints: "//path:target  PASSED in X.Xs" for each passing test
PASSED=$(grep -c "  PASSED in" "${LOG}" 2>/dev/null || echo "0")
TOTAL="${#FULL_TARGETS[@]}"

echo ""
echo "=== Count results ==="
echo "Passed: ${PASSED} / ${TOTAL}"

# Write count files for the Docker final stage
printf '%d\n' "${TOTAL}" > /tmp/_total
printf '%d\n' "${PASSED}" > /tmp/_passed

# Write the full test target list
printf '%s\n' "${FULL_TARGETS[@]}" > /tmp/_all_tests

# E2E subset: silicon_creator tests (test the secure boot firmware path)
E2E_TARGETS=(
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

printf '%s\n' "${E2E_TARGETS[@]}" > /tmp/_e2e_tests
printf '%d\n' "${#E2E_TARGETS[@]}" > /tmp/_e2e_total

echo "E2E subset: ${#E2E_TARGETS[@]} tests written to /tmp/_e2e_tests"
echo "count_tests.sh complete."
