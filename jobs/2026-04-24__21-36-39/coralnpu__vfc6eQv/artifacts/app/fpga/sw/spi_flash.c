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

#include "fpga/sw/spi_flash.h"
#include "fpga/sw/spi.h"
#include "fpga/sw/gpio.h"

// STATUS register WIP (write-in-progress) bit.
#define FLASH_STATUS_WIP (1U << 0)

static void flash_cs_assert(spi_flash_t *flash) {
    gpio_set_pin(flash->gpio_base, flash->cs_pin, 0U);  // active-low CS
    spi_cs_assert(&flash->spi);
}

static void flash_cs_deassert(spi_flash_t *flash) {
    spi_cs_deassert(&flash->spi);
    gpio_set_pin(flash->gpio_base, flash->cs_pin, 1U);
}

void spi_flash_init(spi_flash_t *flash, uint32_t spi_base, uint32_t gpio_base,
                    uint32_t cs_pin) {
    spi_init(&flash->spi, spi_base, 0U);
    flash->gpio_base = gpio_base;
    flash->cs_pin    = cs_pin;
    // Configure CS pin as output and deassert.
    gpio_set_direction(gpio_base, 1U << cs_pin, 1U << cs_pin);
    gpio_set_pin(gpio_base, cs_pin, 1U);
}

int spi_flash_wait_ready(spi_flash_t *flash) {
    uint8_t status;
    do {
        flash_cs_assert(flash);
        spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_READ_STATUS);
        status = spi_transfer_byte(&flash->spi, 0xFFU);
        flash_cs_deassert(flash);
    } while (status & FLASH_STATUS_WIP);
    return SPI_FLASH_OK;
}

int spi_flash_read_id(spi_flash_t *flash, spi_flash_info_t *info) {
    uint8_t id[3];
    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_READ_ID);
    id[0] = spi_transfer_byte(&flash->spi, 0xFFU);
    id[1] = spi_transfer_byte(&flash->spi, 0xFFU);
    id[2] = spi_transfer_byte(&flash->spi, 0xFFU);
    flash_cs_deassert(flash);

    if (info) {
        info->mfr_id    = id[0];
        info->dev_id[0] = id[1];
        info->dev_id[1] = id[2];
        // Capacity/geometry stubs; real firmware would decode from SFDP.
        info->capacity    = 64U * 1024U * 1024U;  // 64 MB
        info->sector_size = 256U * 1024U;          // 256 KB
        info->page_size   = 256U;
    }
    return SPI_FLASH_OK;
}

int spi_flash_read(spi_flash_t *flash, uint32_t addr, uint8_t *buf, size_t len) {
    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_READ);
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 16));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 8));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr));
    spi_read(&flash->spi, buf, len);
    flash_cs_deassert(flash);
    return SPI_FLASH_OK;
}

int spi_flash_sector_erase(spi_flash_t *flash, uint32_t addr) {
    // Write enable.
    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_WRITE_ENABLE);
    flash_cs_deassert(flash);

    // Sector erase command.
    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_SECTOR_ERASE);
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 16));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 8));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr));
    flash_cs_deassert(flash);

    return spi_flash_wait_ready(flash);
}

int spi_flash_page_program(spi_flash_t *flash, uint32_t addr,
                           const uint8_t *buf, size_t len) {
    // Write enable.
    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_WRITE_ENABLE);
    flash_cs_deassert(flash);

    flash_cs_assert(flash);
    spi_transfer_byte(&flash->spi, SPI_FLASH_CMD_PAGE_PROGRAM);
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 16));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr >> 8));
    spi_transfer_byte(&flash->spi, (uint8_t)(addr));
    spi_write(&flash->spi, buf, len);
    flash_cs_deassert(flash);

    return spi_flash_wait_ready(flash);
}
