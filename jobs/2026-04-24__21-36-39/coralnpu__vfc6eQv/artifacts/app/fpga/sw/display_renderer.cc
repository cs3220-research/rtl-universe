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

#include "fpga/sw/display_renderer.h"

#include <stddef.h>
#include <stdint.h>

#include "DEV_Config.h"
#include "LCD_Driver.h"
#include "fpga/sw/dma.h"
#include "fpga/sw/gpio.h"
#include "fpga/sw/spi.h"

// Forward declaration of display HAL helper (defined in display_hal.c).
extern "C" void  display_hal_init(uint32_t spi_base, uint32_t gpio_base);
extern "C" void *display_hal_get_ctx(void);

static dma_t  g_dma;
static PAINT  g_paint;

// Small scratch buffer used for DMA-backed clear operations.
#define SCRATCH_PIXELS 320U
static uint16_t g_scratch[SCRATCH_PIXELS];

void display_renderer_init(uint32_t spi_base, uint32_t gpio_base,
                           uint32_t dma_base) {
    display_hal_init(spi_base, gpio_base);
    dma_init(&g_dma, dma_base);

    void *ctx = display_hal_get_ctx();
    LCD_Init(ctx);

    Paint_NewImage(&g_paint, LCD_WIDTH, LCD_HEIGHT, ROTATE_0, WHITE);
}

void display_renderer_draw_frame(const uint16_t *frame,
                                 uint32_t width, uint32_t height) {
    void *ctx = display_hal_get_ctx();
    LCD_SetWindow(ctx, 0, 0, (UWORD)(width - 1U), (UWORD)(height - 1U));
    // Kick off DMA to push the frame buffer directly to the SPI FIFO base.
    // In a real driver this would use the DMA to stream to the SPI TX reg;
    // here we just DMA to the scratch area as a stub operation.
    dma_memcpy(&g_dma, g_scratch, frame,
               sizeof(g_scratch) < (width * 2U) ? sizeof(g_scratch)
                                                 : width * 2U);
    (void)height;
}

void display_renderer_fill(uint16_t color) {
    void *ctx = display_hal_get_ctx();
    LCD_Clear(ctx, (UWORD)color);
}

void display_renderer_draw_string(uint32_t x, uint32_t y, const char *str,
                                  uint16_t fg, uint16_t bg) {
    Paint_DrawString_EN(&g_paint, (UWORD)x, (UWORD)y, str, &Font16,
                        (UWORD)bg, (UWORD)fg);
    // Flush paint buffer to the LCD.
    void *ctx = display_hal_get_ctx();
    LCD_SetWindow(ctx, (UWORD)x, (UWORD)y,
                  (UWORD)(x + g_paint.Width  - 1U),
                  (UWORD)(y + g_paint.Height - 1U));
}
