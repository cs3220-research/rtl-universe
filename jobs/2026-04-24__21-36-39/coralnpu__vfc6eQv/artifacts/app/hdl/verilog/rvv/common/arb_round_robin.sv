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

// Round-robin arbiter
module arb_round_robin #(
  parameter int N = 4
) (
  input  logic        clk,
  input  logic        rst_n,
  input  logic [N-1:0] req,
  output logic [N-1:0] grant
);
  logic [N-1:0] last_grant;
  logic [N-1:0] mask;
  logic [N-1:0] masked_req;
  logic [N-1:0] unmasked_grant;
  logic [N-1:0] masked_grant;
  logic any_masked;

  // Create mask to exclude already-served requests
  always_comb begin
    mask = '0;
    for (int i = 0; i < N; i++) begin
      if (last_grant[i]) begin
        for (int j = 0; j <= i; j++) begin
          mask[j] = 1'b1;
        end
      end
    end
  end

  assign masked_req = req & ~mask;
  assign any_masked = |masked_req;

  // Priority encoder for masked and unmasked
  always_comb begin
    masked_grant   = '0;
    unmasked_grant = '0;
    for (int i = N-1; i >= 0; i--) begin
      if (masked_req[i])   masked_grant   = (1 << i);
      if (req[i])          unmasked_grant = (1 << i);
    end
  end

  assign grant = any_masked ? masked_grant : unmasked_grant;

  always_ff @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      last_grant <= '0;
    end else begin
      if (|grant) last_grant <= grant;
    end
  end

endmodule
