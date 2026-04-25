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

#include "fpga/sw/uart.h"

// Write a single byte to the UART TX register (any 32-bit write sends the
// lowest byte as a character on the wire).
static inline void uart_write_reg(char c) {
    volatile uint32_t *tx = (volatile uint32_t *)UART1_BASE;
    *tx = (uint32_t)(uint8_t)c;
}

void uart_putc(char c) {
    uart_write_reg(c);
}

void uart_puts(const char *s) {
    while (*s) {
        uart_write_reg(*s);
        s++;
    }
}

void uart_put_hex32(uint32_t val) {
    static const char hex[] = "0123456789abcdef";
    uart_puts("0x");
    for (int i = 28; i >= 0; i -= 4) {
        uart_putc(hex[(val >> i) & 0xFU]);
    }
}
