// Copyright 2026 Google LLC
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

// 256-entry x 256-bit single-port SRAM, no write mask.
module Sram_1rw_256x256 #(
    parameter BASE_ADDR = 64'h0
) (
    input  wire         CK,
    input  wire         EN,
    input  wire         WE,
    input  wire [7:0]   A,
    input  wire [255:0] D,
    output reg  [255:0] Q
);

  localparam integer DEPTH       = 256;
  localparam integer WIDTH       = 256;
  localparam integer WIDTH_BYTES = WIDTH / 8;
  localparam integer SIZE_BYTES  = DEPTH * WIDTH_BYTES;

`ifdef USE_DPI
  import "DPI-C" function chandle sram_init(
      input longint global_addr,
      input longint size_bytes,
      input int     width_bytes);
  import "DPI-C" function void sram_read(
      input  chandle h, input int addr, output bit [WIDTH-1:0] data);
  import "DPI-C" function void sram_write(
      input chandle h, input int addr,
      input bit [WIDTH-1:0] data, input int wmask);

  chandle sram_handle;
  initial begin
    sram_handle = sram_init(BASE_ADDR, SIZE_BYTES, WIDTH_BYTES);
  end

  bit [WIDTH-1:0] read_data;
  always @(posedge CK) begin
    if (EN) begin
      if (WE) begin
        sram_write(sram_handle, {24'd0, A}, D, 32'hFFFFFFFF);
      end else begin
        sram_read(sram_handle, {24'd0, A}, read_data);
        Q <= read_data;
      end
    end
  end
`else
  reg [255:0] mem [0:255];

  always @(posedge CK) begin
    if (EN) begin
      if (WE) begin
        mem[A] <= D;
      end else begin
        Q <= mem[A];
      end
    end
  end
`endif

endmodule
