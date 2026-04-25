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

// Multi-entry FIFO with valid/ready handshaking
module handshake_multi_fifo #(
  parameter int DATA_WIDTH = 32,
  parameter int DEPTH      = 8
) (
  input  logic                    clk,
  input  logic                    rst_n,

  // Push interface
  input  logic                    push_valid,
  output logic                    push_ready,
  input  logic [DATA_WIDTH-1:0]   push_data,

  // Pop interface
  output logic                    pop_valid,
  input  logic                    pop_ready,
  output logic [DATA_WIDTH-1:0]   pop_data,

  // Status
  output logic [$clog2(DEPTH):0]  count
);
  localparam int PTR_W = $clog2(DEPTH);

  logic [DATA_WIDTH-1:0] mem [0:DEPTH-1];
  logic [PTR_W-1:0]      wr_ptr;
  logic [PTR_W-1:0]      rd_ptr;
  logic [PTR_W:0]        cnt;

  assign count      = cnt;
  assign push_ready = (cnt < DEPTH);
  assign pop_valid  = (cnt > 0);
  assign pop_data   = mem[rd_ptr];

  always_ff @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      wr_ptr <= '0;
      rd_ptr <= '0;
      cnt    <= '0;
    end else begin
      if (push_valid && push_ready) begin
        mem[wr_ptr] <= push_data;
        wr_ptr      <= wr_ptr + 1'b1;
        cnt         <= cnt + 1'b1;
      end
      if (pop_valid && pop_ready) begin
        rd_ptr <= rd_ptr + 1'b1;
        cnt    <= cnt - 1'b1;
      end
      if (push_valid && push_ready && pop_valid && pop_ready) begin
        cnt <= cnt;
      end
    end
  end

endmodule
