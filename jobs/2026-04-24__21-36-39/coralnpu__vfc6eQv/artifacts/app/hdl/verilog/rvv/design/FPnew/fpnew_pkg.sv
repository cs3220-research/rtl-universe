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

// FPnew package: floating-point type definitions
package fpnew_pkg;

  // Floating-point formats
  typedef enum logic [2:0] {
    FP32    = 3'd0,
    FP64    = 3'd1,
    FP16    = 3'd2,
    FP8     = 3'd3,
    FP16ALT = 3'd4
  } fp_format_e;

  // Integer formats
  typedef enum logic [1:0] {
    INT8  = 2'd0,
    INT16 = 2'd1,
    INT32 = 2'd2,
    INT64 = 2'd3
  } int_format_e;

  // Operations
  typedef enum logic [3:0] {
    FMADD   = 4'd0,
    FNMSUB  = 4'd1,
    ADD     = 4'd2,
    MUL     = 4'd3,
    DIV     = 4'd4,
    SQRT    = 4'd5,
    SGNJ    = 4'd6,
    MINMAX  = 4'd7,
    CMP     = 4'd8,
    CLASSIFY = 4'd9,
    F2F     = 4'd10,
    F2I     = 4'd11,
    I2F     = 4'd12,
    CPKAB   = 4'd13,
    CPKCD   = 4'd14
  } operation_e;

  // Rounding modes
  typedef enum logic [2:0] {
    RNE = 3'd0,
    RTZ = 3'd1,
    RDN = 3'd2,
    RUP = 3'd3,
    RMM = 3'd4,
    ROD = 3'd6,
    DYN = 3'd7
  } roundmode_e;

  // Status flags (IEEE 754)
  typedef struct packed {
    logic NV;  // Invalid operation
    logic DZ;  // Divide by zero
    logic OF;  // Overflow
    logic UF;  // Underflow
    logic NX;  // Inexact
  } status_t;

  // Format dimensions
  function automatic int unsigned fp_width(fp_format_e fmt);
    case (fmt)
      FP32:    return 32;
      FP64:    return 64;
      FP16:    return 16;
      FP8:     return 8;
      FP16ALT: return 16;
      default: return 32;
    endcase
  endfunction

  function automatic int unsigned exp_bits(fp_format_e fmt);
    case (fmt)
      FP32:    return 8;
      FP64:    return 11;
      FP16:    return 5;
      FP8:     return 4;
      FP16ALT: return 8;
      default: return 8;
    endcase
  endfunction

  function automatic int unsigned man_bits(fp_format_e fmt);
    case (fmt)
      FP32:    return 23;
      FP64:    return 52;
      FP16:    return 10;
      FP8:     return 3;
      FP16ALT: return 7;
      default: return 23;
    endcase
  endfunction

  // Pipeline configuration
  typedef struct packed {
    int unsigned   NumPipeRegs;
    logic          PipeConfig;
  } pipe_config_t;

endpackage
