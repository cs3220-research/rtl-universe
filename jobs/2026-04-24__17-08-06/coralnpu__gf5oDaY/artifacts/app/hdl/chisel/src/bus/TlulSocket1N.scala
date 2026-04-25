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

/** 1-to-N TileLink socket with 128-bit data (stub).
  * Routes one host port to N device ports based on address.
  */
class TlulSocket1N_128(p: Parameters, n: Int = 2) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val host = Flipped(new OpenTitanTileLink.Host2Device(tlulP))
    val devs = Vec(n, new OpenTitanTileLink.Host2Device(tlulP))
  })
  // Stub: tie off all outputs
  io.host.a.ready := false.B
  io.host.d.valid := false.B
  io.host.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlulP))
  for (i <- 0 until n) {
    io.devs(i).a.valid := false.B
    io.devs(i).a.bits  := 0.U.asTypeOf(new TLULChannelA(tlulP))
    io.devs(i).d.ready := false.B
  }
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulSocket1N_128Emitter extends App {
  val p = new coralnpu.Parameters
  p.lsuDataBits = 128
  ChiselStage.emitSystemVerilogFile(new TlulSocket1N_128(p), args)
}
