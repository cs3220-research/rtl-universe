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

#ifndef FPGA_SW_SPI_FLASH_H_
#define FPGA_SW_SPI_FLASH_H_

#include <stddef.h>
#include <stdint.h>

#include "fpga/sw/spi.h"
#include "fpga/sw/gpio.h"

#ifdef __cplusplus
extern "C" {
#endif

// SPI flash result codes.
#define SPI_FLASH_OK     0
#define SPI_FLASH_ERROR  (-1)

// Common JEDEC flash command opcodes.
#define SPI_FLASH_CMD_READ_ID       0x9FU
#define SPI_FLASH_CMD_READ          0x03U
#define SPI_FLASH_CMD_FAST_READ     0x0BU
#define SPI_FLASH_CMD_WRITE_ENABLE  0x06U
#define SPI_FLASH_CMD_WRITE_DISABLE 0x04U
#define SPI_FLASH_CMD_READ_STATUS   0x05U
#define SPI_FLASH_CMD_PAGE_PROGRAM  0x02U
#define SPI_FLASH_CMD_SECTOR_ERASE  0xD8U
#define SPI_FLASH_CMD_BULK_ERASE    0xC7U

// SPI flash device info.
typedef struct {
    uint32_t capacity;     // total capacity in bytes
    uint32_t sector_size;  // erase sector size in bytes
    uint32_t page_size;    // program page size in bytes
    uint8_t  mfr_id;       // manufacturer ID
    uint8_t  dev_id[2];    // device ID bytes
} spi_flash_info_t;

// SPI flash handle.
typedef struct {
    spi_t    spi;
    uint32_t gpio_base;
    uint32_t cs_pin;
} spi_flash_t;

// Initialize a SPI flash handle.
void spi_flash_init(spi_flash_t *flash, uint32_t spi_base, uint32_t gpio_base,
                    uint32_t cs_pin);

// Read JEDEC ID and populate info struct.  Returns SPI_FLASH_OK or error.
int spi_flash_read_id(spi_flash_t *flash, spi_flash_info_t *info);

// Read `len` bytes from flash at `addr` into `buf`.
int spi_flash_read(spi_flash_t *flash, uint32_t addr, uint8_t *buf, size_t len);

// Erase a sector containing `addr`.
int spi_flash_sector_erase(spi_flash_t *flash, uint32_t addr);

// Program up to one page at `addr` from `buf`.
int spi_flash_page_program(spi_flash_t *flash, uint32_t addr,
                           const uint8_t *buf, size_t len);

// Wait for flash to become not-busy (WIP bit clears).
int spi_flash_wait_ready(spi_flash_t *flash);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_SPI_FLASH_H_
