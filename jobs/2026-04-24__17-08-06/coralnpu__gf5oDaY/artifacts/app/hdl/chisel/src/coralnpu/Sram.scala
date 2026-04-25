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
import chisel3.util._
import chisel3.experimental.ExtModule

/** BlackBox wrapper for Sram.v Verilog primitive. */
class Sram1rw(depth: Int = 256, width: Int = 288) extends ExtModule(Map(
  "DEPTH" -> depth,
  "WIDTH" -> width,
)) {
  val clk   = IO(Input(Clock()))
  val ce_n  = IO(Input(Bool()))
  val we_n  = IO(Input(Bool()))
  val addr  = IO(Input(UInt(log2Ceil(depth).W)))
  val din   = IO(Input(UInt(width.W)))
  val dout  = IO(Output(UInt(width.W)))
}

/** Chisel wrapper around the Sram1rw BlackBox. */
class SramWrapper(depth: Int = 256, width: Int = 288) extends Module {
  val addrBits = log2Ceil(depth)

  val io = IO(new Bundle {
    val addr  = Input(UInt(addrBits.W))
    val wen   = Input(Bool())
    val wdata = Input(UInt(width.W))
    val rdata = Output(UInt(width.W))
  })

  val sram = Module(new Sram1rw(depth, width))
  sram.clk  := clock
  sram.ce_n := false.B
  sram.we_n := !io.wen
  sram.addr := io.addr
  sram.din  := io.wdata
  io.rdata  := sram.dout
}
