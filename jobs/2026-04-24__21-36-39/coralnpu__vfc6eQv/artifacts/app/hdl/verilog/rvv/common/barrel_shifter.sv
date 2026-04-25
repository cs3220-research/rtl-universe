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

// Parameterized barrel shifter supporting left/right, logical/arithmetic
module barrel_shifter #(
  parameter int DATA_WIDTH  = 128,
  parameter int SHIFT_WIDTH = 7
) (
  input  logic [DATA_WIDTH-1:0]  data_in,
  input  logic [SHIFT_WIDTH-1:0] shift_amount,
  input  logic                   direction,  // 0=left, 1=right
  input  logic                   arith,      // 0=logical, 1=arithmetic
  output logic [DATA_WIDTH-1:0]  data_out
);
  logic [DATA_WIDTH-1:0] shifted_left;
  logic [DATA_WIDTH-1:0] shifted_right_logical;
  logic [DATA_WIDTH-1:0] shifted_right_arith;
  logic sign_bit;

  assign sign_bit             = data_in[DATA_WIDTH-1];
  assign shifted_left         = data_in << shift_amount;
  assign shifted_right_logical = data_in >> shift_amount;

  // Arithmetic right shift: fill with sign bit
  always_comb begin
    shifted_right_arith = data_in >> shift_amount;
    if (sign_bit) begin
      for (int i = DATA_WIDTH-1; i >= 0; i--) begin
        if (i >= (DATA_WIDTH - shift_amount)) begin
          shifted_right_arith[i] = 1'b1;
        end
      end
    end
  end

  always_comb begin
    if (!direction) begin
      data_out = shifted_left;
    end else if (!arith) begin
      data_out = shifted_right_logical;
    end else begin
      data_out = shifted_right_arith;
    end
  end

endmodule
