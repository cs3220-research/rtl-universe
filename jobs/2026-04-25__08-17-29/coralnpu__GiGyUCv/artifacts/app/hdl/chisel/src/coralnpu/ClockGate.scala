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

package coralnpu

import chisel3._
import chisel3.util.HasBlackBoxInline

/** Clock gate cell.
  *
  * When USE_GENERIC is defined, uses a simple latch-based simulation model.
  * Otherwise, the cell should be replaced by the technology clock gate.
  */
class ClockGate extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val enable = Input(Bool())
    val te     = Input(Bool())
    val clk_o  = Output(Clock())
  })

  setInline(
    "ClockGate.sv",
    """module ClockGate (
      |  input  logic clk_i,
      |  input  logic enable,
      |  input  logic te,
      |  output logic clk_o
      |);
      |`ifdef USE_GENERIC
      |  logic en_latch;
      |  always_latch begin
      |    if (!clk_i) begin
      |      en_latch = enable | te;
      |    end
      |  end
      |  assign clk_o = clk_i & en_latch;
      |`else
      |  // Placeholder: technology-specific cell to be inserted by synthesis.
      |  assign clk_o = clk_i & (enable | te);
      |`endif
      |endmodule
      |""".stripMargin
  )
}
