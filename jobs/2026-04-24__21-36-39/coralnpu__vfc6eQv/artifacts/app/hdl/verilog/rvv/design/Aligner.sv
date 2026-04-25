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

// Data aligner that packs valid entries to the left (lower positions)
module Aligner #(
  parameter int DATA_WIDTH = 32,
  parameter int N          = 4
) (
  input  logic [N-1:0]            valid_i,
  input  logic [N*DATA_WIDTH-1:0] data_i,
  output logic [N-1:0]            valid_o,
  output logic [N*DATA_WIDTH-1:0] data_o
);
  // Packs valid entries to lower positions
  always_comb begin
    valid_o = '0;
    data_o  = '0;
    automatic int j = 0;
    for (int i = 0; i < N; i++) begin
      if (valid_i[i]) begin
        valid_o[j] = 1'b1;
        data_o[j*DATA_WIDTH +: DATA_WIDTH] = data_i[i*DATA_WIDTH +: DATA_WIDTH];
        j++;
      end
    end
  end
endmodule
