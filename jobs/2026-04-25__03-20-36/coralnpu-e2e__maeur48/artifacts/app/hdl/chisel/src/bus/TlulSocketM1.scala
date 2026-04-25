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

/** M-to-1 TileLink-UL socket (multiplexer / arbiter).
  *
  * Arbitrates requests from M TL-UL host ports onto a single device port using
  * a fixed-priority scheme (host 0 has highest priority).  Source IDs are
  * prefixed with the host-port index so that D-channel responses can be routed
  * back to the originating host.
  *
  * @param m         Number of upstream host ports.
  * @param addrBits  Address width.
  * @param dataBits  Data width in bits.
  */
class TlulSocketM1(m: Int, addrBits: Int, dataBits: Int) extends Module {
  require(m >= 1)

  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val hostIdBits = log2Ceil(m) // bits needed to encode which host

  val io = IO(new Bundle {
    val hosts  = Vec(m, new OpenTitanTileLink.Device2Host(tlulP))
    val device = new OpenTitanTileLink.Host2Device(tlulP)
  })

  // -------------------------------------------------------------------------
  // State: one outstanding transaction at a time
  // -------------------------------------------------------------------------
  val sIdle :: sWaitD :: Nil = Enum(2)
  val state  = RegInit(sIdle)
  val selReg = RegInit(0.U(log2Ceil(m).W))

  // Default drives
  for (i <- 0 until m) {
    io.hosts(i).a.ready := false.B
    io.hosts(i).d.valid := false.B
    io.hosts(i).d.bits  := 0.U.asTypeOf(new OpenTitanTileLink.D_Channel(tlulP))
  }
  io.device.a.valid := false.B
  io.device.a.bits  := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlulP))
  io.device.d.ready := false.B

  // -------------------------------------------------------------------------
  // Arbitration: fixed priority (port 0 is highest)
  // -------------------------------------------------------------------------
  val granted    = Wire(UInt(log2Ceil(m).W))
  val anyValid   = Wire(Bool())
  granted  := 0.U
  anyValid := false.B
  for (i <- m - 1 to 0 by -1) {
    when(io.hosts(i).a.valid) {
      granted  := i.U
      anyValid := true.B
    }
  }

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      when(anyValid) {
        // Forward A-channel to device, tagging the source with the host index
        val selA = io.hosts(granted).a.bits
        io.device.a.valid          := true.B
        io.device.a.bits           := selA
        // Embed host index into MSBs of source field
        io.device.a.bits.source    := Cat(granted, selA.source(tlulP.sourceBits - hostIdBits - 1, 0))
        io.hosts(granted).a.ready  := io.device.a.ready
        when(io.device.a.ready) {
          selReg := granted
          state  := sWaitD
        }
      }
    }

    is(sWaitD) {
      io.device.d.ready := io.hosts(selReg).d.ready
      when(io.device.d.valid) {
        // Strip host-index prefix from source before forwarding to host
        val origSource = io.device.d.bits.source(tlulP.sourceBits - hostIdBits - 1, 0)
        io.hosts(selReg).d.valid        := true.B
        io.hosts(selReg).d.bits         := io.device.d.bits
        io.hosts(selReg).d.bits.source  := origSource
        when(io.hosts(selReg).d.ready) {
          state := sIdle
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Convenience subclasses for 2 and 3 hosts at 128-bit width
// ---------------------------------------------------------------------------

class TlulSocketM1_2_128 extends TlulSocketM1(m = 2, addrBits = 32, dataBits = 128)
class TlulSocketM1_3_128 extends TlulSocketM1(m = 3, addrBits = 32, dataBits = 128)

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulSocketM1_2_128Emitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new TlulSocketM1_2_128,
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}

@nowarn
object TlulSocketM1_3_128Emitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new TlulSocketM1_3_128,
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
