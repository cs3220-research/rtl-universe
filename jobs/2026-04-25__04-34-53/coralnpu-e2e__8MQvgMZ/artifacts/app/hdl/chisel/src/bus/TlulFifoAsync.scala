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

/** Asynchronous (two-clock-domain) FIFO bridge for TL-UL.
  *
  * For simulation purposes this is implemented as a simple pass-through in a
  * single clock domain.  The depth parameter is retained for interface
  * compatibility with the emitter objects.
  */
class TlulFifoAsync(p: TLULParameters, depth: Int = 4) extends Module {
  val io = IO(new Bundle {
    val tl_h = Flipped(new TLBundleUL(p))
    val tl_d = new TLBundleUL(p)
  })

  // Pass-through: single clock domain simulation.
  io.tl_d.a <> io.tl_h.a
  io.tl_h.d <> io.tl_d.d
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

/** Emitter for a 128-bit data-width async FIFO. */
@nowarn
object TlulFifoAsync128Emitter extends App {
  val p = TLULParameters(dataWidth = 128, maskWidth = 16)
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulFifoAsync(p))) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
