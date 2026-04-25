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

/** M-to-1 TL-UL socket.
  *
  * Arbitrates M host ports onto a single downstream device port using
  * round-robin arbitration on the A (request) channel.  D (response) channel
  * replies are routed back to the originating host using a registered source
  * tag.
  *
  * @param p TL-UL parameters
  * @param m number of upstream host ports
  */
class TlulSocketM1(p: TLULParameters, m: Int) extends Module {
  val io = IO(new Bundle {
    val tl_h = Vec(m, Flipped(new TLBundleUL(p)))
    val tl_d = new TLBundleUL(p)
  })

  // Round-robin arbiter across all host A channels.
  val arb = Module(new RRArbiter(new TLChannelA(p), m))
  for (i <- 0 until m) {
    arb.io.in(i) <> io.tl_h(i).a
  }
  io.tl_d.a <> arb.io.out

  // Record which host originated the in-flight request so we can demux the
  // D-channel response back to the correct host.
  val srcReg = RegInit(0.U(log2Ceil(m).W))
  when(io.tl_d.a.fire) {
    srcReg := arb.io.chosen
  }

  // D channel: fan out to all hosts; only the originator sees valid.
  for (i <- 0 until m) {
    io.tl_h(i).d.valid := io.tl_d.d.valid && (srcReg === i.U)
    io.tl_h(i).d.bits  := io.tl_d.d.bits
  }
  io.tl_d.d.ready := io.tl_h(srcReg).d.ready

  private def log2Ceil(x: Int): Int = {
    require(x >= 2)
    var r = 0; var v = x - 1; while (v > 0) { r += 1; v >>= 1 }; r
  }
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulSocketM1_2_128Emitter extends App {
  val p = TLULParameters(dataWidth = 128, maskWidth = 16)
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocketM1(p, 2))) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}

@nowarn
object TlulSocketM1_3_128Emitter extends App {
  val p = TLULParameters(dataWidth = 128, maskWidth = 16)
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocketM1(p, 3))) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
