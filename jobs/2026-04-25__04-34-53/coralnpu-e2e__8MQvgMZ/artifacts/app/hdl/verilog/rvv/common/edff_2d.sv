// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// 2-D enable D flip-flop array. Each row has its own enable.
module edff_2d #(
    parameter int W = 32,
    parameter int N = 4
) (
    input  wire                   clk,
    input  wire                   rst_n,
    input  wire [N-1:0]           en,
    input  wire [N-1:0][W-1:0]    d,
    output reg  [N-1:0][W-1:0]    q
);
  genvar i;
  generate
    for (i = 0; i < N; i = i + 1) begin : g_row
      always @(posedge clk or negedge rst_n) begin
        if (!rst_n)     q[i] <= '0;
        else if (en[i]) q[i] <= d[i];
      end
    end
  endgenerate
endmodule
