// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

module chip_nexus (
  // System clock (differential)
  input  logic clk_p_i,
  input  logic clk_n_i,

  // Active-low system reset
  input  logic rst_ni,

  // JTAG
  input  logic tck_i,
  input  logic tms_i,
  input  logic td_i,
  output logic td_o,
  input  logic trst_ni,

  // SPI slave (firmware loading via host)
  input  logic spi_clk_i,
  input  logic spi_csb_i,
  input  logic spi_mosi_i,
  output logic spi_miso_o,

  // SPI master (general purpose / PMOD)
  output logic spim_sclk_o,
  output logic spim_mosi_o,
  input  logic spim_miso_i,
  output logic spim_csb_o,

  // SPI master flash
  output logic spim_flash_sclk_o,
  output logic spim_flash_mosi_o,
  input  logic spim_flash_miso_i,
  output logic spim_flash_csb_o,
  output logic spim_flash_rst_no,

  // UART
  output logic [1:0] uart_tx_o,
  input  logic [1:0] uart_rx_i,

  // GPIO (bidirectional)
  inout  logic [3:0] gpio,

  // I2C (bidirectional)
  inout  logic i2c_scl,
  inout  logic i2c_sda,

  // ISP DVP camera interface (individual 1-bit ports)
  input  logic ISP_DVP_PCLK,
  input  logic ISP_DVP_VSYNC,
  input  logic ISP_DVP_HSYNC,
  input  logic ISP_DVP_D0,
  input  logic ISP_DVP_D1,
  input  logic ISP_DVP_D2,
  input  logic ISP_DVP_D3,
  input  logic ISP_DVP_D4,
  input  logic ISP_DVP_D5,
  input  logic ISP_DVP_D6,
  input  logic ISP_DVP_D7,

  // Camera control
  input  logic CAM_INT,
  output logic CAM_TRIG,

  // Status LEDs / debug
  output logic io_fault,
  output logic io_halted,

  // DDR status outputs (driven by DDR controller IP)
  output logic ddr_cal_complete_o,
  output logic ddr_ui_clk,
  output logic ddr_ui_clk_sync_rst,
  output logic io_ddr_mem_axi_aw_ready,
  output logic io_ddr_mem_axi_ar_ready
);

  // Stub: tie all outputs to safe idle values
  assign td_o                    = 1'b0;
  assign spi_miso_o              = 1'b0;
  assign spim_sclk_o             = 1'b0;
  assign spim_mosi_o             = 1'b0;
  assign spim_csb_o              = 1'b1;
  assign spim_flash_sclk_o       = 1'b0;
  assign spim_flash_mosi_o       = 1'b0;
  assign spim_flash_csb_o        = 1'b1;
  assign spim_flash_rst_no       = 1'b1;
  assign uart_tx_o               = 2'b11;
  assign io_fault                = 1'b0;
  assign io_halted               = 1'b0;
  assign ddr_cal_complete_o      = 1'b0;
  assign ddr_ui_clk              = 1'b0;
  assign ddr_ui_clk_sync_rst     = 1'b0;
  assign io_ddr_mem_axi_aw_ready = 1'b0;
  assign io_ddr_mem_axi_ar_ready = 1'b0;
  assign CAM_TRIG                = 1'b0;

endmodule
