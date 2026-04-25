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

#include "fpga/sw/spi.h"
#include "fpga/sw/clk.h"

static inline volatile uint32_t *spi_reg(uint32_t base, uint32_t offset) {
    return (volatile uint32_t *)(base + offset);
}

void spi_init(spi_t *spi, uint32_t base, uint32_t cs_id) {
    spi->base = base;
    spi->cs_id = cs_id;
    // Deassert CS (CSMODE=0 means auto / idle high).
    *spi_reg(base, SPI_CSID_OFFSET)   = cs_id;
    *spi_reg(base, SPI_CSMODE_OFFSET) = 0U;
}

void spi_wait_idle(spi_t *spi) {
    while (*spi_reg(spi->base, SPI_STATUS_OFFSET) & SPI_STATUS_BUSY) {
        // busy wait
    }
}

void spi_cs_assert(spi_t *spi) {
    *spi_reg(spi->base, SPI_CSID_OFFSET)   = spi->cs_id;
    *spi_reg(spi->base, SPI_CSMODE_OFFSET) = 1U;  // 1 = hold (assert CS)
}

void spi_cs_deassert(spi_t *spi) {
    spi_wait_idle(spi);
    *spi_reg(spi->base, SPI_CSMODE_OFFSET) = 0U;  // 0 = auto (deassert)
}

uint8_t spi_transfer_byte(spi_t *spi, uint8_t tx) {
    // Wait for TX FIFO space.
    while (*spi_reg(spi->base, SPI_STATUS_OFFSET) & SPI_STATUS_TX_FULL) {
        // busy wait
    }
    *spi_reg(spi->base, SPI_TXDATA_OFFSET) = (uint32_t)tx;
    // Wait for RX data valid.
    while (!(*spi_reg(spi->base, SPI_STATUS_OFFSET) & SPI_STATUS_RX_VALID)) {
        // busy wait
    }
    return (uint8_t)*spi_reg(spi->base, SPI_RXDATA_OFFSET);
}

void spi_write(spi_t *spi, const uint8_t *buf, size_t len) {
    for (size_t i = 0; i < len; i++) {
        spi_transfer_byte(spi, buf[i]);
    }
}

void spi_read(spi_t *spi, uint8_t *buf, size_t len) {
    for (size_t i = 0; i < len; i++) {
        buf[i] = spi_transfer_byte(spi, 0xFFU);
    }
}

void spi_transfer(spi_t *spi, const uint8_t *tx, uint8_t *rx, size_t len) {
    for (size_t i = 0; i < len; i++) {
        uint8_t received = spi_transfer_byte(spi, tx ? tx[i] : 0xFFU);
        if (rx) {
            rx[i] = received;
        }
    }
}
