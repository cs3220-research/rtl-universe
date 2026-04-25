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

#include "fpga/sw/i2c.h"
#include "fpga/sw/clk.h"

// Minimal CSR offsets for the i2c_master IP (stub values).
#define I2C_CTRL_OFFSET    0x00U
#define I2C_STATUS_OFFSET  0x04U
#define I2C_DATA_OFFSET    0x08U
#define I2C_CMD_OFFSET     0x0CU

#define I2C_STATUS_BUSY    (1U << 0)
#define I2C_STATUS_ACK     (1U << 1)

static inline volatile uint32_t *i2c_reg(uint32_t base, uint32_t offset) {
    return (volatile uint32_t *)(base + offset);
}

void i2c_init(i2c_t *i2c, uint32_t base) {
    i2c->base = base;
    // Enable controller.
    *i2c_reg(base, I2C_CTRL_OFFSET) = 1U;
}

static void i2c_wait_idle(i2c_t *i2c) {
    while (*i2c_reg(i2c->base, I2C_STATUS_OFFSET) & I2C_STATUS_BUSY) {
        // busy wait
    }
}

int i2c_write(i2c_t *i2c, uint8_t dev_addr, const uint8_t *data, size_t len) {
    (void)dev_addr;
    for (size_t i = 0; i < len; i++) {
        i2c_wait_idle(i2c);
        *i2c_reg(i2c->base, I2C_DATA_OFFSET) = (uint32_t)data[i];
        *i2c_reg(i2c->base, I2C_CMD_OFFSET)  = 1U;  // write command
        i2c_wait_idle(i2c);
        if (!(*i2c_reg(i2c->base, I2C_STATUS_OFFSET) & I2C_STATUS_ACK)) {
            return I2C_ERROR;
        }
    }
    return I2C_OK;
}

int i2c_read(i2c_t *i2c, uint8_t dev_addr, uint8_t *data, size_t len) {
    (void)dev_addr;
    for (size_t i = 0; i < len; i++) {
        i2c_wait_idle(i2c);
        *i2c_reg(i2c->base, I2C_CMD_OFFSET) = 2U;  // read command
        i2c_wait_idle(i2c);
        data[i] = (uint8_t)*i2c_reg(i2c->base, I2C_DATA_OFFSET);
    }
    return I2C_OK;
}

int i2c_write_read(i2c_t *i2c, uint8_t dev_addr,
                   const uint8_t *wr, size_t wr_len,
                   uint8_t *rd, size_t rd_len) {
    int ret = i2c_write(i2c, dev_addr, wr, wr_len);
    if (ret != I2C_OK) return ret;
    return i2c_read(i2c, dev_addr, rd, rd_len);
}
