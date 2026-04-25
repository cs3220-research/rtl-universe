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

// 4-to-2 carry-save adder (two-level CSA)
module compressor_4_2 #(
  parameter int WIDTH = 32
) (
  input  logic [WIDTH-1:0] a,
  input  logic [WIDTH-1:0] b,
  input  logic [WIDTH-1:0] c,
  input  logic [WIDTH-1:0] d,
  input  logic [WIDTH-1:0] cin,
  output logic [WIDTH-1:0] sum,
  output logic [WIDTH-1:0] carry,
  output logic [WIDTH-1:0] cout
);
  logic [WIDTH-1:0] t1, t2;

  // First level: compress a, b, c
  assign t1 = a ^ b ^ c;
  assign t2 = ((a & b) | (b & c) | (a & c)) << 1;

  // Second level: compress t1, d, cin
  assign sum   = t1 ^ d ^ cin;
  assign carry = ((t1 & d) | (d & cin) | (t1 & cin)) << 1;
  assign cout  = t2;
endmodule
