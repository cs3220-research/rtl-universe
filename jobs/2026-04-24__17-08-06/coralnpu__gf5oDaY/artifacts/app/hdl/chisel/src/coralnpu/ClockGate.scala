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

/** The actual ClockGate primitive BlackBox.
  *
  * When Chisel generates an instantiation of a BlackBox, it connects ports
  * using their raw names (without the io_ prefix). So the Verilog module
  * must also use the raw names: clk_i, enable, te, clk_o.
  */
class ClockGatePrimitive extends BlackBox with HasBlackBoxInline {
  override def desiredName = "ClockGate"
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val enable = Input(Bool())
    val te     = Input(Bool())
    val clk_o  = Output(Clock())
  })

  setInline("ClockGate.sv",
    """|`ifdef USE_GENERIC
       |module ClockGate (
       |  input  logic clk_i,
       |  input  logic enable,
       |  input  logic te,
       |  output logic clk_o
       |);
       |  logic enable_latch;
       |  always_latch begin
       |    if (!clk_i) begin
       |      enable_latch = enable | te;
       |    end
       |  end
       |  assign clk_o = clk_i & enable_latch;
       |endmodule
       |`else
       |module ClockGate (
       |  input  logic clk_i,
       |  input  logic enable,
       |  input  logic te,
       |  output logic clk_o
       |);
       |  logic enable_latch;
       |  always_latch begin
       |    if (!clk_i) begin
       |      enable_latch = enable | te;
       |    end
       |  end
       |  assign clk_o = clk_i & enable_latch;
       |endmodule
       |`endif
       |""".stripMargin)
}

/** Chisel Module wrapper for the clock gate cell.
  * Exposes an io Bundle so it can be used uniformly in Chisel designs.
  */
class ClockGate extends Module {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val enable = Input(Bool())
    val te     = Input(Bool())
    val clk_o  = Output(Clock())
  })

  val bb = Module(new ClockGatePrimitive)
  bb.io.clk_i  := io.clk_i
  bb.io.enable := io.enable
  bb.io.te     := io.te
  io.clk_o     := bb.io.clk_o
}
