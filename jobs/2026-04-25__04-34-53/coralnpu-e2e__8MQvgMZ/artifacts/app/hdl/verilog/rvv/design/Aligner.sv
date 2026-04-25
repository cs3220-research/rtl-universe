// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Aligner: compacts a vector of valid bits to the bottom positions, while
// preserving the original ordering of valid entries.
module Aligner #(
    parameter int N = 4,
    parameter int W = 32
) (
    input  wire [N-1:0]            in_valid,
    input  wire [N-1:0][W-1:0]     in_data,
    output reg  [N-1:0]            out_valid,
    output reg  [N-1:0][W-1:0]     out_data,
    output reg  [$clog2(N+1)-1:0]  out_count
);
  integer i, j;
  always @(*) begin
    out_valid = '0;
    out_data  = '{default:'0};
    j = 0;
    for (i = 0; i < N; i = i + 1) begin
      if (in_valid[i]) begin
        out_valid[j] = 1'b1;
        out_data[j]  = in_data[i];
        j = j + 1;
      end
    end
    out_count = j[$clog2(N+1)-1:0];
  end
endmodule
