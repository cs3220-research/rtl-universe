// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// RVV backend configuration parameters.

`ifndef RVV_BACKEND_CONFIG_SVH
`define RVV_BACKEND_CONFIG_SVH

// Vector engine configuration.
`define VLEN              128
`define ELEN              32
`define XLEN              32
`define VLENB             (`VLEN/8)

// Pipeline widths.
`define NUM_DP_UOP        2
`define NUM_RT_UOP        2
`define NUM_ALU           2
`define NUM_MUL           1
`define NUM_DIV           1
`define NUM_LSU           1
`define NUM_PMTRDT        1
`define NUM_FMA           1

// Register file.
`define NUM_VRF           32
`define NUM_VRF_BANK      4
`define VRF_BANK_DEPTH    (`NUM_VRF/`NUM_VRF_BANK)

`endif  // RVV_BACKEND_CONFIG_SVH
