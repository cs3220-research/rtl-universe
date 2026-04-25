// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Clock-enable D flip-flop with async active-low reset.
module cdffr #(
    parameter int W = 1
) (
    input  wire         clk,
    input  wire         rst_n,
    input  wire         en,
    input  wire [W-1:0] d,
    output reg  [W-1:0] q
);
  always @(posedge clk or negedge rst_n) begin
    if (!rst_n)      q <= '0;
    else if (en)     q <= d;
  end
endmodule
