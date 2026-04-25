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

/** Asynchronous FIFO for TileLink-UL with 128-bit data (stub). */
class TlulFifoAsync128(p: Parameters) extends Module {
  val tlulP = new TLULParameters(p)
  val io = IO(new Bundle {
    val host = Flipped(new OpenTitanTileLink.Host2Device(tlulP))
    val dev  = new OpenTitanTileLink.Host2Device(tlulP)
  })
  // Pass-through stub
  io.dev.a  <> io.host.a
  io.host.d <> io.dev.d
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulFifoAsync128Emitter extends App {
  val p = new coralnpu.Parameters
  p.lsuDataBits = 128
  ChiselStage.emitSystemVerilogFile(new TlulFifoAsync128(p), args)
}
