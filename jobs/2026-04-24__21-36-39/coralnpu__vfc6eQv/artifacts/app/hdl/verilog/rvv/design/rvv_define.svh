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

`ifndef RVV_DEFINE_SVH
`define RVV_DEFINE_SVH

// ============================================================
// RVV general defines (design-level, used across design files)
// ============================================================

`ifndef VLEN
  `define VLEN 128
`endif

`ifndef VLEN_128
  `define VLEN_128
`endif

// Number of vector registers
`define NR_VEC_REGS     32
// Number of elements per register at EW=8
`define NR_ELEM_EW8     (`VLEN / 8)
// Number of elements per register at EW=16
`define NR_ELEM_EW16    (`VLEN / 16)
// Number of elements per register at EW=32
`define NR_ELEM_EW32    (`VLEN / 32)
// Number of elements per register at EW=64
`define NR_ELEM_EW64    (`VLEN / 64)

// Supported SEW values
`define SEW_8           2'b00
`define SEW_16          2'b01
`define SEW_32          2'b10
`define SEW_64          2'b11

`endif // RVV_DEFINE_SVH
