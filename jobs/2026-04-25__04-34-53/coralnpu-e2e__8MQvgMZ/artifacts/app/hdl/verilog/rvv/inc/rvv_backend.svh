// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Top-level RVV backend includes.

`ifndef RVV_BACKEND_SVH
`define RVV_BACKEND_SVH

`include "rvv_backend_config.svh"
`include "rvv_backend_define.svh"

// Generic uop type used across the backend pipeline.
typedef struct packed {
  logic                 valid;
  logic [31:0]          inst;
  logic [4:0]           rd;
  logic [4:0]           rs1;
  logic [4:0]           rs2;
  logic [`VLEN-1:0]     vs1;
  logic [`VLEN-1:0]     vs2;
  logic [`XLEN-1:0]     rs1_data;
  logic [2:0]           sew;
  logic [2:0]           lmul;
  logic [`XLEN-1:0]     vl;
} rvv_uop_t;

`endif  // RVV_BACKEND_SVH
