// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// timer: demonstrates simple timer/cycle-counter usage.

#include <stdint.h>

#include "fpga/sw/uart.h"

// Read the RISC-V cycle counter (lower 32 bits).
static inline uint32_t read_cycles(void) {
#if defined(__riscv)
    uint32_t c;
    __asm__ volatile("csrr %0, cycle" : "=r"(c));
    return c;
#else
    return 0U;
#endif
}

int main(void) {
    uint32_t t0 = read_cycles();

    // Busy-wait approximately 1 ms (50000 cycles @ 50 MHz).
    while ((read_cycles() - t0) < 50000U) {
        // busy wait
    }

    uint32_t elapsed = read_cycles() - t0;
    uart_puts("timer: elapsed cycles = ");
    uart_put_hex32(elapsed);
    uart_putc('\n');

    return 0;
}
