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

`ifndef RVV_BACKEND_DEFINE_SVH
`define RVV_BACKEND_DEFINE_SVH

// ============================================================
// RVV Backend General Defines
// ============================================================

// Instruction encoding fields
`define OPCODE_V        7'h57   // Vector opcode
`define FUNC3_OPIVV     3'b000  // Integer vector-vector
`define FUNC3_OPIVX     3'b100  // Integer vector-scalar
`define FUNC3_OPIVI     3'b011  // Integer vector-immediate
`define FUNC3_OPMVV     3'b010  // Integer mask/widening vector-vector
`define FUNC3_OPMVX     3'b110  // Integer mask/widening vector-scalar
`define FUNC3_OPFVV     3'b001  // Float vector-vector
`define FUNC3_OPFVF     3'b101  // Float vector-scalar
`define FUNC3_OPCFG     3'b111  // Config

// Response codes
`define RVV_RESP_OK     2'b00
`define RVV_RESP_ERR    2'b10

// Useful macros
`define MAX(a,b) ((a) > (b) ? (a) : (b))
`define MIN(a,b) ((a) < (b) ? (a) : (b))

// Enable TB support
`ifdef TB_SUPPORT
  `define TB_ONLY(x) x
`else
  `define TB_ONLY(x)
`endif

`endif // RVV_BACKEND_DEFINE_SVH
