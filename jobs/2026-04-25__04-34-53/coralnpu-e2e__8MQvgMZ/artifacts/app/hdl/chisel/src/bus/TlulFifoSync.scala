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

package bus

import chisel3._
import chisel3.util._

/** Synchronous FIFO bridge for TL-UL.
  *
  * Inserts elastic queues on both the A (request) and D (response) channels so
  * that bursting sources can be decoupled from the downstream device.
  */
class TlulFifoSync(p: TLULParameters, depth: Int = 4) extends Module {
  val io = IO(new Bundle {
    val tl_h = Flipped(new TLBundleUL(p))
    val tl_d = new TLBundleUL(p)
  })

  val aFifo = Module(new Queue(new TLChannelA(p), depth))
  val dFifo = Module(new Queue(new TLChannelD(p), depth))

  aFifo.io.enq <> io.tl_h.a
  io.tl_d.a    <> aFifo.io.deq

  dFifo.io.enq <> io.tl_d.d
  io.tl_h.d    <> dFifo.io.deq
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulFifoSyncEmitter extends App {
  val p = TLULParameters(dataWidth = 128, maskWidth = 16)
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulFifoSync(p))) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
