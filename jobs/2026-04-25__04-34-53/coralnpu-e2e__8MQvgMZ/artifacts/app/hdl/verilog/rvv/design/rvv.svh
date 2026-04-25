// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// RVV type definitions used by the front-end and core.

`ifndef RVV_SVH
`define RVV_SVH

`ifndef VLEN
`define VLEN 128
`endif

`ifndef XLEN
`define XLEN 32
`endif

// Standard scalar->vector dispatch packet.
typedef struct packed {
  logic            valid;
  logic [31:0]     inst;
  logic [`XLEN-1:0] rs1_data;
  logic [`XLEN-1:0] rs2_data;
  logic [4:0]       vd;
} rvv_dispatch_t;

typedef struct packed {
  logic             valid;
  logic [4:0]       vd;
  logic [`VLEN-1:0] result;
} rvv_writeback_t;

`endif  // RVV_SVH
