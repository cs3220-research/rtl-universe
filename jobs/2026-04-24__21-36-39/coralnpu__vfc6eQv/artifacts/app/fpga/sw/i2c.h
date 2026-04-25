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

#ifndef FPGA_SW_I2C_H_
#define FPGA_SW_I2C_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// I2C master base address.
#define I2C_BASE 0x40040000U

// I2C result codes.
#define I2C_OK    0
#define I2C_ERROR (-1)

// I2C handle.
typedef struct {
    uint32_t base;
} i2c_t;

// Initialize I2C peripheral.
void i2c_init(i2c_t *i2c, uint32_t base);

// Write bytes to an I2C device.  Returns I2C_OK or I2C_ERROR.
int i2c_write(i2c_t *i2c, uint8_t dev_addr, const uint8_t *data, size_t len);

// Read bytes from an I2C device.  Returns I2C_OK or I2C_ERROR.
int i2c_read(i2c_t *i2c, uint8_t dev_addr, uint8_t *data, size_t len);

// Write a single register then read back bytes.
int i2c_write_read(i2c_t *i2c, uint8_t dev_addr,
                   const uint8_t *wr, size_t wr_len,
                   uint8_t *rd, size_t rd_len);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_I2C_H_
