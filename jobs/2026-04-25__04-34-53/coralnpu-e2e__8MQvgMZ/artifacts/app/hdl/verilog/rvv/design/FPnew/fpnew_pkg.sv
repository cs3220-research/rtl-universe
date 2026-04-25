// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Minimal FPnew package (subset of pulp-platform/fpnew).
package fpnew_pkg;

  typedef enum logic [2:0] {
    FP32    = 3'b000,
    FP64    = 3'b001,
    FP16    = 3'b010,
    FP8     = 3'b011,
    FP16ALT = 3'b100,
    FP8ALT  = 3'b101
  } fp_format_e;

  typedef enum logic [1:0] {
    INT8  = 2'b00,
    INT16 = 2'b01,
    INT32 = 2'b10,
    INT64 = 2'b11
  } int_format_e;

  typedef enum logic [3:0] {
    FMADD, FNMSUB, ADD, MUL,
    DIV, SQRT,
    SGNJ, MINMAX, CMP, CLASSIFY,
    F2F, F2I, I2F, CPKAB, CPKCD
  } operation_e;

  typedef enum logic [2:0] {
    RNE = 3'b000,
    RTZ = 3'b001,
    RDN = 3'b010,
    RUP = 3'b011,
    RMM = 3'b100,
    DYN = 3'b111
  } roundmode_e;

  typedef enum logic [2:0] {
    NEGINF, NEGNORM, NEGSUBNORM, NEGZERO,
    POSZERO, POSSUBNORM, POSNORM, POSINF
  } classmask_e;

  typedef struct packed {
    logic NV;
    logic DZ;
    logic OF;
    logic UF;
    logic NX;
  } status_t;

  typedef struct packed {
    logic is_normal;
    logic is_subnormal;
    logic is_zero;
    logic is_inf;
    logic is_nan;
    logic is_signalling;
    logic is_quiet;
    logic is_boxed;
  } fp_info_t;

  function automatic integer fp_width(input fp_format_e fmt);
    case (fmt)
      FP32:    fp_width = 32;
      FP64:    fp_width = 64;
      FP16:    fp_width = 16;
      FP8:     fp_width = 8;
      FP16ALT: fp_width = 16;
      FP8ALT:  fp_width = 8;
      default: fp_width = 32;
    endcase
  endfunction

  function automatic integer exp_bits(input fp_format_e fmt);
    case (fmt)
      FP32:    exp_bits = 8;
      FP64:    exp_bits = 11;
      FP16:    exp_bits = 5;
      FP16ALT: exp_bits = 8;
      FP8:     exp_bits = 5;
      FP8ALT:  exp_bits = 4;
      default: exp_bits = 8;
    endcase
  endfunction

  function automatic integer man_bits(input fp_format_e fmt);
    man_bits = fp_width(fmt) - exp_bits(fmt) - 1;
  endfunction

endpackage
