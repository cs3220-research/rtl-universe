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

#include "fpga/sw/gpio.h"

static inline volatile uint32_t *gpio_reg(uint32_t base, uint32_t offset) {
    return (volatile uint32_t *)(base + offset);
}

void gpio_set_direction(uint32_t base, uint32_t pin_mask, uint32_t dir_mask) {
    volatile uint32_t *oe = gpio_reg(base, GPIO_OUT_EN_OFFSET);
    uint32_t cur = *oe;
    *oe = (cur & ~pin_mask) | (dir_mask & pin_mask);
}

void gpio_write(uint32_t base, uint32_t pin_mask, uint32_t value) {
    volatile uint32_t *out = gpio_reg(base, GPIO_DATA_OUT_OFFSET);
    uint32_t cur = *out;
    *out = (cur & ~pin_mask) | (value & pin_mask);
}

uint32_t gpio_read(uint32_t base) {
    return *gpio_reg(base, GPIO_DATA_IN_OFFSET);
}

void gpio_set_pin(uint32_t base, uint32_t pin, uint32_t value) {
    uint32_t mask = 1U << pin;
    gpio_write(base, mask, value ? mask : 0U);
}
