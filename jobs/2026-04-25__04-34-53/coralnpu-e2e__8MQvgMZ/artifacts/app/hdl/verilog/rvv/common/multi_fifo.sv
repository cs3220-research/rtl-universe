// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Generic multi-entry FIFO (no handshake protocol; raw push/pop).
module multi_fifo #(
    parameter int W     = 32,
    parameter int DEPTH = 8
) (
    input  wire           clk,
    input  wire           rst_n,
    input  wire           push,
    input  wire [W-1:0]   din,
    input  wire           pop,
    output wire [W-1:0]   dout,
    output wire           empty,
    output wire           full,
    output wire [$clog2(DEPTH+1)-1:0] count
);
  localparam int AW = (DEPTH > 1) ? $clog2(DEPTH) : 1;

  reg [W-1:0] mem [0:DEPTH-1];
  reg [AW:0]  wptr, rptr;

  assign empty = (wptr == rptr);
  assign full  = (wptr[AW-1:0] == rptr[AW-1:0]) && (wptr[AW] != rptr[AW]);
  assign dout  = mem[rptr[AW-1:0]];
  assign count = wptr - rptr;

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      wptr <= '0;
      rptr <= '0;
    end else begin
      if (push && !full)  begin mem[wptr[AW-1:0]] <= din; wptr <= wptr + 1'b1; end
      if (pop  && !empty) rptr <= rptr + 1'b1;
    end
  end
endmodule
