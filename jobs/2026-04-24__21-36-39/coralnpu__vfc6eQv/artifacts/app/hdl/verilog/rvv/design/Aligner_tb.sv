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

// Testbench for Aligner module
module Aligner_tb;
  parameter int DATA_WIDTH = 32;
  parameter int N = 4;

  logic [N-1:0]            valid_i;
  logic [N*DATA_WIDTH-1:0] data_i;
  logic [N-1:0]            valid_o;
  logic [N*DATA_WIDTH-1:0] data_o;

  Aligner #(
    .DATA_WIDTH(DATA_WIDTH),
    .N(N)
  ) dut (
    .valid_i(valid_i),
    .data_i(data_i),
    .valid_o(valid_o),
    .data_o(data_o)
  );

  initial begin
    // Test 1: All valid
    valid_i = 4'b1111;
    data_i  = {32'd4, 32'd3, 32'd2, 32'd1};
    #1;
    $display("Test 1: valid_o=%b data_o=%h", valid_o, data_o);
    assert(valid_o == 4'b1111) else $fatal(1, "Test 1 failed");

    // Test 2: Alternating valid
    valid_i = 4'b1010;
    data_i  = {32'd4, 32'd0, 32'd2, 32'd0};
    #1;
    $display("Test 2: valid_o=%b data_o=%h", valid_o, data_o);
    assert(valid_o[1:0] == 2'b11) else $fatal(1, "Test 2 failed");

    // Test 3: None valid
    valid_i = 4'b0000;
    data_i  = '0;
    #1;
    $display("Test 3: valid_o=%b data_o=%h", valid_o, data_o);
    assert(valid_o == 4'b0000) else $fatal(1, "Test 3 failed");

    $display("All Aligner tests PASSED");
    $finish;
  end
endmodule
