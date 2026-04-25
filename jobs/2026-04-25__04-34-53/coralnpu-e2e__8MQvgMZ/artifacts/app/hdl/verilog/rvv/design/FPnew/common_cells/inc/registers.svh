// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Common register macros (subset of pulp-platform/common_cells/registers.svh).

`ifndef COMMON_CELLS_REGISTERS_SVH
`define COMMON_CELLS_REGISTERS_SVH

// Always-FF (no reset).
`define FF(q, d, clk)                          \
  always_ff @(posedge clk) q <= d;

// FF with async active-low reset.
`define FFAR(q, d, rst_val, clk, rst_n)         \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n) q <= rst_val;                    \
    else        q <= d;                          \
  end

// FF with sync active-high reset.
`define FFSR(q, d, rst_val, clk, rst)           \
  always_ff @(posedge clk) begin                 \
    if (rst)    q <= rst_val;                    \
    else        q <= d;                          \
  end

// FF with enable + async reset.
`define FFLARNC(q, d, en, rst_val, clk, rst_n)  \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n) q <= rst_val;                    \
    else if (en) q <= d;                         \
  end

// FFL: FF with load enable, async low reset.
`define FFL(q, d, load, rst_val, clk, rst_n)    \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n) q <= rst_val;                    \
    else if (load) q <= d;                       \
  end

`endif
