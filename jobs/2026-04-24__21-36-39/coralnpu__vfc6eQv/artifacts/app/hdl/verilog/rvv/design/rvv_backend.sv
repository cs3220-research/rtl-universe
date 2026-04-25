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

`include "rvv_backend.svh"

// RVV backend top-level: receives micro-ops from the frontend and
// dispatches them to execution units.
module rvv_backend #(
  parameter int VLEN = 128
) (
  input  logic        clk,
  input  logic        rst_n,
  // Micro-op input
  input  logic        uop_valid_i,
  output logic        uop_ready_o,
  input  logic [31:0] uop_inst_i,
  input  logic [31:0] uop_rs1_i,
  input  logic [31:0] uop_rs2_i,
  // Writeback outputs
  output logic        rd_valid_o,
  output logic [4:0]  rd_addr_o,
  output logic [31:0] rd_data_o,
  // Memory interface
  output logic         mem_valid_o,
  input  logic         mem_ready_i,
  output logic [31:0]  mem_addr_o,
  output logic         mem_write_o,
  output logic [127:0] mem_wdata_o,
  output logic [15:0]  mem_wmask_o,
  input  logic         mem_rvalid_i,
  input  logic [127:0] mem_rdata_i
);
  // Stub implementation
  assign uop_ready_o  = 1'b0;
  assign rd_valid_o   = 1'b0;
  assign rd_addr_o    = '0;
  assign rd_data_o    = '0;
  assign mem_valid_o  = 1'b0;
  assign mem_addr_o   = '0;
  assign mem_write_o  = 1'b0;
  assign mem_wdata_o  = '0;
  assign mem_wmask_o  = '0;
endmodule
