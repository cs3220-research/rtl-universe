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
  * TileLink-UL M-to-1 socket (arbitrated multiplexer).
  *
  * Accepts requests from M hosts and forwards them to a single device,
  * using round-robin arbitration. Responses are routed back to the
  * originating host based on the source ID tag.
  */
class TlulSocketM1(p: Parameters, nHosts: Int) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val host   = Vec(nHosts, Flipped(new OpenTitanTileLink.Host2Device(tlp)))
    val device = new OpenTitanTileLink.Host2Device(tlp)
  })

  val arbiter = Module(new RRArbiter(new TLULChannelA(tlp), nHosts))

  for (i <- 0 until nHosts) {
    arbiter.io.in(i).valid := io.host(i).a.valid
    arbiter.io.in(i).bits  := io.host(i).a.bits
    io.host(i).a.ready     := arbiter.io.in(i).ready
  }

  // Tag the source with host index (encode in upper bits of source)
  val taggedA  = Wire(new TLULChannelA(tlp))
  taggedA      := arbiter.io.out.bits
  taggedA.source := Cat(arbiter.io.chosen, arbiter.io.out.bits.source(tlp.sourceBits - log2Ceil(nHosts) - 2, 0))

  io.device.a.valid := arbiter.io.out.valid
  io.device.a.bits  := taggedA
  arbiter.io.out.ready := io.device.a.ready

  // Route D channel back to the correct host
  val hostSel = io.device.d.bits.source(tlp.sourceBits - 1, tlp.sourceBits - log2Ceil(nHosts))
  for (i <- 0 until nHosts) {
    io.host(i).d.valid := io.device.d.valid && (hostSel === i.U)
    io.host(i).d.bits  := io.device.d.bits
  }
  io.device.d.ready := MuxLookup(hostSel, false.B)(
    (0 until nHosts).map(i => i.U -> io.host(i).d.ready)
  )
}

/** 2-host to 1-device socket with 128-bit bus. */
class TlulSocketM1_2_128 extends Module {
  val p = new Parameters
  p.lsuDataBits = 128
  val inner = Module(new TlulSocketM1(p, 2))
  val io = IO(chiselTypeOf(inner.io))
  io <> inner.io
}

/** 3-host to 1-device socket with 128-bit bus. */
class TlulSocketM1_3_128 extends Module {
  val p = new Parameters
  p.lsuDataBits = 128
  val inner = Module(new TlulSocketM1(p, 3))
  val io = IO(chiselTypeOf(inner.io))
  io <> inner.io
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulSocketM1_2_128Emitter extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocketM1_2_128))
  )
}

@nowarn
object TlulSocketM1_3_128Emitter extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocketM1_3_128))
  )
}
