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

// Specific SRAM: 256 entries x 288 bits (for L1 D-cache with ECC)
// 288-bit data width uses 36-bit byte mask (288/8 = 36)
module Sram_1rwm_256x288 (
  input  wire        clk,
  input  wire        en,
  input  wire        write,
  input  wire [7:0]  addr,
  input  wire [287:0] wdata,
  input  wire [35:0] wmask,
  output reg  [287:0] rdata
);
  reg [287:0] mem [0:255];
  integer i;
  always @(posedge clk) begin
    if (en) begin
      if (write) begin
        for (i = 0; i < 36; i = i+1) begin
          if (wmask[i]) mem[addr][i*8 +: 8] <= wdata[i*8 +: 8];
        end
      end else begin
        rdata <= mem[addr];
      end
    end
  end
endmodule
