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

// Specific SRAM configuration: 256 entries x 256 bits (for L1 I-cache)
module Sram_1rw_256x256 (
  input  wire        clk,
  input  wire        en,
  input  wire        write,
  input  wire [7:0]  addr,
  input  wire [255:0] wdata,
  input  wire [31:0] wmask,
  output reg  [255:0] rdata
);
  reg [255:0] mem [0:255];
  integer i;
  always @(posedge clk) begin
    if (en) begin
      if (write) begin
        for (i = 0; i < 32; i = i+1) begin
          if (wmask[i]) mem[addr][i*8 +: 8] <= wdata[i*8 +: 8];
        end
      end else begin
        rdata <= mem[addr];
      end
    end
  end
endmodule
