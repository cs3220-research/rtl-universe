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

`ifndef RVV_BACKEND_DIV_SVH
`define RVV_BACKEND_DIV_SVH

// ============================================================
// RVV Backend Division Defines
// ============================================================

// Division opcodes
`define DIV_OP_VDIVU    4'h0
`define DIV_OP_VDIV     4'h1
`define DIV_OP_VREMU    4'h2
`define DIV_OP_VREM     4'h3
`define DIV_OP_VFDIV    4'h4
`define DIV_OP_VFSQRT   4'h5
`define DIV_OP_VFREC7   4'h6
`define DIV_OP_VFRSQRT7 4'h7

// Division unit states
typedef enum logic [2:0] {
  DIV_IDLE    = 3'd0,
  DIV_BUSY    = 3'd1,
  DIV_DONE    = 3'd2
} rvv_div_state_e;

`endif // RVV_BACKEND_DIV_SVH
