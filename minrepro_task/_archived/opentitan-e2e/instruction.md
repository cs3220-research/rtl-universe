# OpenTitan Root of Trust — Restore the Secure Boot Firmware (E2E)

You are in `/app`, a **Bazel-based** repository for **OpenTitan** — the
lowRISC/Google open-source silicon Root of Trust. This E2E task focuses
exclusively on the **silicon_creator** (secure boot) firmware — the code that
runs in the ROM to establish the hardware Root of Trust.

The silicon_creator is the most security-critical software in OpenTitan: it
verifies and boots subsequent firmware images, manages cryptographic keys,
controls hardware security features, and handles fault injection attacks.

## Repository Structure (E2E scope)

```
sw/device/silicon_creator/
  lib/
  │   boot_data.c/h     — Boot data parsing & FLASH storage
  │   boot_log.c/h      — Debug logging in ROM context
  │   epmp_state.c/h    — Enhanced PMP configuration tracking
  │   manifest.c/h      — Firmware manifest parsing & validation
  │   shutdown.c/h      — Fault-triggered shutdown sequencing
  │   dbg_print.c/h     — Debug printf (ROM variant)
  │   *_unittest.cc     — KEPT (test files, use these as specs)
  │
  lib/drivers/           — Low-level hardware drivers (ROM context)
  │   alert.c/h         — Alert handler driver
  │   ast.c/h           — Analog Sensor Top driver
  │   clkmgr.c/h        — Clock manager driver
  │   epmp.c/h          — Enhanced PMP hardware driver
  │   flash_ctrl.c/h    — Flash controller (read/erase/program)
  │   gpio.c/h          — GPIO driver
  │   hmac.c/h          — HMAC-SHA256 hardware accelerator
  │   kmac.c/h          — KMAC hardware accelerator
  │   lifecycle.c/h     — Lifecycle state reader
  │   keymgr.c/h        — Key manager hardware driver
  │   otp.c/h           — One-Time Programmable memory driver
  │   pinmux.c/h        — Pin multiplexer driver
  │   pwrmgr.c/h        — Power manager driver
  │   rstmgr.c/h        — Reset manager driver
  │   rnd.c/h           — Hardware RNG driver
  │   spi_device.c/h    — SPI device driver
  │   uart.c/h          — UART driver (bare-metal, polling)
  │   *_unittest.cc     — KEPT
  │
  lib/sigverify/         — Signature verification
  │   mod_exp_ibex.c/h  — RSA modular exponentiation (Ibex SW)
  │   flash_exec.c/h    — Flash execute-in-place control
  │   sigverify.c/h     — ECDSA/RSA signature verification
  │   sphincsplus/       — SPHINCS+ post-quantum signature
  │     verify.c/h      — SPHINCS+ verification
  │
  lib/boot_svc/          — Boot services (ROM_EXT interaction)
  │   boot_svc_header.c/h — Boot service message header
  │   boot_svc_msg.c/h    — Boot service message handling
  │
  lib/ownership/         — Device ownership management
  │   owner_block.c/h   — Owner configuration block parsing
  │   ownership_key.c/h — Ownership key management
  │   ownership.c/h     — Ownership state machine
  │
  lib/cert/              — Certificate handling
  │   x509.c/h          — X.509 certificate parsing/generation
```

## What Has Been Stripped

All `.c` source files under `sw/device/silicon_creator/` have been truncated.
Header files, test files, and BUILD files are intact.

## Important: Partial Credit and Persistence

You are scored **proportionally** — every silicon_creator test you get to
pass earns credit. The E2E subset has ~34 tests.

**Do not give up or stop early.** Work incrementally:
1. Pick a driver or library module
2. Read the header (`.h`) to understand the API
3. Read the unittest (`.cc`) to understand what's tested
4. Implement the `.c` file to pass the tests
5. Verify: `./bazelisk.sh test //sw/device/silicon_creator/lib/drivers:uart_unittest`
6. Move to the next module

You have up to 24 hours. Use all of it.

## Suggested Implementation Order (easiest to hardest)

1. **GPIO driver** (`lib/drivers/gpio.c`) — 3-4 register operations
2. **Reset manager** (`lib/drivers/rstmgr.c`) — read reset reason register
3. **Clock manager** (`lib/drivers/clkmgr.c`) — enable/disable clocks
4. **UART driver** (`lib/drivers/uart.c`) — transmit bytes via FIFO
5. **Lifecycle** (`lib/drivers/lifecycle.c`) — read lifecycle state
6. **Error/shutdown** (`lib/shutdown.c`, `lib/epmp_state.c`) — state machines
7. **HMAC driver** (`lib/drivers/hmac.c`) — SHA256 hardware accelerator
8. **Flash controller** (`lib/drivers/flash_ctrl.c`) — complex but well-tested
9. **Sigverify** (`lib/sigverify/`) — RSA/ECDSA verification

## Scoring

Reward = `passed_tests / total_tests` (E2E subset, ~34 tests).

Verifier runs:
```
./bazelisk.sh test --keep_going <silicon_creator targets>
```
Counts targets that print `PASSED in` in Bazel output.

## Environment

- **Bazel** via `./bazelisk.sh` (auto-downloads correct version)
- **Clang** pre-installed (managed by Bazel toolchains_llvm)
- **GoogleTest** via Bazel MODULE.bazel (auto-fetched)
- No commercial EDA tools needed — all tests run on the host CPU

## Useful Commands

```bash
# Run a single driver test (fast iteration)
./bazelisk.sh test //sw/device/silicon_creator/lib/drivers:uart_unittest
./bazelisk.sh test //sw/device/silicon_creator/lib/drivers:flash_ctrl_unittest

# Run all lib tests
./bazelisk.sh test --keep_going //sw/device/silicon_creator/lib/...

# Check what a test actually tests (read the unittest source)
cat sw/device/silicon_creator/lib/drivers/uart_unittest.cc

# Check the driver API (read the header)
cat sw/device/silicon_creator/lib/drivers/uart.h

# Look at the register definitions (built from hjson)
./bazelisk.sh build //hw/top:uart_c_regs
find bazel-bin -name "uart_regs.h" 2>/dev/null | head -1 | xargs cat

# Show test output (what tests ran, what failed)
./bazelisk.sh test //sw/device/silicon_creator/lib/drivers:uart_unittest \
    --test_output=all
```

## Implementation Notes

### Hardware Abstraction

The silicon_creator drivers use the `abs_mmio` API (abstract MMIO) rather
than the standard DIF `mmio_region_*` functions. Key primitives:
```c
#include "sw/device/lib/base/abs_mmio.h"

uint32_t abs_mmio_read32(uint32_t addr);
void abs_mmio_write32(uint32_t addr, uint32_t value);
```

Register base addresses are defined in the top-level autogenerated header:
```c
#include "hw/top_earlgrey/sw/autogen/top_earlgrey.h"
// e.g., TOP_EARLGREY_UART0_BASE_ADDR
```

### Test Mocking

The unittest files use `rom_test::RomTest` base class and mock the
`abs_mmio_read32` / `abs_mmio_write32` calls. The mock simulates hardware
register behavior. Your driver implementation should:
1. Read register values with `abs_mmio_read32(base + REG_OFFSET)`
2. Write register values with `abs_mmio_write32(base + REG_OFFSET, value)`
3. Use the register field definitions from the auto-generated `*_regs.h`
