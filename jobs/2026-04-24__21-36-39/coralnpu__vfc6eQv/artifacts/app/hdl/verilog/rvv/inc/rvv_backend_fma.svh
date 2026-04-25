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

`ifndef RVV_BACKEND_FMA_SVH
`define RVV_BACKEND_FMA_SVH

// ============================================================
// RVV Backend FMA Defines
// ============================================================

// FMA operation codes
`define FMA_OP_VFMACC   4'h0
`define FMA_OP_VFNMACC  4'h1
`define FMA_OP_VFMSAC   4'h2
`define FMA_OP_VFNMSAC  4'h3
`define FMA_OP_VFMADD   4'h4
`define FMA_OP_VFNMADD  4'h5
`define FMA_OP_VFMSUB   4'h6
`define FMA_OP_VFNMSUB  4'h7
`define FMA_OP_VFMUL    4'h8
`define FMA_OP_VFADD    4'h9
`define FMA_OP_VFSUB    4'hA
`define FMA_OP_VFRSUB   4'hB

// FMA rounding modes (matches IEEE 754)
typedef enum logic [2:0] {
  FMA_RNE = 3'd0,   // Round to nearest, ties to even
  FMA_RTZ = 3'd1,   // Round toward zero
  FMA_RDN = 3'd2,   // Round down
  FMA_RUP = 3'd3,   // Round up
  FMA_RMM = 3'd4,   // Round to nearest, ties to max magnitude
  FMA_DYN = 3'd7    // Dynamic rounding
} rvv_fma_rnd_e;

`endif // RVV_BACKEND_FMA_SVH
