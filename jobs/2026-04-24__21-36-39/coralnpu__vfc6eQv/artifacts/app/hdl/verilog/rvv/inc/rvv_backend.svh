// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

`ifndef RVV_BACKEND_SVH
`define RVV_BACKEND_SVH

`include "rvv_backend_config.svh"
`include "rvv_backend_define.svh"

// ============================================================
// RVV Backend top-level type definitions
// ============================================================

// Vector length configuration
`ifndef VLEN
  `define VLEN 128
`endif
`ifndef ELEN
  `define ELEN 32
`endif

// Number of vector register file entries
`define NR_REGS 32

// Micro-op types
typedef enum logic [2:0] {
  RVV_UOP_ALU    = 3'd0,
  RVV_UOP_MUL    = 3'd1,
  RVV_UOP_DIV    = 3'd2,
  RVV_UOP_LSU    = 3'd3,
  RVV_UOP_PMTRDT = 3'd4,
  RVV_UOP_FMA    = 3'd5,
  RVV_UOP_FDIV   = 3'd6,
  RVV_UOP_NOP    = 3'd7
} rvv_uop_type_e;

// Element width encoding
typedef enum logic [1:0] {
  EW8  = 2'd0,
  EW16 = 2'd1,
  EW32 = 2'd2,
  EW64 = 2'd3
} rvv_ew_e;

// Vector micro-op bundle
typedef struct packed {
  logic [4:0]        vd;
  logic [4:0]        vs1;
  logic [4:0]        vs2;
  logic [4:0]        vs3;
  logic              vm;
  rvv_uop_type_e     uop_type;
  logic [7:0]        uop_code;
  rvv_ew_e           sew;
  logic [2:0]        lmul;
  logic [`VLEN-1:0]  rs1_data;
  logic [`VLEN-1:0]  rs2_data;
  logic [4:0]        rob_id;
} rvv_uop_t;

`endif // RVV_BACKEND_SVH
