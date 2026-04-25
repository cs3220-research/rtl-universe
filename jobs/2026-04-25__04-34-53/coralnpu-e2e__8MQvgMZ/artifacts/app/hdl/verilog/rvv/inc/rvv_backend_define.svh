// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// RVV backend opcode/encoding defines.

`ifndef RVV_BACKEND_DEFINE_SVH
`define RVV_BACKEND_DEFINE_SVH

// RVV major opcodes.
`define OPCODE_LOAD_FP    7'b0000111
`define OPCODE_STORE_FP   7'b0100111
`define OPCODE_VECTOR     7'b1010111

// funct3 within the vector opcode.
`define FUNCT3_OPIVV      3'b000
`define FUNCT3_OPFVV      3'b001
`define FUNCT3_OPMVV      3'b010
`define FUNCT3_OPIVI      3'b011
`define FUNCT3_OPIVX      3'b100
`define FUNCT3_OPFVF      3'b101
`define FUNCT3_OPMVX      3'b110
`define FUNCT3_OPCFG      3'b111

// SEW / LMUL encoded values.
`define SEW8              3'b000
`define SEW16             3'b001
`define SEW32             3'b010
`define SEW64             3'b011

`endif  // RVV_BACKEND_DEFINE_SVH
