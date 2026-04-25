// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0

module barrel_shifter #(
    parameter int W = 32
) (
    input  wire [W-1:0]           in_data,
    input  wire [$clog2(W)-1:0]   shift_amt,
    input  wire                   left,    // 1=left, 0=right
    input  wire                   arith,   // arithmetic right shift
    output wire [W-1:0]           out_data
);
  wire signed [W-1:0] s_in = in_data;
  assign out_data = left  ? (in_data << shift_amt)
                  : arith ? (s_in    >>> shift_amt)
                          : (in_data >>  shift_amt);
endmodule
