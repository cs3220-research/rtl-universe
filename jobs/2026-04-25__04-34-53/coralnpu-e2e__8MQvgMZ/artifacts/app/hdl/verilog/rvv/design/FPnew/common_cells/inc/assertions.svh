// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0

`ifndef COMMON_CELLS_ASSERTIONS_SVH
`define COMMON_CELLS_ASSERTIONS_SVH

`define ASSERT_INIT(name, expr)
`define ASSERT_FINAL(name, expr)
`define ASSERT(name, expr, clk, rst)
`define ASSERT_NEVER(name, expr, clk, rst)
`define ASSERT_KNOWN(name, expr, clk, rst)
`define ASSUME(name, expr, clk, rst)
`define COVER(name, expr, clk, rst)

`endif
