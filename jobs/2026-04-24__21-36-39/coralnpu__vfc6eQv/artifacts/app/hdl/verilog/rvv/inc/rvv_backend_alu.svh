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

`ifndef RVV_BACKEND_ALU_SVH
`define RVV_BACKEND_ALU_SVH

// ============================================================
// RVV Backend ALU Operation Defines
// ============================================================

// ALU opcodes (func6 field of vector instructions)
`define ALU_OP_VADD     6'h00
`define ALU_OP_VSUB     6'h02
`define ALU_OP_VRSUB    6'h03
`define ALU_OP_VMINU    6'h04
`define ALU_OP_VMIN     6'h05
`define ALU_OP_VMAXU    6'h06
`define ALU_OP_VMAX     6'h07
`define ALU_OP_VAND     6'h09
`define ALU_OP_VOR      6'h0A
`define ALU_OP_VXOR     6'h0B
`define ALU_OP_VRGATHER 6'h0C
`define ALU_OP_VSLIDEUP 6'h0E
`define ALU_OP_VSLIDEDOWN 6'h0F
`define ALU_OP_VADC     6'h10
`define ALU_OP_VMADC    6'h11
`define ALU_OP_VSBC     6'h12
`define ALU_OP_VMSBC    6'h13
`define ALU_OP_VMERGE   6'h17
`define ALU_OP_VMV      6'h17
`define ALU_OP_VMSEQ    6'h18
`define ALU_OP_VMSNE    6'h19
`define ALU_OP_VMSLTU   6'h1A
`define ALU_OP_VMSLT    6'h1B
`define ALU_OP_VMSLEU   6'h1C
`define ALU_OP_VMSLE    6'h1D
`define ALU_OP_VMSGTU   6'h1E
`define ALU_OP_VMSGT    6'h1F
`define ALU_OP_VSADDU   6'h20
`define ALU_OP_VSADD    6'h21
`define ALU_OP_VSSUBU   6'h22
`define ALU_OP_VSSUB    6'h23
`define ALU_OP_VSLL     6'h25
`define ALU_OP_VSMUL    6'h27
`define ALU_OP_VSRL     6'h28
`define ALU_OP_VSRA     6'h29
`define ALU_OP_VSSRL    6'h2A
`define ALU_OP_VSSRA    6'h2B
`define ALU_OP_VNSRL    6'h2C
`define ALU_OP_VNSRA    6'h2D
`define ALU_OP_VNCLIPU  6'h2E
`define ALU_OP_VNCLIP   6'h2F

// ALU unit operation type
typedef enum logic [5:0] {
  ALU_ADDSUB   = 6'd0,
  ALU_SHIFT    = 6'd1,
  ALU_MASK     = 6'd2,
  ALU_COMPARE  = 6'd3,
  ALU_OTHER    = 6'd4
} rvv_alu_unit_op_e;

`endif // RVV_BACKEND_ALU_SVH
