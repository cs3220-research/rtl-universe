// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Leading zero counter (or trailing zero when MODE=1). Simple loop-based
// implementation suitable for simulation.
module lzc #(
    parameter int unsigned WIDTH = 8,
    parameter bit          MODE  = 1'b0   // 0: count leading; 1: count trailing
) (
    input  wire [WIDTH-1:0]                   in_i,
    output reg  [$clog2(WIDTH > 1 ? WIDTH : 2)-1:0] cnt_o,
    output reg                                empty_o
);
  integer i;
  always @(*) begin
    cnt_o   = '0;
    empty_o = 1'b1;
    if (MODE == 1'b0) begin
      // Count leading zeros (from MSB).
      for (i = WIDTH-1; i >= 0; i = i - 1) begin
        if (empty_o && in_i[i]) begin
          cnt_o   = (WIDTH-1-i);
          empty_o = 1'b0;
        end
      end
    end else begin
      // Count trailing zeros (from LSB).
      for (i = 0; i < WIDTH; i = i + 1) begin
        if (empty_o && in_i[i]) begin
          cnt_o   = i;
          empty_o = 1'b0;
        end
      end
    end
  end
endmodule
