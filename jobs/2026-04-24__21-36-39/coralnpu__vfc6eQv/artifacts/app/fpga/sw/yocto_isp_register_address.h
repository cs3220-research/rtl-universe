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

#ifndef FPGA_SW_YOCTO_ISP_REGISTER_ADDRESS_H_
#define FPGA_SW_YOCTO_ISP_REGISTER_ADDRESS_H_

#include <stdint.h>

// Base address of the ISP (Yocto ISP) peripheral.
#define ISP_BASE 0x40060000U

// ISP register offsets (stub definitions).
#define ISP_CTRL_OFFSET          0x000U
#define ISP_STATUS_OFFSET        0x004U
#define ISP_FRAME_WIDTH_OFFSET   0x008U
#define ISP_FRAME_HEIGHT_OFFSET  0x00CU
#define ISP_INPUT_ADDR_OFFSET    0x010U
#define ISP_OUTPUT_ADDR_OFFSET   0x014U
#define ISP_GAIN_R_OFFSET        0x020U
#define ISP_GAIN_G_OFFSET        0x024U
#define ISP_GAIN_B_OFFSET        0x028U
#define ISP_GAMMA_OFFSET         0x030U

// ISP CTRL bits.
#define ISP_CTRL_ENABLE  (1U << 0)
#define ISP_CTRL_RESET   (1U << 1)
#define ISP_CTRL_START   (1U << 2)

// ISP STATUS bits.
#define ISP_STATUS_DONE  (1U << 0)
#define ISP_STATUS_BUSY  (1U << 1)

#endif  // FPGA_SW_YOCTO_ISP_REGISTER_ADDRESS_H_
