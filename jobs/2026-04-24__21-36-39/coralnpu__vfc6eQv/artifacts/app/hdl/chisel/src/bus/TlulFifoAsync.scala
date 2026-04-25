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
import freechips.rocketchip.util._

/**
  * TileLink-UL asynchronous FIFO with 128-bit data bus.
  * Bridges two clock domains using AsyncQueue.
  */
class TlulFifoAsync128 extends Module {
  val p = new Parameters
  p.lsuDataBits = 128
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    // Enqueue side (input clock domain, uses module clock)
    val enq_tl = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val deq_tl = new OpenTitanTileLink.Host2Device(tlp)

    // Dequeue clock
    val deq_clock = Input(Clock())
    val deq_reset = Input(Bool())
  })

  // We use an AsyncQueue for the request (A channel) and response (D channel)
  val aQueue = Module(new AsyncQueue(new TLULChannelA(tlp), AsyncQueueParams(depth = 4, safe = false)))
  val dQueue = Module(new AsyncQueue(new TLULChannelD(tlp), AsyncQueueParams(depth = 4, safe = false)))

  // A channel: enq side = module clock, deq side = deq_clock
  aQueue.io.enq_clock := clock
  aQueue.io.enq_reset := reset.asBool
  aQueue.io.deq_clock := io.deq_clock
  aQueue.io.deq_reset := io.deq_reset

  // D channel: enq side = deq_clock, deq side = module clock
  dQueue.io.enq_clock := io.deq_clock
  dQueue.io.enq_reset := io.deq_reset
  dQueue.io.deq_clock := clock
  dQueue.io.deq_reset := reset.asBool

  // Wire A channel from enq TL to async queue
  aQueue.io.enq.valid := io.enq_tl.a.valid
  aQueue.io.enq.bits  := io.enq_tl.a.bits
  io.enq_tl.a.ready   := aQueue.io.enq.ready

  // Wire A channel from async queue to deq TL
  io.deq_tl.a.valid    := aQueue.io.deq.valid
  io.deq_tl.a.bits     := aQueue.io.deq.bits
  aQueue.io.deq.ready  := io.deq_tl.a.ready

  // Wire D channel from deq TL to async queue
  dQueue.io.enq.valid := io.deq_tl.d.valid
  dQueue.io.enq.bits  := io.deq_tl.d.bits
  io.deq_tl.d.ready   := dQueue.io.enq.ready

  // Wire D channel from async queue to enq TL
  io.enq_tl.d.valid    := dQueue.io.deq.valid
  io.enq_tl.d.bits     := dQueue.io.deq.bits
  dQueue.io.deq.ready  := io.enq_tl.d.ready
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulFifoAsync128Emitter extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulFifoAsync128))
  )
}
