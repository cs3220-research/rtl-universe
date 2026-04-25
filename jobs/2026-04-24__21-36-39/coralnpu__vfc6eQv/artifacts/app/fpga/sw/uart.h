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

#ifndef FPGA_SW_UART_H_
#define FPGA_SW_UART_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Base address of UART1 TX register.
#define UART1_BASE 0x40010000U

// Send a single character over UART1.
void uart_putc(char c);

// Send a null-terminated string over UART1.
void uart_puts(const char *s);

// Send a 32-bit hex value over UART1 (for diagnostics).
void uart_put_hex32(uint32_t val);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_UART_H_
