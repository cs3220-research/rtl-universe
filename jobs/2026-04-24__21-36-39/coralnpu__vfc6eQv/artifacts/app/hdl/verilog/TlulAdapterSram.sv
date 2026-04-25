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

// TileLink-UL to SRAM adapter (used in FPGA flows)
module TlulAdapterSram #(
  parameter int unsigned ADDR_WIDTH  = 10,
  parameter int unsigned DATA_WIDTH  = 32,
  parameter int unsigned OUTSTANDING = 1
) (
  input  logic                    clk_i,
  input  logic                    rst_ni,

  // TL-UL interface (device side)
  input  logic                    tl_a_valid_i,
  output logic                    tl_a_ready_o,
  input  logic [2:0]              tl_a_opcode_i,
  input  logic [2:0]              tl_a_param_i,
  input  logic [2:0]              tl_a_size_i,
  input  logic [ADDR_WIDTH-1:0]   tl_a_address_i,
  input  logic [DATA_WIDTH/8-1:0] tl_a_mask_i,
  input  logic [DATA_WIDTH-1:0]   tl_a_data_i,

  output logic                    tl_d_valid_o,
  input  logic                    tl_d_ready_i,
  output logic [2:0]              tl_d_opcode_o,
  output logic [1:0]              tl_d_param_o,
  output logic [2:0]              tl_d_size_o,
  output logic [DATA_WIDTH-1:0]   tl_d_data_o,
  output logic [1:0]              tl_d_error_o,

  // SRAM interface
  output logic                    sram_req_o,
  output logic                    sram_we_o,
  output logic [ADDR_WIDTH-1:0]   sram_addr_o,
  output logic [DATA_WIDTH-1:0]   sram_wdata_o,
  output logic [DATA_WIDTH/8-1:0] sram_wmask_o,
  input  logic [DATA_WIDTH-1:0]   sram_rdata_i
);

  // Simple 1-cycle passthrough adapter
  logic pending_d;
  logic [2:0] pending_size;

  assign tl_a_ready_o  = !pending_d;
  assign sram_req_o    = tl_a_valid_i && !pending_d;
  assign sram_we_o     = (tl_a_opcode_i == 3'h0) ? 1'b0 :
                         (tl_a_opcode_i == 3'h1 || tl_a_opcode_i == 3'h4) ? 1'b1 : 1'b0;
  assign sram_addr_o   = tl_a_address_i;
  assign sram_wdata_o  = tl_a_data_i;
  assign sram_wmask_o  = tl_a_mask_i;

  assign tl_d_valid_o  = pending_d;
  assign tl_d_opcode_o = 3'h1;  // AccessAckData
  assign tl_d_param_o  = 2'h0;
  assign tl_d_size_o   = pending_size;
  assign tl_d_data_o   = sram_rdata_i;
  assign tl_d_error_o  = 2'h0;

  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      pending_d    <= 1'b0;
      pending_size <= '0;
    end else begin
      if (sram_req_o) begin
        pending_d    <= 1'b1;
        pending_size <= tl_a_size_i;
      end else if (tl_d_valid_o && tl_d_ready_i) begin
        pending_d    <= 1'b0;
      end
    end
  end

endmodule
