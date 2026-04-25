// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Simple round-robin arbiter (combinational priority + rotated mask).
module arb_round_robin #(
    parameter int N = 4
) (
    input  wire             clk,
    input  wire             rst_n,
    input  wire [N-1:0]     req,
    output reg  [N-1:0]     grant
);

  reg [$clog2(N>1?N:2)-1:0] ptr;

  integer i;
  always @(*) begin
    grant = '0;
    for (i = 0; i < N; i = i + 1) begin
      // Pick first set bit starting from ptr.
      if (grant == '0) begin
        if (req[(ptr + i) % N]) begin
          grant[(ptr + i) % N] = 1'b1;
        end
      end
    end
  end

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      ptr <= '0;
    end else begin
      // Advance ptr past the granted slot.
      for (i = 0; i < N; i = i + 1) begin
        if (grant[i]) ptr <= (i + 1) % N;
      end
    end
  end

endmodule
