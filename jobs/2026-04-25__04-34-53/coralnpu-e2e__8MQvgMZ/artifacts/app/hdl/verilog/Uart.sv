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

// Simple register-mapped UART model for simulation.
//
// Register map (byte offsets):
//   0x0  TX_DATA   - Write transmits a character (lower byte). Read returns
//                    last transmitted byte.
//   0x4  RX_DATA   - Read returns next received byte.
//   0x8  STATUS    - bit[0] = TX ready (always 1)
//                    bit[1] = RX valid (data available)
//
// This model implements a loopback so a TX write can be observed on the
// RX side after one cycle. tx_o exposes the most recently transmitted byte
// for testbench observation.
module Uart #(
    parameter int unsigned AddrWidth = 4
) (
    input  wire                  clk,
    input  wire                  rst_n,

    // Register access bus.
    input  wire                  req_i,
    input  wire                  we_i,
    input  wire [AddrWidth-1:0]  addr_i,
    input  wire [31:0]           tl_d_i,    // write data
    output reg  [31:0]           tl_d_o,    // read data
    output reg                   ack_o,

    // Optional external TX/RX byte interface.
    output reg  [7:0]            tx_byte_o,
    output reg                   tx_valid_o,
    input  wire [7:0]            rx_byte_i,
    input  wire                  rx_valid_i
);

  reg [7:0] tx_data_reg;
  reg [7:0] rx_data_reg;
  reg       rx_valid_reg;

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      tx_data_reg  <= 8'h0;
      rx_data_reg  <= 8'h0;
      rx_valid_reg <= 1'b0;
      tl_d_o       <= 32'h0;
      ack_o        <= 1'b0;
      tx_byte_o    <= 8'h0;
      tx_valid_o   <= 1'b0;
    end else begin
      ack_o      <= 1'b0;
      tx_valid_o <= 1'b0;

      // External RX ingestion.
      if (rx_valid_i) begin
        rx_data_reg  <= rx_byte_i;
        rx_valid_reg <= 1'b1;
      end

      if (req_i) begin
        ack_o <= 1'b1;
        case (addr_i[3:2])
          2'b00: begin // TX_DATA @ 0x0
            if (we_i) begin
              tx_data_reg <= tl_d_i[7:0];
              tx_byte_o   <= tl_d_i[7:0];
              tx_valid_o  <= 1'b1;
              // Loopback for sim convenience.
              rx_data_reg  <= tl_d_i[7:0];
              rx_valid_reg <= 1'b1;
            end else begin
              tl_d_o <= {24'h0, tx_data_reg};
            end
          end
          2'b01: begin // RX_DATA @ 0x4
            if (!we_i) begin
              tl_d_o       <= {24'h0, rx_data_reg};
              rx_valid_reg <= 1'b0;
            end
          end
          2'b10: begin // STATUS @ 0x8
            tl_d_o <= {30'h0, rx_valid_reg, 1'b1};
          end
          default: tl_d_o <= 32'h0;
        endcase
      end
    end
  end

endmodule
