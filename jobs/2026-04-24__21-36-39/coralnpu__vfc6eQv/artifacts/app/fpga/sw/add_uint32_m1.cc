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

// add_uint32_m1: reads a 32-bit value, computes value + 1, and outputs the
// result byte-by-byte over UART (little-endian).
//
// The input value is supplied via a well-known memory symbol so it can be
// patched by the host before execution.

#include <stdint.h>

#include "fpga/sw/uart.h"

// Input operand patched by the host (placed in DTCM).
volatile uint32_t add_uint32_m1_input __attribute__((used, section(".dtcm"))) =
    0U;

// Output written by the firmware (placed in DTCM).
volatile uint32_t add_uint32_m1_output __attribute__((used, section(".dtcm"))) =
    0U;

int main(void) {
    uint32_t result = add_uint32_m1_input + 1U;
    add_uint32_m1_output = result;

    // Emit the 4 bytes of `result` over UART (little-endian) so the host can
    // read them from the UART stream if needed.
    uart_putc((char)(result & 0xFFU));
    uart_putc((char)((result >> 8) & 0xFFU));
    uart_putc((char)((result >> 16) & 0xFFU));
    uart_putc((char)((result >> 24) & 0xFFU));

    return 0;
}
