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

// 2D enable D flip-flop: 2D array of enable flip-flops
module edff_2d #(
  parameter int WIDTH  = 8,
  parameter int DEPTH  = 4
) (
  input  logic                       clk,
  input  logic                       rst_n,
  input  logic [DEPTH-1:0]           en,
  input  logic [DEPTH*WIDTH-1:0]     d,
  output logic [DEPTH*WIDTH-1:0]     q
);
  genvar gi;
  generate
    for (gi = 0; gi < DEPTH; gi++) begin : gen_edff
      always_ff @(posedge clk or negedge rst_n) begin
        if (!rst_n) begin
          q[gi*WIDTH +: WIDTH] <= '0;
        end else if (en[gi]) begin
          q[gi*WIDTH +: WIDTH] <= d[gi*WIDTH +: WIDTH];
        end
      end
    end
  endgenerate
endmodule
