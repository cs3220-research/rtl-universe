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

module RstSync #(
  parameter int STAGES = 2
) (
  input  logic clk_i,
  input  logic rst_ni,
  output logic rst_no
);
  logic [STAGES-1:0] sync_ff;
  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) sync_ff <= '0;
    else         sync_ff <= {sync_ff[STAGES-2:0], 1'b1};
  end
  assign rst_no = sync_ff[STAGES-1];
endmodule
