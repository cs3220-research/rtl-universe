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

#include "fpga/sw/clk.h"

// Cycles per millisecond / microsecond derived from CLK_FREQ_HZ.
#define CYCLES_PER_MS  (CLK_FREQ_HZ / 1000U)
#define CYCLES_PER_US  (CLK_FREQ_HZ / 1000000U)

// Read the RISC-V cycle counter (lower 32 bits).
static inline uint32_t read_cycles(void) {
#if defined(__riscv)
    uint32_t cycles;
    __asm__ volatile("csrr %0, cycle" : "=r"(cycles));
    return cycles;
#else
    // Fallback for non-RISC-V build hosts (e.g., analysis tools).
    static uint32_t fake_counter = 0;
    return fake_counter++;
#endif
}

uint32_t clk_get_cycles(void) {
    return read_cycles();
}

void clk_delay_us(uint32_t us) {
    uint32_t start = read_cycles();
    uint32_t ticks = us * CYCLES_PER_US;
    while ((read_cycles() - start) < ticks) {
        // busy wait
    }
}

void clk_delay_ms(uint32_t ms) {
    uint32_t start = read_cycles();
    uint32_t ticks = ms * CYCLES_PER_MS;
    while ((read_cycles() - start) < ticks) {
        // busy wait
    }
}
