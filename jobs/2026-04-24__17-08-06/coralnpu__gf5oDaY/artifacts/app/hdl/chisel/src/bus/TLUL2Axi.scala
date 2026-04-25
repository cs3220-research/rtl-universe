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

/** TileLink-UL to AXI4 bridge (stub). */
class TLUL2Axi(p: Parameters) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val tl  = Flipped(new OpenTitanTileLink.Host2Device(tlulP))
    val axi = new Axi4Master(p)
  })
  // Tie off outputs
  io.tl.a.ready  := false.B
  io.tl.d.valid  := false.B
  io.tl.d.bits   := 0.U.asTypeOf(new TLULChannelD(tlulP))

  io.axi.ar.valid := false.B
  io.axi.ar.bits  := 0.U.asTypeOf(io.axi.ar.bits)
  io.axi.aw.valid := false.B
  io.axi.aw.bits  := 0.U.asTypeOf(io.axi.aw.bits)
  io.axi.w.valid  := false.B
  io.axi.w.bits   := 0.U.asTypeOf(io.axi.w.bits)
  io.axi.r.ready  := false.B
  io.axi.b.ready  := false.B
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object EmitTLUL2Axi extends App {
  val p = new coralnpu.Parameters
  ChiselStage.emitSystemVerilogFile(new TLUL2Axi(p), args)
}
