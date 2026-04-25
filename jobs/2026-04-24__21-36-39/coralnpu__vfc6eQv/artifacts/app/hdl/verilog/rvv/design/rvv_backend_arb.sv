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

// RVV backend arbitration: arbitrates between execution unit results
module rvv_backend_arb #(
  parameter int NUM_UNITS = 4,
  parameter int DATA_W    = 32
) (
  input  logic                        clk,
  input  logic                        rst_n,
  input  logic [NUM_UNITS-1:0]        valid_i,
  output logic [NUM_UNITS-1:0]        ready_o,
  input  logic [NUM_UNITS*DATA_W-1:0] data_i,
  input  logic [NUM_UNITS*5-1:0]      rd_i,
  output logic                        valid_o,
  input  logic                        ready_i,
  output logic [DATA_W-1:0]           data_o,
  output logic [4:0]                  rd_o
);
  // Simple round-robin stub
  logic [NUM_UNITS-1:0] grant;
  logic [$clog2(NUM_UNITS)-1:0] sel;

  always_comb begin
    grant = '0;
    sel   = '0;
    for (int i = 0; i < NUM_UNITS; i++) begin
      if (valid_i[i]) begin
        grant[i] = 1'b1;
        sel      = i[$clog2(NUM_UNITS)-1:0];
        break;
      end
    end
  end

  assign valid_o  = |valid_i;
  assign data_o   = data_i[sel*DATA_W +: DATA_W];
  assign rd_o     = rd_i[sel*5 +: 5];
  assign ready_o  = {NUM_UNITS{ready_i}} & grant;
endmodule
