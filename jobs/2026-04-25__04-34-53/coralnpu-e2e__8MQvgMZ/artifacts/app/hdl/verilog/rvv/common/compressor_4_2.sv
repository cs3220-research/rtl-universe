// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// 4:2 compressor implemented as two cascaded 3:2 stages.
module compressor_4_2 #(
    parameter int W = 32
) (
    input  wire [W-1:0] a,
    input  wire [W-1:0] b,
    input  wire [W-1:0] c,
    input  wire [W-1:0] d,
    input  wire [W-1:0] cin,
    output wire [W-1:0] sum,
    output wire [W-1:0] carry,
    output wire [W-1:0] cout
);
  wire [W-1:0] s1   = a ^ b ^ c;
  wire [W-1:0] c1   = (a & b) | (b & c) | (a & c);
  assign cout       = c1;
  assign sum        = s1 ^ d ^ cin;
  assign carry      = ((s1 & d) | (d & cin) | (s1 & cin)) << 1;
endmodule
