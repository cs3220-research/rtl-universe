// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Multi-entry FIFO with valid/ready handshake on both sides.
module handshake_multi_fifo #(
    parameter int W     = 32,
    parameter int DEPTH = 4
) (
    input  wire           clk,
    input  wire           rst_n,
    input  wire           in_valid,
    output wire           in_ready,
    input  wire [W-1:0]   in_data,
    output wire           out_valid,
    input  wire           out_ready,
    output wire [W-1:0]   out_data
);
  localparam int AW = (DEPTH > 1) ? $clog2(DEPTH) : 1;

  reg [W-1:0] mem [0:DEPTH-1];
  reg [AW:0]  wptr, rptr;

  wire empty = (wptr == rptr);
  wire full  = (wptr[AW-1:0] == rptr[AW-1:0]) && (wptr[AW] != rptr[AW]);

  assign in_ready  = !full;
  assign out_valid = !empty;
  assign out_data  = mem[rptr[AW-1:0]];

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      wptr <= '0;
      rptr <= '0;
    end else begin
      if (in_valid && in_ready) begin
        mem[wptr[AW-1:0]] <= in_data;
        wptr <= wptr + 1'b1;
      end
      if (out_valid && out_ready) begin
        rptr <= rptr + 1'b1;
      end
    end
  end
endmodule
