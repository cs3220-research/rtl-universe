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

// Display HAL: implements DEV_Config.h callbacks required by the waveshare
// LCD driver.  Hardware: SPI master + GPIO for DC/RST pins.

#include <stddef.h>
#include <stdint.h>

#include "DEV_Config.h"
#include "fpga/sw/gpio.h"
#include "fpga/sw/spi.h"

// Display context passed as `void *ctx` to LCD_Driver functions.
typedef struct {
    spi_t    spi;
    uint32_t gpio_base;
} display_ctx_t;

// Default display context (statically allocated for bare-metal use).
static display_ctx_t g_display_ctx;

// Returns a pointer to the default display context (for callers that need it).
void *display_hal_get_ctx(void) {
    return &g_display_ctx;
}

// Initialize the display HAL.
void display_hal_init(uint32_t spi_base, uint32_t gpio_base) {
    spi_init(&g_display_ctx.spi, spi_base, 0U);
    g_display_ctx.gpio_base = gpio_base;

    // Configure DC and RST pins as outputs.
    gpio_set_direction(gpio_base,
                       (1U << DEV_DC_PIN) | (1U << DEV_RST_PIN),
                       (1U << DEV_DC_PIN) | (1U << DEV_RST_PIN));
    // Deassert reset, set DC high (data mode default).
    gpio_set_pin(gpio_base, DEV_RST_PIN, 1U);
    gpio_set_pin(gpio_base, DEV_DC_PIN,  1U);
}

// ---------------------------------------------------------------------------
// DEV_Config.h callbacks
// ---------------------------------------------------------------------------

void DEV_Digital_Write(void *ctx, UWORD pin, UBYTE value) {
    display_ctx_t *dc = (display_ctx_t *)ctx;
    gpio_set_pin(dc->gpio_base, (uint32_t)pin, (uint32_t)value);
}

void DEV_SPI_WRITE(void *ctx, UBYTE value) {
    display_ctx_t *dc = (display_ctx_t *)ctx;
    spi_transfer_byte(&dc->spi, value);
}

void DEV_SPI_BLOCK_WRITE(void *ctx, const uint8_t *buffer, size_t bytes) {
    display_ctx_t *dc = (display_ctx_t *)ctx;
    spi_write(&dc->spi, buffer, bytes);
}

void DEV_Delay_ms(void *ctx, UWORD ms) {
    // Busy-wait delay.  No timer peripheral needed.
    (void)ctx;
    // Approximate 50 MHz: 50000 cycles per ms.
    volatile uint32_t count = (uint32_t)ms * 50000U;
    while (count--) {
        // busy wait
    }
}
