// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Single-entry handshake (valid/ready) flop.
module handshake_ff #(
    parameter int W = 32
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
  reg           full;
  reg [W-1:0]   data;

  assign in_ready  = !full;
  assign out_valid = full;
  assign out_data  = data;

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      full <= 1'b0;
      data <= '0;
    end else begin
      if (in_valid && in_ready) begin
        data <= in_data;
        full <= 1'b1;
      end else if (out_valid && out_ready) begin
        full <= 1'b0;
      end
    end
  end
endmodule
