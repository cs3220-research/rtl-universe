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

/** 1-to-N TileLink-UL socket (demultiplexer / decoder).
  *
  * Routes requests from a single TL-UL host to one of N device ports based on
  * the address presented.  The address mapping is provided as a sequence of
  * (base, mask) pairs: a request with address A is routed to device i when
  * `(A & mask(i)) == base(i)`.
  *
  * A registered routing tag is used to forward the D-channel response back to
  * the host.
  *
  * @param n         Number of downstream device ports.
  * @param addrBits  Address width.
  * @param dataBits  Data width in bits.
  */
class TlulSocket1N(n: Int, addrBits: Int, dataBits: Int) extends Module {
  require(n >= 1)

  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    val host    = new OpenTitanTileLink.Device2Host(tlulP)
    val devices = Vec(n, new OpenTitanTileLink.Host2Device(tlulP))
    // Address decode: for each device port, which address base and mask to use
    val base    = Input(Vec(n, UInt(addrBits.W)))
    val mask    = Input(Vec(n, UInt(addrBits.W)))
  })

  // -------------------------------------------------------------------------
  // Decode: find which device port matches the incoming address
  // -------------------------------------------------------------------------
  val aAddr   = io.host.a.bits.address
  val devSel  = Wire(UInt(log2Ceil(n + 1).W))
  val devHit  = Wire(Bool())

  devSel := 0.U
  devHit := false.B
  for (i <- n - 1 to 0 by -1) {
    when((aAddr & io.mask(i)) === io.base(i)) {
      devSel := i.U
      devHit := true.B
    }
  }

  // -------------------------------------------------------------------------
  // State: track which device the outstanding transaction went to
  // -------------------------------------------------------------------------
  val sIdle :: sWaitD :: Nil = Enum(2)
  val state   = RegInit(sIdle)
  val selReg  = RegInit(0.U(log2Ceil(n).W))

  // -------------------------------------------------------------------------
  // Drive device A-channel ports (only the selected device receives the request)
  // -------------------------------------------------------------------------
  for (i <- 0 until n) {
    io.devices(i).a.valid := false.B
    io.devices(i).a.bits  := io.host.a.bits
    io.devices(i).d.ready := false.B
  }

  io.host.a.ready := false.B
  io.host.d.valid := false.B
  io.host.d.bits  := 0.U.asTypeOf(new OpenTitanTileLink.D_Channel(tlulP))

  switch(state) {
    is(sIdle) {
      when(io.host.a.valid && devHit) {
        io.devices(devSel).a.valid := true.B
        io.host.a.ready            := io.devices(devSel).a.ready
        when(io.devices(devSel).a.ready) {
          selReg := devSel
          state  := sWaitD
        }
      }.elsewhen(io.host.a.valid && !devHit) {
        // No device matched — return error response
        io.host.a.ready    := true.B
        io.host.d.valid    := true.B
        io.host.d.bits.opcode  := TLULOpcodesD.AccessAck.asUInt
        io.host.d.bits.param   := 0.U
        io.host.d.bits.size    := io.host.a.bits.size
        io.host.d.bits.source  := io.host.a.bits.source
        io.host.d.bits.sink    := 0.U
        io.host.d.bits.data    := 0.U
        io.host.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
        io.host.d.bits.error   := true.B
        io.host.d.bits.corrupt := false.B
      }
    }

    is(sWaitD) {
      io.host.d.valid            := io.devices(selReg).d.valid
      io.host.d.bits             := io.devices(selReg).d.bits
      io.devices(selReg).d.ready := io.host.d.ready
      when(io.devices(selReg).d.valid && io.host.d.ready) {
        state := sIdle
      }
    }
  }
}

/** Convenience subclass: 4-port 128-bit socket. */
class TlulSocket1N_128 extends TlulSocket1N(n = 4, addrBits = 32, dataBits = 128)

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulSocket1N_128Emitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new TlulSocket1N_128,
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
