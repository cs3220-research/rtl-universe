// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Round-robin arbiter tree (simplified). Picks among NumIn req lines and
// outputs grant + arbitrated data.
module rr_arb_tree #(
    parameter int unsigned NumIn     = 4,
    parameter int unsigned DataWidth = 32,
    parameter type         DataType  = logic [DataWidth-1:0],
    parameter bit          AxiVldRdy = 1'b0,
    parameter bit          LockIn    = 1'b0,
    parameter bit          FairArb   = 1'b1,
    parameter int unsigned IdxWidth  = (NumIn > 1) ? $clog2(NumIn) : 1
) (
    input  wire                       clk_i,
    input  wire                       rst_ni,
    input  wire                       flush_i,
    input  wire [IdxWidth-1:0]        rr_i,
    input  wire [NumIn-1:0]           req_i,
    output reg  [NumIn-1:0]           gnt_o,
    input  wire [NumIn-1:0][DataWidth-1:0] data_i,
    output wire                       req_o,
    input  wire                       gnt_i,
    output reg  [DataWidth-1:0]       data_o,
    output reg  [IdxWidth-1:0]        idx_o
);
  (* unused *) wire _flush_unused = flush_i;
  (* unused *) wire _rri_unused   = |rr_i;

  reg [IdxWidth-1:0] ptr_q;
  integer i;

  always @(*) begin
    gnt_o  = '0;
    data_o = '0;
    idx_o  = '0;
    for (i = 0; i < NumIn; i = i + 1) begin
      if (gnt_o == '0) begin
        if (req_i[(ptr_q + i) % NumIn]) begin
          gnt_o[(ptr_q + i) % NumIn] = 1'b1;
          data_o = data_i[(ptr_q + i) % NumIn];
          idx_o  = (ptr_q + i) % NumIn;
        end
      end
    end
  end

  assign req_o = |req_i;

  always @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      ptr_q <= '0;
    end else if (req_o && gnt_i) begin
      ptr_q <= idx_o + 1'b1;
    end
  end
endmodule
