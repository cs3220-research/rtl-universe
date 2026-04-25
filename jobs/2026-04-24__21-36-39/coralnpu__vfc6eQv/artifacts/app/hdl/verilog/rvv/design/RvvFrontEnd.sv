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

// RVV frontend: accepts scalar-side instructions and dispatches to backend
module RvvFrontEnd #(
  parameter int VLEN = 128
) (
  input  logic        clk,
  input  logic        rst_n,
  // From scalar core
  input  logic        inst_valid_i,
  output logic        inst_ready_o,
  input  logic [31:0] inst_i,
  input  logic [31:0] rs1_data_i,
  input  logic [31:0] rs2_data_i,
  // CSR state
  input  logic [10:0] vtype_i,
  input  logic [6:0]  vl_i,
  input  logic [6:0]  vstart_i,
  // To backend
  output logic        uop_valid_o,
  input  logic        uop_ready_i,
  output logic [31:0] uop_inst_o,
  output logic [31:0] uop_rs1_o,
  output logic [31:0] uop_rs2_o,
  // Writeback to scalar
  output logic        wb_valid_o,
  output logic [4:0]  wb_rd_o,
  output logic [31:0] wb_data_o
);
  // Stub implementation
  assign inst_ready_o = 1'b0;
  assign uop_valid_o  = 1'b0;
  assign uop_inst_o   = '0;
  assign uop_rs1_o    = '0;
  assign uop_rs2_o    = '0;
  assign wb_valid_o   = 1'b0;
  assign wb_rd_o      = '0;
  assign wb_data_o    = '0;
endmodule
