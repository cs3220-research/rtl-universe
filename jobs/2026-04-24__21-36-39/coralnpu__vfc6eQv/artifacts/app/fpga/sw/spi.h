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

#ifndef FPGA_SW_SPI_H_
#define FPGA_SW_SPI_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// SPI master base address.
#define SPI_BASE 0x40030000U

// SPI master CSR offsets (from SpiMaster.scala).
#define SPI_STATUS_OFFSET   0x00U
#define SPI_CONTROL_OFFSET  0x04U
#define SPI_TXDATA_OFFSET   0x08U
#define SPI_RXDATA_OFFSET   0x0CU
#define SPI_CSID_OFFSET     0x10U
#define SPI_CSMODE_OFFSET   0x14U

// SPI STATUS register bits.
#define SPI_STATUS_TX_FULL  (1U << 0)
#define SPI_STATUS_RX_VALID (1U << 1)
#define SPI_STATUS_BUSY     (1U << 2)

// SPI context / handle.
typedef struct {
    uint32_t base;    // Peripheral base address.
    uint32_t cs_id;   // Chip-select index.
} spi_t;

// Initialize an SPI handle.
void spi_init(spi_t *spi, uint32_t base, uint32_t cs_id);

// Assert chip-select and begin a transfer.
void spi_cs_assert(spi_t *spi);

// Deassert chip-select and end a transfer.
void spi_cs_deassert(spi_t *spi);

// Transmit and receive one byte. Returns received byte.
uint8_t spi_transfer_byte(spi_t *spi, uint8_t tx);

// Transmit a buffer (received bytes discarded).
void spi_write(spi_t *spi, const uint8_t *buf, size_t len);

// Receive a buffer (transmit 0xFF).
void spi_read(spi_t *spi, uint8_t *buf, size_t len);

// Full-duplex transfer.
void spi_transfer(spi_t *spi, const uint8_t *tx, uint8_t *rx, size_t len);

// Wait until the SPI peripheral is no longer busy.
void spi_wait_idle(spi_t *spi);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_SPI_H_
