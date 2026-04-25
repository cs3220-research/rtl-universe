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

`ifndef RVV_BACKEND_CONFIG_SVH
`define RVV_BACKEND_CONFIG_SVH

// ============================================================
// RVV Backend Configuration Parameters
// ============================================================

// Vector length in bits
`define VLEN_CFG        128
// Maximum element length in bits
`define ELEN_CFG        32
// Number of ALU units
`define NUM_ALU_UNITS   4
// Number of MUL units
`define NUM_MUL_UNITS   2
// Number of DIV units
`define NUM_DIV_UNITS   1
// Number of LSU units
`define NUM_LSU_UNITS   2
// ROB depth
`define ROB_DEPTH       16
// Dispatch queue depth
`define DISPATCH_DEPTH  8
// VRF banks
`define VRF_BANKS       4
// VRF entries per bank
`define VRF_ENTRIES     8

// Derived parameters
`define VRF_ADDR_W      ($clog2(`VRF_ENTRIES))
`define ROB_ID_W        ($clog2(`ROB_DEPTH))

`endif // RVV_BACKEND_CONFIG_SVH
