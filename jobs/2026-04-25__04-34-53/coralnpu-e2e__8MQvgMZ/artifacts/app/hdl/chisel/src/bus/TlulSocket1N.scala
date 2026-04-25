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

/** 1-to-N TL-UL socket.
  *
  * Routes an incoming request from a single host to one of N downstream
  * devices based on an externally-provided one-hot / priority select vector.
  *
  * @param p        TL-UL parameters
  * @param n        number of downstream device ports
  * @param addrBits full address width (kept for interface symmetry)
  */
class TlulSocket1N(p: TLULParameters, n: Int, addrBits: Int = 32) extends Module {
  val io = IO(new Bundle {
    val tl_h   = Flipped(new TLBundleUL(p))
    val tl_d   = Vec(n, new TLBundleUL(p))
    val devSel = Input(Vec(n, Bool()))
  })

  // Priority-encode the selection vector to get an index.
  val sel = PriorityEncoder(io.devSel)

  // A channel: fan out to the selected device.
  for (i <- 0 until n) {
    io.tl_d(i).a.valid := io.tl_h.a.valid && io.devSel(i)
    io.tl_d(i).a.bits  := io.tl_h.a.bits
    io.tl_d(i).d.ready := io.tl_h.d.ready && io.devSel(i)
  }

  // Back-pressure from selected device to host.
  io.tl_h.a.ready := io.tl_d(sel).a.ready

  // D channel: mux from selected device.
  io.tl_h.d.valid := io.tl_d(sel).d.valid
  io.tl_h.d.bits  := io.tl_d(sel).d.bits
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

/** Emitter for a 128-bit, 4-device 1-to-N socket. */
@nowarn
object TlulSocket1N_128Emitter extends App {
  val p = TLULParameters(dataWidth = 128, maskWidth = 16)
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocket1N(p, 4))) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
