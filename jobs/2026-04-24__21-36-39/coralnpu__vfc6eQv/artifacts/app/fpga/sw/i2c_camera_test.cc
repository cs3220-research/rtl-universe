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

// i2c_camera_test: probes a camera module via I2C.

#include <stdint.h>

#include "fpga/sw/i2c.h"
#include "fpga/sw/uart.h"

// Common camera I2C address (e.g. OV5640 = 0x3C).
#define CAMERA_I2C_ADDR 0x3CU

int main(void) {
    i2c_t i2c;
    i2c_init(&i2c, I2C_BASE);

    // Read the camera chip ID register (register 0x300A for OV5640).
    uint8_t reg_addr[2] = {0x30U, 0x0AU};
    uint8_t chip_id[2]  = {0U, 0U};

    int rc = i2c_write_read(&i2c, CAMERA_I2C_ADDR,
                            reg_addr, sizeof(reg_addr),
                            chip_id,  sizeof(chip_id));
    if (rc != I2C_OK) {
        uart_puts("I2C camera: read failed\n");
    } else {
        uart_puts("I2C camera: chip ID read OK\n");
        uart_put_hex32(((uint32_t)chip_id[0] << 8) | chip_id[1]);
        uart_putc('\n');
    }

    return 0;
}
