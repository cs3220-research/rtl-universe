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

// flash_tool firmware
//
// Protocol (matches flash_tool_lib.py):
//   1. Firmware initialises the flash, then writes FLASH_TOOL_READY_MAGIC to
//      the `ready` symbol.
//   2. Host writes a 16-byte flash_tool_cmd_t to the `cmd` symbol.
//   3. Firmware processes the command and writes a 16-byte flash_tool_resp_t
//      to the `resp` symbol (with resp.cmd == cmd.cmd on completion).
//   4. For CMD_GET_INFO the firmware also fills the `buffer` symbol.
//   5. For CMD_PROGRAM_DATA the host writes data to `buffer` first, then
//      sends the command; firmware verifies CRC and programs the flash.

#include <stddef.h>
#include <stdint.h>

#include "fpga/sw/flash_tool_status.h"
#include "fpga/sw/spi.h"
#include "fpga/sw/spi_flash.h"
#include "fpga/sw/uart.h"

// ---------------------------------------------------------------------------
// Shared memory symbols accessed by the host over TileLink / SPI bridge.
// Placed in DTCM with no-cache semantics (volatile).
// ---------------------------------------------------------------------------
volatile uint32_t       ready  __attribute__((used, section(".dtcm"))) = 0;
volatile flash_tool_cmd_t  cmd __attribute__((used, section(".dtcm")));
volatile flash_tool_resp_t resp __attribute__((used, section(".dtcm")));

// Transfer buffer: must be large enough for one sector (256 KB).
#define BUFFER_SIZE (256U * 1024U)
volatile uint8_t buffer[BUFFER_SIZE] __attribute__((used, section(".dtcm")));

// ---------------------------------------------------------------------------
// Minimal CRC-32 (IEEE 802.3, matches Python zlib.crc32).
// ---------------------------------------------------------------------------
static uint32_t crc32_update(uint32_t crc, const uint8_t *data, size_t len) {
    crc = ~crc;
    for (size_t i = 0; i < len; i++) {
        crc ^= data[i];
        for (int j = 0; j < 8; j++) {
            if (crc & 1U) {
                crc = (crc >> 1) ^ 0xEDB88320U;
            } else {
                crc >>= 1;
            }
        }
    }
    return ~crc;
}

// ---------------------------------------------------------------------------
// Command handlers
// ---------------------------------------------------------------------------

static void handle_hello(spi_flash_t *flash) {
    (void)flash;
    // Nothing to do — just echo the command back with OK status.
    resp.cmd      = FLASH_TOOL_CMD_HELLO;
    resp.capacity = 0U;
    resp.reserved = 0U;
    resp.status   = FLASH_TOOL_STATUS_OK;
}

static void handle_get_info(spi_flash_t *flash) {
    spi_flash_info_t info;
    int rc = spi_flash_read_id(flash, &info);
    if (rc != SPI_FLASH_OK) {
        resp.cmd      = FLASH_TOOL_CMD_GET_INFO;
        resp.capacity = 0U;
        resp.reserved = 0U;
        resp.status   = FLASH_TOOL_STATUS_DISCOVERY_FAILED;
        return;
    }

    // Write info into buffer (matches flash_tool_lib.py get_info parsing).
    volatile flash_tool_info_t *buf_info =
        (volatile flash_tool_info_t *)(uintptr_t)buffer;
    buf_info->capacity    = info.capacity;
    buf_info->sector_size = info.sector_size;
    buf_info->page_size   = info.page_size;
    buf_info->reserved    = 0U;

    resp.cmd      = FLASH_TOOL_CMD_GET_INFO;
    resp.capacity = info.capacity;
    resp.reserved = 0U;
    resp.status   = FLASH_TOOL_STATUS_OK;
}

static void handle_program_data(spi_flash_t *flash,
                                uint32_t addr, uint32_t length,
                                uint32_t expected_crc) {
    if (length == 0U || length > BUFFER_SIZE) {
        resp.cmd      = FLASH_TOOL_CMD_PROGRAM_DATA;
        resp.capacity = 0U;
        resp.reserved = 0U;
        resp.status   = FLASH_TOOL_STATUS_INVALID_LEN;
        return;
    }
    if (addr >= 16U * 1024U * 1024U) {
        resp.cmd      = FLASH_TOOL_CMD_PROGRAM_DATA;
        resp.capacity = 0U;
        resp.reserved = 0U;
        resp.status   = FLASH_TOOL_STATUS_ADDRESS_ERROR;
        return;
    }

    // Verify CRC of data already placed in buffer by the host.
    // Cast away volatile: safe for single-pass CRC scan of host-written data.
    uint32_t actual_crc = crc32_update(0U,
                                       (const uint8_t *)(uintptr_t)buffer,
                                       length);
    if (actual_crc != expected_crc) {
        resp.cmd      = FLASH_TOOL_CMD_PROGRAM_DATA;
        resp.capacity = 0U;
        resp.reserved = 0U;
        resp.status   = FLASH_TOOL_STATUS_CRC_MISMATCH;
        return;
    }

    // Erase and program sector(s).
    spi_flash_info_t info;
    spi_flash_read_id(flash, &info);
    uint32_t sector_size = info.sector_size ? info.sector_size : (256U * 1024U);
    uint32_t page_size   = info.page_size   ? info.page_size   : 256U;

    uint32_t written = 0U;
    uint32_t cur_addr = addr;

    while (written < length) {
        // Erase sector if at sector boundary.
        if ((cur_addr % sector_size) == 0U) {
            spi_flash_sector_erase(flash, cur_addr);
        }

        uint32_t chunk = length - written;
        if (chunk > page_size) chunk = page_size;

        // Cast away volatile: safe here because we do a single-pass read of the
        // buffer that was written by the host before this command was issued.
        spi_flash_page_program(flash, cur_addr,
                               (const uint8_t *)(uintptr_t)(buffer + written),
                               chunk);
        written  += chunk;
        cur_addr += chunk;
    }

    resp.cmd      = FLASH_TOOL_CMD_PROGRAM_DATA;
    resp.capacity = 0U;
    resp.reserved = 0U;
    resp.status   = FLASH_TOOL_STATUS_OK;
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------
int main(void) {
    uart_puts("flash_tool: init\n");

    spi_flash_t flash;
    spi_flash_init(&flash, SPI_BASE, GPIO_BASE, /*cs_pin=*/0U);

    // Signal readiness to the host.
    ready = FLASH_TOOL_READY_MAGIC;
    uart_puts("flash_tool: ready\n");

    // Command loop.
    while (1) {
        // Wait for a new command (cmd.cmd != 0 indicates pending work;
        // the host zeroes `resp` before writing `cmd`).
        uint32_t c = cmd.cmd;
        if (c == 0U) {
            continue;
        }

        uint32_t cmd_addr   = cmd.addr;
        uint32_t cmd_length = cmd.length;
        uint32_t cmd_crc    = cmd.crc32;

        // Acknowledge by clearing cmd so the host can re-use it.
        cmd.cmd = 0U;

        switch (c) {
            case FLASH_TOOL_CMD_HELLO:
                handle_hello(&flash);
                break;
            case FLASH_TOOL_CMD_GET_INFO:
                handle_get_info(&flash);
                break;
            case FLASH_TOOL_CMD_PROGRAM_DATA:
                handle_program_data(&flash, cmd_addr, cmd_length, cmd_crc);
                break;
            default:
                resp.cmd      = c;
                resp.capacity = 0U;
                resp.reserved = 0U;
                resp.status   = FLASH_TOOL_STATUS_INVALID_LEN;
                break;
        }
    }

    return 0;
}
