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

`ifndef RVV_BACKEND_PMTRDT_SVH
`define RVV_BACKEND_PMTRDT_SVH

// ============================================================
// RVV Backend Permute/Reduce Defines
// ============================================================

// Permutation operation codes
`define PMTRDT_OP_VRGATHER     4'h0
`define PMTRDT_OP_VRGATHEREI16 4'h1
`define PMTRDT_OP_VSLIDEUP     4'h2
`define PMTRDT_OP_VSLIDEDOWN   4'h3
`define PMTRDT_OP_VCOMPRESS    4'h4

// Reduction operation codes
`define PMTRDT_OP_VREDSUM      4'h8
`define PMTRDT_OP_VREDAND      4'h9
`define PMTRDT_OP_VREDOR       4'hA
`define PMTRDT_OP_VREDXOR      4'hB
`define PMTRDT_OP_VREDMINU     4'hC
`define PMTRDT_OP_VREDMIN      4'hD
`define PMTRDT_OP_VREDMAXU     4'hE
`define PMTRDT_OP_VREDMAX      4'hF

// Permute/reduce type
typedef enum logic [1:0] {
  PMTRDT_PERM    = 2'd0,
  PMTRDT_REDUCE  = 2'd1,
  PMTRDT_MASK    = 2'd2
} rvv_pmtrdt_type_e;

`endif // RVV_BACKEND_PMTRDT_SVH
