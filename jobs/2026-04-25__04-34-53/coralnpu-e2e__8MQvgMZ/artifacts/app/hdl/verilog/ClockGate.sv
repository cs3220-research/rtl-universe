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

// Clock gate cell.
// When USE_GENERIC is defined: implements an integrated clock gate using a
// simple latch (transparent when clk_i is low) AND'ed with the clock.
// Otherwise: instantiates a black-box library cell.
module ClockGate(
    input  wire clk_i,
    input  wire enable,
    input  wire te,
    output wire clk_o
);

`ifdef USE_GENERIC
  // Latch-based clock gate. Capture (enable | te) when clock is low so the
  // gating signal is stable while clock is high.
  reg enable_latched;
  always @(*) begin
    if (!clk_i) begin
      enable_latched = enable | te;
    end
  end
  assign clk_o = clk_i & enable_latched;
`else
  // Vendor library cell black box.
  CKLNQD10BWP6T20P96CPDLVT u_ckln (
    .CP  (clk_i),
    .E   (enable),
    .TE  (te),
    .Q   (clk_o)
  );
`endif

endmodule
