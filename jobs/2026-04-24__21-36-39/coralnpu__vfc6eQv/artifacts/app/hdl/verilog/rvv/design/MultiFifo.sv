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

// Multi-port FIFO for RVV design
module MultiFifo #(
  parameter int DATA_WIDTH  = 32,
  parameter int DEPTH       = 8,
  parameter int PUSH_PORTS  = 2,
  parameter int POP_PORTS   = 2
) (
  input  logic                              clk,
  input  logic                              rst_n,

  // Push interface
  input  logic [PUSH_PORTS-1:0]             push_valid,
  output logic [PUSH_PORTS-1:0]             push_ready,
  input  logic [PUSH_PORTS*DATA_WIDTH-1:0]  push_data,

  // Pop interface
  output logic [POP_PORTS-1:0]              pop_valid,
  input  logic [POP_PORTS-1:0]              pop_ready,
  output logic [POP_PORTS*DATA_WIDTH-1:0]   pop_data,

  // Status
  output logic [$clog2(DEPTH+1)-1:0]        count,
  output logic                              full,
  output logic                              empty
);
  localparam int PTR_W = $clog2(DEPTH);

  logic [DATA_WIDTH-1:0]      mem [0:DEPTH-1];
  logic [PTR_W:0]             wr_ptr;
  logic [PTR_W:0]             rd_ptr;
  logic [$clog2(DEPTH+1)-1:0] cnt;

  assign count = cnt;
  assign full  = (cnt == DEPTH);
  assign empty = (cnt == '0);

  always_comb begin
    for (int i = 0; i < PUSH_PORTS; i++) begin
      push_ready[i] = (cnt + i < DEPTH);
    end
    for (int i = 0; i < POP_PORTS; i++) begin
      pop_valid[i] = (cnt > i);
      pop_data[i*DATA_WIDTH +: DATA_WIDTH] = mem[(rd_ptr[PTR_W-1:0] + i) % DEPTH];
    end
  end

  logic [$clog2(PUSH_PORTS+1)-1:0] num_push;
  logic [$clog2(POP_PORTS+1)-1:0]  num_pop;

  always_comb begin
    num_push = '0;
    for (int i = 0; i < PUSH_PORTS; i++)
      if (push_valid[i] && push_ready[i]) num_push = num_push + 1'b1;
    num_pop = '0;
    for (int i = 0; i < POP_PORTS; i++)
      if (pop_valid[i] && pop_ready[i]) num_pop = num_pop + 1'b1;
  end

  always_ff @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      wr_ptr <= '0;
      rd_ptr <= '0;
      cnt    <= '0;
    end else begin
      for (int i = 0; i < PUSH_PORTS; i++) begin
        if (push_valid[i] && push_ready[i])
          mem[(wr_ptr[PTR_W-1:0] + i) % DEPTH] <= push_data[i*DATA_WIDTH +: DATA_WIDTH];
      end
      wr_ptr <= wr_ptr + num_push;
      rd_ptr <= rd_ptr + num_pop;
      cnt    <= cnt + num_push - num_pop;
    end
  end

endmodule
