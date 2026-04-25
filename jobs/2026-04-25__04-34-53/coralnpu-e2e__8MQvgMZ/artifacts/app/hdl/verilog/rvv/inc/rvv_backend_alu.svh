// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0

`ifndef RVV_BACKEND_ALU_SVH
`define RVV_BACKEND_ALU_SVH

`include "rvv_backend_config.svh"

// ALU op encoding (internal).
`define ALU_OP_ADD     5'd0
`define ALU_OP_SUB     5'd1
`define ALU_OP_AND     5'd2
`define ALU_OP_OR      5'd3
`define ALU_OP_XOR     5'd4
`define ALU_OP_SLL     5'd5
`define ALU_OP_SRL     5'd6
`define ALU_OP_SRA     5'd7
`define ALU_OP_MIN     5'd8
`define ALU_OP_MAX     5'd9
`define ALU_OP_MINU    5'd10
`define ALU_OP_MAXU    5'd11
`define ALU_OP_RSUB    5'd12
`define ALU_OP_MERGE   5'd13

`endif  // RVV_BACKEND_ALU_SVH
