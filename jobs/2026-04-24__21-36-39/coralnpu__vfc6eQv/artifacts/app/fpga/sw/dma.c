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

#include "fpga/sw/dma.h"

static inline volatile uint32_t *dma_reg(uint32_t base, uint32_t offset) {
    return (volatile uint32_t *)(base + offset);
}

void dma_init(dma_t *dma, uint32_t base) {
    dma->base = base;
    // Reset the DMA engine.
    *dma_reg(base, DMA_CTRL_OFFSET) = DMA_CTRL_RESET;
    *dma_reg(base, DMA_CTRL_OFFSET) = 0U;
}

void dma_start(dma_t *dma, const dma_desc_t *desc) {
    // Write descriptor address and kick off transfer.
    *dma_reg(dma->base, DMA_DESC_ADDR_OFFSET) = (uint32_t)(uintptr_t)desc;
    *dma_reg(dma->base, DMA_CTRL_OFFSET)      = DMA_CTRL_START;
}

int dma_wait(dma_t *dma) {
    while (*dma_reg(dma->base, DMA_STATUS_OFFSET) & DMA_STATUS_BUSY) {
        // busy wait
    }
    uint32_t status = *dma_reg(dma->base, DMA_STATUS_OFFSET);
    if (status & DMA_STATUS_ERROR) {
        return -1;
    }
    return 0;
}

int dma_memcpy(dma_t *dma, void *dst, const void *src, size_t len) {
    dma_desc_t desc;
    desc.src_addr = (uint32_t)(uintptr_t)src;
    desc.dst_addr = (uint32_t)(uintptr_t)dst;
    desc.length   = (uint32_t)len;
    desc.flags    = 0U;
    dma_start(dma, &desc);
    return dma_wait(dma);
}
