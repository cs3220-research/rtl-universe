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

#ifndef FPGA_SW_FLASH_TOOL_STATUS_H_
#define FPGA_SW_FLASH_TOOL_STATUS_H_

#include <stdint.h>

// Magic value written to `ready` when the firmware is initialised and waiting
// for commands.  Must match FLASH_TOOL_READY_MAGIC in flash_tool_lib.py.
#define FLASH_TOOL_READY_MAGIC 0xFEEDFACEU

// Command IDs (must match flash_tool_lib.py).
#define FLASH_TOOL_CMD_GET_INFO      1U
#define FLASH_TOOL_CMD_PROGRAM_DATA  2U
#define FLASH_TOOL_CMD_HELLO         3U

// Status codes (must match flash_tool_lib.py).
#define FLASH_TOOL_STATUS_OK               0U
#define FLASH_TOOL_STATUS_DISCOVERY_FAILED 1U
#define FLASH_TOOL_STATUS_VERIFY_FAILED    2U
#define FLASH_TOOL_STATUS_CRC_MISMATCH     3U
#define FLASH_TOOL_STATUS_BOUNDARY_ERROR   4U
#define FLASH_TOOL_STATUS_BUFFER_TOO_SMALL 5U
#define FLASH_TOOL_STATUS_INVALID_LEN      6U
#define FLASH_TOOL_STATUS_NOT_INITIALIZED  7U
#define FLASH_TOOL_STATUS_ADDRESS_ERROR    8U

// Command packet layout (16 bytes, must match flash_tool_lib.py send_cmd).
typedef struct {
    uint32_t cmd;
    uint32_t addr;
    uint32_t length;
    uint32_t crc32;
} flash_tool_cmd_t;

// Response packet layout (16 bytes, must match flash_tool_lib.py).
typedef struct {
    uint32_t cmd;
    uint32_t capacity;
    uint32_t reserved;
    uint32_t status;
} flash_tool_resp_t;

// Flash info layout written to `buffer` for CMD_GET_INFO (matches lib.py).
typedef struct {
    uint32_t capacity;
    uint32_t sector_size;
    uint32_t page_size;
    uint32_t reserved;
} flash_tool_info_t;

#endif  // FPGA_SW_FLASH_TOOL_STATUS_H_
