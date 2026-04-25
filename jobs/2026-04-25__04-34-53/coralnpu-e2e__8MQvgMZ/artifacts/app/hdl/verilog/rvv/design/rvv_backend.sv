// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// rvv_backend: top-level backend stub.
module rvv_backend (
    input  wire        clk,
    input  wire        rst_n,
    input  wire        in_valid,
    output wire        in_ready,
    input  wire [31:0] in_inst,
    output wire        out_valid,
    output wire [31:0] out_data
);
  assign in_ready  = 1'b1;
  assign out_valid = 1'b0;
  assign out_data  = 32'h0;
endmodule
