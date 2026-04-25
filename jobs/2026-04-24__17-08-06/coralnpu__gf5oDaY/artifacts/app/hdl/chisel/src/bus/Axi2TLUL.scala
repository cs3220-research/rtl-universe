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

package bus

import chisel3._
import chisel3.util._
import coralnpu.Parameters

/** AXI4 to TileLink-UL bridge (stub). */
class Axi2TLUL(p: Parameters) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val axi = Flipped(new Axi4Slave(p))
    val tl  = new OpenTitanTileLink.Host2Device(tlulP)
  })
  // Tie off outputs
  io.axi.r.valid  := false.B
  io.axi.r.bits   := 0.U.asTypeOf(io.axi.r.bits)
  io.axi.b.valid  := false.B
  io.axi.b.bits   := 0.U.asTypeOf(io.axi.b.bits)
  io.axi.ar.ready := false.B
  io.axi.aw.ready := false.B
  io.axi.w.ready  := false.B

  io.tl.a.valid := false.B
  io.tl.a.bits  := 0.U.asTypeOf(new TLULChannelA(tlulP))
  io.tl.d.ready := false.B
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object EmitAxi2TLUL extends App {
  val p = new coralnpu.Parameters
  ChiselStage.emitSystemVerilogFile(new Axi2TLUL(p), args)
}
