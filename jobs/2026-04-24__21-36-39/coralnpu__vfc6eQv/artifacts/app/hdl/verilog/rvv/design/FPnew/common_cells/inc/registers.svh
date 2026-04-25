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

`ifndef REGISTERS_SVH
`define REGISTERS_SVH

// Register macros for common_cells
// FF: D flip-flop with synchronous reset
`define FF(q, d, rst_val, clk, rst_n) \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n) q <= rst_val; \
    else        q <= d;       \
  end

// FFL: D flip-flop with load enable and synchronous reset
`define FFL(q, d, load, rst_val, clk, rst_n) \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n)  q <= rst_val; \
    else if (load) q <= d;     \
  end

// FFAR: D flip-flop with async reset
`define FFAR(q, d, rst_val, clk, rst_n) \
  always_ff @(posedge clk or negedge rst_n) begin \
    if (!rst_n) q <= rst_val; \
    else        q <= d;       \
  end

// FFNR: D flip-flop without reset
`define FFNR(q, d, clk) \
  always_ff @(posedge clk) begin \
    q <= d; \
  end

`endif // REGISTERS_SVH
