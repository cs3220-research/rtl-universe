// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// RvvFrontEnd: accepts dispatch from a scalar core and forwards it to the
// RVV backend (RvvCore). Provides a thin handshake layer so dispatch and
// completion can be back-pressured.
module RvvFrontEnd #(
    parameter int VLEN = 128,
    parameter int XLEN = 32
) (
    input  wire             clk,
    input  wire             rst_n,

    // Scalar dispatch.
    input  wire             dispatch_valid,
    output wire             dispatch_ready,
    input  wire [31:0]      dispatch_inst,
    input  wire [XLEN-1:0]  dispatch_rs1,
    input  wire [XLEN-1:0]  dispatch_rs2,

    // Completion.
    output wire             complete_valid,
    output wire [XLEN-1:0]  complete_data,
    output wire             complete_is_vsetvl,

    // Memory port (passthrough to RvvCore).
    output wire             mem_req,
    output wire             mem_we,
    output wire [XLEN-1:0]  mem_addr,
    output wire [VLEN-1:0]  mem_wdata,
    output wire [VLEN/8-1:0] mem_wmask,
    input  wire             mem_gnt,
    input  wire             mem_rvalid,
    input  wire [VLEN-1:0]  mem_rdata
);

  wire core_ready;
  assign dispatch_ready = core_ready;

  RvvCore #(.VLEN(VLEN), .XLEN(XLEN)) u_core (
    .clk           (clk),
    .rst_n         (rst_n),
    .issue_valid   (dispatch_valid),
    .issue_ready   (core_ready),
    .issue_inst    (dispatch_inst),
    .issue_rs1     (dispatch_rs1),
    .issue_rs2     (dispatch_rs2),
    .wb_valid      (complete_valid),
    .wb_data       (complete_data),
    .wb_is_vsetvl  (complete_is_vsetvl),
    .mem_req       (mem_req),
    .mem_we        (mem_we),
    .mem_addr      (mem_addr),
    .mem_wdata     (mem_wdata),
    .mem_wmask     (mem_wmask),
    .mem_gnt       (mem_gnt),
    .mem_rvalid    (mem_rvalid),
    .mem_rdata     (mem_rdata)
  );

endmodule
