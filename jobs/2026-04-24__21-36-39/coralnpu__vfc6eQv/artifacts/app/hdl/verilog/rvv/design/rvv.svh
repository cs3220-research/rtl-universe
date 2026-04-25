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

`ifndef RVV_SVH
`define RVV_SVH

`include "rvv_define.svh"

// ============================================================
// RVV top-level type definitions and interfaces
// ============================================================

// Vector configuration state (CSR values)
typedef struct packed {
  logic [1:0]  vsew;     // Selected element width
  logic [2:0]  vlmul;    // LMUL
  logic        vta;      // Tail agnostic
  logic        vma;      // Mask agnostic
  logic        vill;     // Illegal configuration
} vtype_t;

// Vector instruction encoding fields
typedef struct packed {
  logic [5:0]  func6;
  logic        vm;
  logic [4:0]  vs2;
  logic [4:0]  vs1_rs1;
  logic [2:0]  func3;
  logic [4:0]  vd_rd;
  logic [6:0]  opcode;
} venc_t;

// Writeback data
typedef struct packed {
  logic        valid;
  logic [4:0]  rd;
  logic [31:0] data;
  logic        is_vector;
  logic [`VLEN-1:0] vdata;
} rvv_wb_t;

`endif // RVV_SVH
