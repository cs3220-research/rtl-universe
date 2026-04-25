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

`ifndef RVV_BACKEND_DISPATCH_SVH
`define RVV_BACKEND_DISPATCH_SVH

// ============================================================
// RVV Backend Dispatch Stage Defines
// ============================================================

// Dispatch queue entry
typedef struct packed {
  logic        valid;
  logic [4:0]  rob_id;
  logic [7:0]  uop_code;
  logic [4:0]  vd;
  logic [4:0]  vs1;
  logic [4:0]  vs2;
  logic        vm;
  logic [1:0]  sew;
  logic [2:0]  lmul;
} rvv_dispatch_entry_t;

// Bypass source
typedef enum logic [1:0] {
  BYPASS_NONE = 2'd0,
  BYPASS_ROB  = 2'd1,
  BYPASS_WB   = 2'd2,
  BYPASS_IMM  = 2'd3
} rvv_bypass_src_e;

// Operand ready status
typedef struct packed {
  logic ready;
  logic [4:0] rob_id;
  rvv_bypass_src_e src;
} rvv_opr_status_t;

`endif // RVV_BACKEND_DISPATCH_SVH
