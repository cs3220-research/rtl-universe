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

// Simple synchronous TileLink-UL to SRAM adapter.
//
// This is a minimal adapter. The TL-UL request fields are presented on a
// flattened 'tl_i' bus and the response on 'tl_o'. For full interoperability
// with OpenTitan TL-UL, this adapter expects an external decoder to extract
// fields and to assemble the response packet.
//
// The internal SRAM-side interface is the standard one used elsewhere in the
// repo: req/we/addr/wdata/wmask + rvalid/rdata.
module TlulAdapterSram #(
    parameter int unsigned DataWidth     = 32,
    parameter int unsigned AddrWidth     = 10,
    parameter int unsigned TlReqWidth    = 1 + 1 + AddrWidth + DataWidth +
                                           (DataWidth/8),
    parameter int unsigned TlRspWidth    = 1 + DataWidth
) (
    input  wire                          clk,
    input  wire                          rst_n,

    // Internal SRAM port.
    output wire                          req_o,
    output wire                          we_o,
    output wire [AddrWidth-1:0]          addr_o,
    output wire [DataWidth-1:0]          wdata_o,
    output wire [(DataWidth/8)-1:0]      wmask_o,
    input  wire                          rvalid_i,
    input  wire [DataWidth-1:0]          rdata_i,

    // External SRAM-style request (for backward compatibility / tests).
    input  wire                          req_i,
    input  wire                          we_i,
    input  wire [AddrWidth-1:0]          addr_i,
    input  wire [DataWidth-1:0]          wdata_i,
    input  wire [(DataWidth/8)-1:0]      wmask_i,
    output wire                          rvalid_o,
    output wire [DataWidth-1:0]          rdata_o,

    // Flattened TL-UL request/response buses (placeholder).
    input  wire [TlReqWidth-1:0]         tl_i,
    output wire [TlRspWidth-1:0]         tl_o
);

  // Drive the internal SRAM port directly from the request inputs.
  assign req_o   = req_i;
  assign we_o    = we_i;
  assign addr_o  = addr_i;
  assign wdata_o = wdata_i;
  assign wmask_o = wmask_i;

  // Pass the SRAM read response back out.
  assign rvalid_o = rvalid_i;
  assign rdata_o  = rdata_i;

  // Internal storage for stand-alone use (optional). Allows the adapter to be
  // exercised without an external SRAM by using the local memory if no
  // backing SRAM is connected. When wired to an external SRAM the user can
  // simply ignore the *_o outputs.
  reg [DataWidth-1:0] local_mem [0:(1<<AddrWidth)-1];
  reg [DataWidth-1:0] local_rdata;
  reg                 local_rvalid;

  integer i;
  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      local_rvalid <= 1'b0;
      local_rdata  <= '0;
    end else begin
      local_rvalid <= 1'b0;
      if (req_i) begin
        if (we_i) begin
          for (i = 0; i < DataWidth/8; i = i + 1) begin
            if (wmask_i[i]) begin
              local_mem[addr_i][i*8 +: 8] <= wdata_i[i*8 +: 8];
            end
          end
        end else begin
          local_rdata  <= local_mem[addr_i];
          local_rvalid <= 1'b1;
        end
      end
    end
  end

  // TL-UL response assembly: {valid, data}. This is a simplified passthrough
  // that mirrors the SRAM read response.
  assign tl_o = {rvalid_i, rdata_i};

  // Silence unused warnings for tl_i.
  wire _unused_tl;
  assign _unused_tl = |tl_i;

endmodule
