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

// dma_test: exercises the DMA engine and prints "PASS\n" on success.

#include <stdint.h>

#include "fpga/sw/dma.h"
#include "fpga/sw/uart.h"

// Small source and destination buffers in DTCM.
static volatile uint32_t src_buf[4] = {0x11223344U, 0xAABBCCDDU,
                                       0xDEADBEEFU, 0xCAFEBABEU};
static volatile uint32_t dst_buf[4] = {0U, 0U, 0U, 0U};

int main(void) {
    dma_t dma;
    dma_init(&dma, DMA_BASE);

    int rc = dma_memcpy(&dma, (void *)dst_buf, (const void *)src_buf,
                        sizeof(src_buf));
    if (rc != 0) {
        uart_puts("FAIL: dma_memcpy returned error\n");
        return 1;
    }

    // Verify the transfer.
    for (int i = 0; i < 4; i++) {
        if (dst_buf[i] != src_buf[i]) {
            uart_puts("FAIL: data mismatch\n");
            return 1;
        }
    }

    uart_puts("PASS\n");
    return 0;
}
