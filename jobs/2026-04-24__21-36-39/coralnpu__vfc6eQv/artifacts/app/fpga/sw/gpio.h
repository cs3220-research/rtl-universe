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

#ifndef FPGA_SW_GPIO_H_
#define FPGA_SW_GPIO_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// GPIO peripheral base address.
#define GPIO_BASE 0x40020000U

// GPIO CSR offsets.
#define GPIO_DATA_IN_OFFSET   0x00U
#define GPIO_DATA_OUT_OFFSET  0x04U
#define GPIO_OUT_EN_OFFSET    0x08U

// Configure pin direction: 1 = output, 0 = input.
void gpio_set_direction(uint32_t base, uint32_t pin_mask, uint32_t dir_mask);

// Write output value for the given pins.
void gpio_write(uint32_t base, uint32_t pin_mask, uint32_t value);

// Read the current input values.
uint32_t gpio_read(uint32_t base);

// Set a single pin high (1) or low (0).
void gpio_set_pin(uint32_t base, uint32_t pin, uint32_t value);

#ifdef __cplusplus
}
#endif

#endif  // FPGA_SW_GPIO_H_
