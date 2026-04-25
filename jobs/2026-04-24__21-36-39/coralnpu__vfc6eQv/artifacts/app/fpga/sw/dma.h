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

#ifndef FPGA_SW_DMA_H_
#define FPGA_SW_DMA_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// DMA engine base address.
#define DMA_BASE 0x40050000U

// DMA CSR offsets.
#define DMA_CTRL_OFFSET      0x00U
#define DMA_STATUS_OFFSET    0x04U
#define DMA_DESC_ADDR_OFFSET 0x08U

// DMA STATUS bits.
#define DMA_STATUS_DONE  (1U << 0)
#define DMA_STATUS_BUSY  (1U << 1)
#define DMA_STATUS_ERROR (1U << 2)

// DMA CTRL bits.
#define DMA_CTRL_START  (1U << 0)
#define DMA_CTRL_RESET  (1U << 1)

// DMA transfer descriptor.
typedef struct {
    uint32_t src_addr;
    uint32_t dst_addr;
    uint32_t length;   // in bytes
    uint32_t flags;    // reserved / optional
} dma_desc_t;

// DMA handle.
typedef struct {
    uint32_t base;
} dma_t;

// Initialize DMA handle.
void dma_init(dma_t *dma, uint32_t base);

// Start a transfer given a descriptor.  Returns immediately (non-blocking).
void dma_start(dma_t *dma, const dma_desc_t *desc);

// Poll until the transfer is complete (blocking).  Returns 0 on success.
int dma_wait(dma_t *dma);

// Convenience: synchronous memcpy via DMA.  Returns 0 on success.
int dma_memcpy(dma_t *dma, void *dst, const void *src, size_t len);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_DMA_H_
