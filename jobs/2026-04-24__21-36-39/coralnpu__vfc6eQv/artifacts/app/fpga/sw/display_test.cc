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

// display_test: exercises the display HAL and LCD driver stubs.

#include <stdint.h>

#include "DEV_Config.h"
#include "LCD_Driver.h"
#include "fpga/sw/gpio.h"
#include "fpga/sw/spi.h"

// Forward declaration from display_hal.c.
extern "C" void  display_hal_init(uint32_t spi_base, uint32_t gpio_base);
extern "C" void *display_hal_get_ctx(void);

int main(void) {
    display_hal_init(SPI_BASE, GPIO_BASE);
    void *ctx = display_hal_get_ctx();

    LCD_Init(ctx);
    LCD_Clear(ctx, WHITE);

    return 0;
}
