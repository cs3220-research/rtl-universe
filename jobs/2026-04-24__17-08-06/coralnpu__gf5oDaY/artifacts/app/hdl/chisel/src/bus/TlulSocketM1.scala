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

/** M-to-1 TileLink socket with 128-bit data and 2 host ports (stub). */
class TlulSocketM1_2_128(p: Parameters) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val hosts = Vec(2, Flipped(new OpenTitanTileLink.Host2Device(tlulP)))
    val dev   = new OpenTitanTileLink.Host2Device(tlulP)
  })
  // Stub: tie off all outputs
  for (i <- 0 until 2) {
    io.hosts(i).a.ready := false.B
    io.hosts(i).d.valid := false.B
    io.hosts(i).d.bits  := 0.U.asTypeOf(new TLULChannelD(tlulP))
  }
  io.dev.a.valid := false.B
  io.dev.a.bits  := 0.U.asTypeOf(new TLULChannelA(tlulP))
  io.dev.d.ready := false.B
}

/** M-to-1 TileLink socket with 128-bit data and 3 host ports (stub). */
class TlulSocketM1_3_128(p: Parameters) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val hosts = Vec(3, Flipped(new OpenTitanTileLink.Host2Device(tlulP)))
    val dev   = new OpenTitanTileLink.Host2Device(tlulP)
  })
  // Stub: tie off all outputs
  for (i <- 0 until 3) {
    io.hosts(i).a.ready := false.B
    io.hosts(i).d.valid := false.B
    io.hosts(i).d.bits  := 0.U.asTypeOf(new TLULChannelD(tlulP))
  }
  io.dev.a.valid := false.B
  io.dev.a.bits  := 0.U.asTypeOf(new TLULChannelA(tlulP))
  io.dev.d.ready := false.B
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulSocketM1_2_128Emitter extends App {
  val p = new coralnpu.Parameters
  p.lsuDataBits = 128
  ChiselStage.emitSystemVerilogFile(new TlulSocketM1_2_128(p), args)
}

@nowarn
object TlulSocketM1_3_128Emitter extends App {
  val p = new coralnpu.Parameters
  p.lsuDataBits = 128
  ChiselStage.emitSystemVerilogFile(new TlulSocketM1_3_128(p), args)
}
