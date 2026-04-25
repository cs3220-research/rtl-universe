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

// spi_flash_test: exercises the SPI flash driver stub and prints "PASS\n".

#include <stdint.h>

#include "fpga/sw/gpio.h"
#include "fpga/sw/spi.h"
#include "fpga/sw/spi_flash.h"
#include "fpga/sw/uart.h"

int main(void) {
    spi_flash_t flash;
    spi_flash_init(&flash, SPI_BASE, GPIO_BASE, /*cs_pin=*/0U);

    spi_flash_info_t info;
    int rc = spi_flash_read_id(&flash, &info);
    if (rc != SPI_FLASH_OK) {
        uart_puts("FAIL: spi_flash_read_id\n");
        return 1;
    }

    // Stub simulation always returns success; just verify the call completed.
    uart_puts("PASS\n");
    return 0;
}
