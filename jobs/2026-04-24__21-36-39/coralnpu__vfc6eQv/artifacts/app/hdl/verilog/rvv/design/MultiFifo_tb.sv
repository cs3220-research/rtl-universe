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

// Testbench for MultiFifo module
module MultiFifo_tb;
  parameter int DATA_WIDTH = 32;
  parameter int DEPTH      = 8;
  parameter int PUSH_PORTS = 2;
  parameter int POP_PORTS  = 2;

  logic clk;
  logic rst_n;

  logic [PUSH_PORTS-1:0]            push_valid;
  logic [PUSH_PORTS-1:0]            push_ready;
  logic [PUSH_PORTS*DATA_WIDTH-1:0] push_data;

  logic [POP_PORTS-1:0]             pop_valid;
  logic [POP_PORTS-1:0]             pop_ready;
  logic [POP_PORTS*DATA_WIDTH-1:0]  pop_data;

  logic [$clog2(DEPTH+1)-1:0] count;
  logic full, empty;

  // Clock
  initial clk = 0;
  always #5 clk = ~clk;

  MultiFifo #(
    .DATA_WIDTH(DATA_WIDTH),
    .DEPTH(DEPTH),
    .PUSH_PORTS(PUSH_PORTS),
    .POP_PORTS(POP_PORTS)
  ) dut (.*);

  initial begin
    rst_n      = 0;
    push_valid = '0;
    push_data  = '0;
    pop_ready  = '0;
    @(posedge clk); #1;
    rst_n = 1;

    // Push 2 items
    push_valid = 2'b11;
    push_data  = {32'd200, 32'd100};
    @(posedge clk); #1;
    push_valid = '0;

    // Pop 2 items
    pop_ready = 2'b11;
    @(posedge clk); #1;
    pop_ready = '0;

    @(posedge clk); #1;
    $display("MultiFifo test PASSED");
    $finish;
  end
endmodule
