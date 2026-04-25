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

// Handshake flip-flop: single-entry register with valid/ready handshaking
module handshake_ff #(
  parameter int DATA_WIDTH = 32
) (
  input  logic                    clk,
  input  logic                    rst_n,

  // Input handshake
  input  logic                    in_valid,
  output logic                    in_ready,
  input  logic [DATA_WIDTH-1:0]   in_data,

  // Output handshake
  output logic                    out_valid,
  input  logic                    out_ready,
  output logic [DATA_WIDTH-1:0]   out_data
);
  logic [DATA_WIDTH-1:0] data_reg;
  logic                  full;

  assign in_ready  = !full;
  assign out_valid = full;
  assign out_data  = data_reg;

  always_ff @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      full     <= 1'b0;
      data_reg <= '0;
    end else begin
      if (in_valid && in_ready) begin
        data_reg <= in_data;
        full     <= 1'b1;
      end else if (out_valid && out_ready) begin
        full     <= 1'b0;
      end
    end
  end

endmodule
