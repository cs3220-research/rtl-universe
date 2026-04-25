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

// isp_cam_test: exercises the ISP pipeline with a synthetic test pattern.

#include <stdint.h>

#include "fpga/sw/uart.h"
#include "fpga/sw/yocto_isp_register_address.h"

// Convenience accessor for a 32-bit MMIO register.
static inline volatile uint32_t *isp_reg(uint32_t offset) {
    return (volatile uint32_t *)(ISP_BASE + offset);
}

// Reset and enable the ISP.
static void isp_init(void) {
    *isp_reg(ISP_CTRL_OFFSET) = ISP_CTRL_RESET;
    *isp_reg(ISP_CTRL_OFFSET) = 0U;
    *isp_reg(ISP_CTRL_OFFSET) = ISP_CTRL_ENABLE;
}

// Configure frame dimensions.
static void isp_set_frame(uint32_t width, uint32_t height) {
    *isp_reg(ISP_FRAME_WIDTH_OFFSET)  = width;
    *isp_reg(ISP_FRAME_HEIGHT_OFFSET) = height;
}

// Set white-balance gains (1.0 = 256 in fixed-point 8.8).
static void isp_set_gains(uint32_t r, uint32_t g, uint32_t b) {
    *isp_reg(ISP_GAIN_R_OFFSET) = r;
    *isp_reg(ISP_GAIN_G_OFFSET) = g;
    *isp_reg(ISP_GAIN_B_OFFSET) = b;
}

// Start ISP processing and wait for completion.
static int isp_run(void) {
    *isp_reg(ISP_CTRL_OFFSET) |= ISP_CTRL_START;
    // Poll STATUS with a simple timeout counter.
    volatile uint32_t timeout = 1000000U;
    while (timeout--) {
        if (*isp_reg(ISP_STATUS_OFFSET) & ISP_STATUS_DONE) {
            return 0;
        }
    }
    return -1;  // timed out
}

int main(void) {
    isp_init();
    isp_set_frame(320U, 240U);
    isp_set_gains(256U, 256U, 256U);  // unity gains

    int rc = isp_run();
    if (rc != 0) {
        uart_puts("ISP test: timeout\n");
        return 1;
    }

    uart_puts("ISP test: done\n");
    return 0;
}
