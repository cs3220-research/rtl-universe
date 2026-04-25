// Copyright 2026 Google LLC
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

// 2-FF reset synchronizer.
// Asynchronously asserted when 'd' is low (active-low reset assert is
// effectively asynchronous), synchronously deasserted on rising edge of clk.
// Implements: q follows d through two FFs, with the async reset path bringing
// both stages low immediately.
module RstSync(
    input  wire clk,
    input  wire d,
    output wire q
);

  reg q1;
  reg q2;

  always @(posedge clk or negedge d) begin
    if (!d) begin
      q1 <= 1'b0;
      q2 <= 1'b0;
    end else begin
      q1 <= 1'b1;
      q2 <= q1;
    end
  end

  assign q = q2;

endmodule
