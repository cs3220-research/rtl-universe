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

package bus

import chisel3._
import chisel3.util._
import coralnpu.Parameters

/**
  * TileLink-UL synchronous FIFO with 128-bit data bus.
  * Buffers requests (A channel) and responses (D channel) with Queue modules.
  */
class TlulFifoSync extends Module {
  val p = new Parameters
  p.lsuDataBits = 128
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val enq_tl = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val deq_tl = new OpenTitanTileLink.Host2Device(tlp)
  })

  val aFifo = Module(new Queue(new TLULChannelA(tlp), 4))
  val dFifo = Module(new Queue(new TLULChannelD(tlp), 4))

  // A channel: enq from enq_tl, deq to deq_tl
  aFifo.io.enq.valid := io.enq_tl.a.valid
  aFifo.io.enq.bits  := io.enq_tl.a.bits
  io.enq_tl.a.ready  := aFifo.io.enq.ready

  io.deq_tl.a.valid  := aFifo.io.deq.valid
  io.deq_tl.a.bits   := aFifo.io.deq.bits
  aFifo.io.deq.ready := io.deq_tl.a.ready

  // D channel: enq from deq_tl, deq to enq_tl
  dFifo.io.enq.valid := io.deq_tl.d.valid
  dFifo.io.enq.bits  := io.deq_tl.d.bits
  io.deq_tl.d.ready  := dFifo.io.enq.ready

  io.enq_tl.d.valid  := dFifo.io.deq.valid
  io.enq_tl.d.bits   := dFifo.io.deq.bits
  dFifo.io.deq.ready := io.enq_tl.d.ready
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulFifoSyncEmitter extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulFifoSync))
  )
}
