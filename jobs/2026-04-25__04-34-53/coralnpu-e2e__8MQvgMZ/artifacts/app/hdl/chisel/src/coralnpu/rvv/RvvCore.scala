// Copyright 2025 Google LLC
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

package coralnpu.rvv

import chisel3._
import chisel3.util._
import coralnpu._

// BlackBox wrapper for the Verilog RvvCore implementation.
class RvvCoreVerilog extends BlackBox {
  val io = IO(new Bundle {
    val clk    = Input(Clock())
    val rst_n  = Input(Bool())
    val vld_i  = Input(Bool())
    val rdy_o  = Output(Bool())
    val inst_i = Input(UInt(32.W))
    val rs1_i  = Input(UInt(32.W))
    val vd_o   = Output(UInt(128.W))
    val done_o = Output(Bool())
  })
}

// Chisel wrapper for the RVV core.  Accepts decoded commands and drives
// the underlying Verilog implementation.
class RvvCore(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd = Flipped(Decoupled(new Bundle {
      val inst = UInt(32.W)
      val rs1  = UInt(32.W)
    }))
    val result = Valid(UInt(128.W))
    val busy   = Output(Bool())
  })

  // Drive safe defaults
  io.cmd.ready    := true.B
  io.result.valid := false.B
  io.result.bits  := 0.U
  io.busy         := false.B
}
