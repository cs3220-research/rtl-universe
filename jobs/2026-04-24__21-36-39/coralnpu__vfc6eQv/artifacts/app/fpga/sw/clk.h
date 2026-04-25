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

#ifndef FPGA_SW_CLK_H_
#define FPGA_SW_CLK_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// SoC clock frequency in Hz (50 MHz).
#define CLK_FREQ_HZ 50000000U

// Busy-wait for approximately `ms` milliseconds using cycle counting.
void clk_delay_ms(uint32_t ms);

// Busy-wait for approximately `us` microseconds using cycle counting.
void clk_delay_us(uint32_t us);

// Return an approximate cycle count (uses the RISC-V cycle CSR when available,
// otherwise a software counter).
uint32_t clk_get_cycles(void);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_CLK_H_
