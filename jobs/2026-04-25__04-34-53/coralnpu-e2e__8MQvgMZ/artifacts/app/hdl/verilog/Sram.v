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

// Parameterizable single-port SRAM with byte-mask write support.
//
// In simulation (USE_DPI defined), the storage is held in C++ (sram_backdoor.cc)
// and accessed via DPI calls. This allows tests to pre-load memory contents
// via SramBackdoorLoad / sram_backdoor_load_c.
//
// In synthesis or when USE_DPI is not defined, falls back to a simple reg
// array implementation.
module Sram #(
    parameter integer DEPTH      = 256,
    parameter integer WIDTH      = 32,
    parameter         BASE_ADDR  = 64'h0
) (
    input  wire                       clk,
    input  wire                       en,
    input  wire                       write,
    input  wire [$clog2(DEPTH)-1:0]   addr,
    input  wire [WIDTH-1:0]           wdata,
    input  wire [(WIDTH/8)-1:0]       wmask,
    output reg  [WIDTH-1:0]           rdata,
    // Independent backdoor read port (DPI simulation only)
    input  wire                       bd_en,
    input  wire [$clog2(DEPTH)-1:0]   bd_addr,
    output reg  [WIDTH-1:0]           bd_rdata,
    // Independent backdoor write port (DPI simulation only)
    input  wire                       bd_wen,
    input  wire [$clog2(DEPTH)-1:0]   bd_waddr,
    input  wire [WIDTH-1:0]           bd_wdata,
    input  wire [(WIDTH/8)-1:0]       bd_wmask
);

  localparam integer WIDTH_BYTES = (WIDTH + 7) / 8;
  localparam integer SIZE_BYTES  = DEPTH * WIDTH_BYTES;

`ifdef USE_DPI
  // DPI imports for simulation backdoor.
  import "DPI-C" function chandle sram_init(
      input longint global_addr,
      input longint size_bytes,
      input int     width_bytes);

  import "DPI-C" function void sram_read(
      input  chandle             h,
      input  int                 addr,
      output bit [WIDTH-1:0]     data);

  import "DPI-C" function void sram_write(
      input chandle              h,
      input int                  addr,
      input bit [WIDTH-1:0]      data,
      input int                  wmask);

  chandle sram_handle;

  initial begin
    sram_handle = sram_init(BASE_ADDR, SIZE_BYTES, WIDTH_BYTES);
  end

  bit [WIDTH-1:0] read_data;
  always @(posedge clk) begin
    if (en) begin
      if (write) begin
        sram_write(sram_handle, {{(32-$clog2(DEPTH)){1'b0}}, addr},
                   wdata, {{(32-(WIDTH/8)){1'b0}}, wmask});
      end else begin
        sram_read(sram_handle, {{(32-$clog2(DEPTH)){1'b0}}, addr}, read_data);
        rdata <= read_data;
      end
    end
  end

  // Backdoor read port: independent of the main port, reads at bd_addr
  // regardless of CPU activity on the main port.
  bit [WIDTH-1:0] bd_read_data;
  always @(posedge clk) begin
    if (bd_en) begin
      sram_read(sram_handle, {{(32-$clog2(DEPTH)){1'b0}}, bd_addr}, bd_read_data);
      bd_rdata <= bd_read_data;
    end
  end

  // Backdoor write port: independent of the main port.
  always @(posedge clk) begin
    if (bd_wen) begin
      sram_write(sram_handle, {{(32-$clog2(DEPTH)){1'b0}}, bd_waddr},
                 bd_wdata, {{(32-(WIDTH/8)){1'b0}}, bd_wmask});
    end
  end
`else
  // Pure-RTL fallback: simple reg array.
  reg [WIDTH-1:0] mem [0:DEPTH-1];

  integer i;
  always @(posedge clk) begin
    if (en) begin
      if (write) begin
        for (i = 0; i < WIDTH_BYTES; i = i + 1) begin
          if (wmask[i]) begin
            mem[addr][i*8 +: 8] <= wdata[i*8 +: 8];
          end
        end
      end else begin
        rdata <= mem[addr];
      end
    end
  end

  always @(posedge clk) begin
    if (bd_en) begin
      bd_rdata <= mem[bd_addr];
    end
  end

  always @(posedge clk) begin
    if (bd_wen) begin
      for (i = 0; i < WIDTH_BYTES; i = i + 1) begin
        if (bd_wmask[i]) begin
          mem[bd_waddr][i*8 +: 8] <= bd_wdata[i*8 +: 8];
        end
      end
    end
  end
`endif

endmodule
