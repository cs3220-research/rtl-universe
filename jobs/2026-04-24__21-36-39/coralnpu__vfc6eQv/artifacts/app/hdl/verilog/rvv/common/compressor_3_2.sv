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

// 3-to-2 carry-save adder (full adder)
module compressor_3_2 #(
  parameter int WIDTH = 32
) (
  input  logic [WIDTH-1:0] a,
  input  logic [WIDTH-1:0] b,
  input  logic [WIDTH-1:0] c,
  output logic [WIDTH-1:0] sum,
  output logic [WIDTH-1:0] carry
);
  // Bitwise full adder: sum = a^b^c, carry = majority(a,b,c) shifted left
  assign sum   = a ^ b ^ c;
  assign carry = ((a & b) | (b & c) | (a & c)) << 1;
endmodule
