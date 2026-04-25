// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// 3:2 compressor (full adder chain).
module compressor_3_2 #(
    parameter int W = 32
) (
    input  wire [W-1:0] a,
    input  wire [W-1:0] b,
    input  wire [W-1:0] c,
    output wire [W-1:0] sum,
    output wire [W-1:0] carry
);
  assign sum   = a ^ b ^ c;
  assign carry = ((a & b) | (b & c) | (a & c)) << 1;
endmodule
