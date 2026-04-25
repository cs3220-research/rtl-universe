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

// spi_test: exercises the SPI driver and prints UART output.

#include <stdint.h>

#include "fpga/sw/spi.h"
#include "fpga/sw/uart.h"

int main(void) {
    spi_t spi;
    spi_init(&spi, SPI_BASE, 0U);

    spi_cs_assert(&spi);
    uint8_t rx = spi_transfer_byte(&spi, 0xA5U);
    spi_cs_deassert(&spi);
    (void)rx;

    uart_puts("SPI test done\n");
    return 0;
}
