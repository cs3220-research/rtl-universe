// Copyright 2024 Google LLC
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

// UART peripheral with TL-UL interface
// Register map:
//   0x00: TX data (write)
//   0x04: RX data (read)
//   0x08: Status  (read): [0]=tx_ready, [1]=rx_valid
//   0x0C: Control (r/w): [0]=tx_en, [1]=rx_en
//   0x10: Baud divisor (r/w)
module Uart #(
  parameter int unsigned ADDR_WIDTH = 8,
  parameter int unsigned DATA_WIDTH = 32
) (
  input  logic                    clk_i,
  input  logic                    rst_ni,

  // TL-UL interface
  input  logic                    tl_a_valid_i,
  output logic                    tl_a_ready_o,
  input  logic [2:0]              tl_a_opcode_i,
  input  logic [ADDR_WIDTH-1:0]   tl_a_address_i,
  input  logic [DATA_WIDTH/8-1:0] tl_a_mask_i,
  input  logic [DATA_WIDTH-1:0]   tl_a_data_i,

  output logic                    tl_d_valid_o,
  input  logic                    tl_d_ready_i,
  output logic [DATA_WIDTH-1:0]   tl_d_data_o,
  output logic [1:0]              tl_d_error_o,

  // UART pins
  output logic                    uart_tx_o,
  input  logic                    uart_rx_i,

  // Interrupt
  output logic                    irq_o
);

  // Internal registers
  logic [DATA_WIDTH-1:0] ctrl_reg;
  logic [DATA_WIDTH-1:0] baud_reg;
  logic [7:0] tx_data_reg;
  logic [7:0] rx_data_reg;
  logic tx_ready;
  logic rx_valid;

  // TX shift register
  logic [9:0] tx_shift;
  logic [3:0] tx_bit_cnt;
  logic [15:0] tx_baud_cnt;

  // RX shift register
  logic [8:0] rx_shift;
  logic [3:0] rx_bit_cnt;
  logic [15:0] rx_baud_cnt;
  logic rx_start;

  assign tl_a_ready_o = 1'b1;
  assign irq_o = rx_valid;

  // Register read
  always_comb begin
    tl_d_data_o  = '0;
    tl_d_error_o = 2'h0;
    case (tl_a_address_i[4:0])
      5'h04: tl_d_data_o = {24'h0, rx_data_reg};
      5'h08: tl_d_data_o = {30'h0, rx_valid, tx_ready};
      5'h0C: tl_d_data_o = ctrl_reg;
      5'h10: tl_d_data_o = baud_reg;
      default: tl_d_data_o = '0;
    endcase
  end

  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      tl_d_valid_o <= 1'b0;
      ctrl_reg     <= '0;
      baud_reg     <= 32'd868; // ~115200 baud at 100MHz
      tx_ready     <= 1'b1;
      rx_valid     <= 1'b0;
      tx_shift     <= '1;
      tx_bit_cnt   <= '0;
      tx_baud_cnt  <= '0;
      rx_shift     <= '0;
      rx_bit_cnt   <= '0;
      rx_baud_cnt  <= '0;
      rx_start     <= 1'b0;
      uart_tx_o    <= 1'b1;
    end else begin
      tl_d_valid_o <= tl_a_valid_i;

      // Register write
      if (tl_a_valid_i && (tl_a_opcode_i == 3'h1 || tl_a_opcode_i == 3'h4)) begin
        case (tl_a_address_i[4:0])
          5'h00: begin
            tx_data_reg <= tl_a_data_i[7:0];
            tx_ready    <= 1'b0;
            tx_shift    <= {1'b1, tl_a_data_i[7:0], 1'b0}; // stop, data, start
            tx_bit_cnt  <= 4'd10;
            tx_baud_cnt <= '0;
          end
          5'h0C: ctrl_reg <= tl_a_data_i;
          5'h10: baud_reg <= tl_a_data_i;
          default: ;
        endcase
      end

      // TX state machine
      if (tx_bit_cnt != '0) begin
        if (tx_baud_cnt >= baud_reg[15:0]) begin
          tx_baud_cnt <= '0;
          uart_tx_o   <= tx_shift[0];
          tx_shift    <= {1'b1, tx_shift[9:1]};
          tx_bit_cnt  <= tx_bit_cnt - 1'b1;
          if (tx_bit_cnt == 4'd1) tx_ready <= 1'b1;
        end else begin
          tx_baud_cnt <= tx_baud_cnt + 1'b1;
        end
      end

      // RX state machine (simple: detect start bit and shift in data)
      if (!rx_start && !uart_rx_i) begin
        rx_start    <= 1'b1;
        rx_baud_cnt <= baud_reg[15:1]; // half-bit delay for center sampling
        rx_bit_cnt  <= 4'd8;
      end else if (rx_start) begin
        if (rx_baud_cnt >= baud_reg[15:0]) begin
          rx_baud_cnt <= '0;
          if (rx_bit_cnt != '0) begin
            rx_shift   <= {uart_rx_i, rx_shift[8:1]};
            rx_bit_cnt <= rx_bit_cnt - 1'b1;
          end else begin
            rx_data_reg <= rx_shift[8:1];
            rx_valid    <= 1'b1;
            rx_start    <= 1'b0;
          end
        end else begin
          rx_baud_cnt <= rx_baud_cnt + 1'b1;
        end
      end

      // Clear rx_valid on read
      if (tl_a_valid_i && (tl_a_opcode_i == 3'h4) && tl_a_address_i[4:0] == 5'h04) begin
        rx_valid <= 1'b0;
      end
    end
  end

endmodule
