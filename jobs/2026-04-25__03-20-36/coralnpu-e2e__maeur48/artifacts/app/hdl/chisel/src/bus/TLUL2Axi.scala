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

/** Converts TileLink-UL device transactions to AXI4-master transactions.
  *
  * Acts as a TL-UL device (receives requests from a TL-UL host) and translates
  * them to AXI4-master reads/writes.
  *
  * This is the reverse of [[Axi2TLUL]].  It is a simplified single-outstanding-
  * transaction implementation suitable for control-plane use.
  */
class TLUL2Axi(addrBits: Int, dataBits: Int, idBits: Int) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    val tl  = new OpenTitanTileLink.Device2Host(tlulP)
    val axi = new AxiMasterIO(addrBits, dataBits, idBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sReadAddr :: sReadData :: sWriteAddr :: sWriteData :: sWriteResp :: Nil = Enum(6)

  val state = RegInit(sIdle)

  // Latched TL-UL request fields
  val tlOpcode  = RegInit(0.U(3.W))
  val tlSize    = RegInit(0.U(4.W))
  val tlSource  = RegInit(0.U(8.W))
  val tlAddr    = RegInit(0.U(addrBits.W))
  val tlMask    = RegInit(0.U((dataBits / 8).W))
  val tlData    = RegInit(0.U(dataBits.W))

  // Latched AXI response for write
  val axiWriteErr = RegInit(false.B)

  // -------------------------------------------------------------------------
  // Default IO drives
  // -------------------------------------------------------------------------
  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := 0.U.asTypeOf(new OpenTitanTileLink.D_Channel(tlulP))

  io.axi.read_addr.valid  := false.B
  io.axi.read_addr.bits   := 0.U.asTypeOf(new AxiAddrBundle(addrBits, idBits))
  io.axi.read_data.ready  := false.B
  io.axi.write_addr.valid := false.B
  io.axi.write_addr.bits  := 0.U.asTypeOf(new AxiAddrBundle(addrBits, idBits))
  io.axi.write_data.valid := false.B
  io.axi.write_data.bits  := 0.U.asTypeOf(new AxiWriteDataBundle(dataBits))
  io.axi.write_resp.ready := false.B

  // -------------------------------------------------------------------------
  // Helper: build AXI address bundle
  // -------------------------------------------------------------------------
  def buildAxiAddr(addr: UInt, src: UInt, sz: UInt): AxiAddrBundle = {
    val b = Wire(new AxiAddrBundle(addrBits, idBits))
    b.addr   := addr
    b.id     := src(idBits - 1, 0)
    b.prot   := 0.U
    b.len    := 0.U   // single beat
    b.size   := sz(2, 0)
    b.burst  := AxiBurst.INCR
    b.lock   := 0.U
    b.cache  := 0.U
    b.qos    := 0.U
    b.region := 0.U
    b
  }

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      io.tl.a.ready := true.B
      when(io.tl.a.valid) {
        tlOpcode := io.tl.a.bits.opcode
        tlSize   := io.tl.a.bits.size
        tlSource := io.tl.a.bits.source
        tlAddr   := io.tl.a.bits.address
        tlMask   := io.tl.a.bits.mask
        tlData   := io.tl.a.bits.data
        when(io.tl.a.bits.opcode === TLULOpcodesA.Get.asUInt) {
          state := sReadAddr
        }.otherwise {
          state := sWriteAddr
        }
      }
    }

    is(sReadAddr) {
      io.axi.read_addr.valid := true.B
      io.axi.read_addr.bits  := buildAxiAddr(tlAddr, tlSource, tlSize)
      when(io.axi.read_addr.ready) {
        state := sReadData
      }
    }

    is(sReadData) {
      io.axi.read_data.ready := true.B
      when(io.axi.read_data.valid) {
        io.tl.d.valid             := true.B
        io.tl.d.bits.opcode       := TLULOpcodesD.AccessAckData.asUInt
        io.tl.d.bits.param        := 0.U
        io.tl.d.bits.size         := tlSize
        io.tl.d.bits.source       := tlSource
        io.tl.d.bits.sink         := 0.U
        io.tl.d.bits.data         := io.axi.read_data.bits.data
        io.tl.d.bits.user         := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
        io.tl.d.bits.error        := (io.axi.read_data.bits.resp =/= AxiResp.OKAY)
        io.tl.d.bits.corrupt      := false.B
        when(io.tl.d.ready) {
          state := sIdle
        }
      }
    }

    is(sWriteAddr) {
      io.axi.write_addr.valid := true.B
      io.axi.write_addr.bits  := buildAxiAddr(tlAddr, tlSource, tlSize)
      when(io.axi.write_addr.ready) {
        state := sWriteData
      }
    }

    is(sWriteData) {
      io.axi.write_data.valid      := true.B
      io.axi.write_data.bits.data  := tlData
      io.axi.write_data.bits.strb  := tlMask
      io.axi.write_data.bits.last  := true.B
      when(io.axi.write_data.ready) {
        state := sWriteResp
      }
    }

    is(sWriteResp) {
      io.axi.write_resp.ready := true.B
      when(io.axi.write_resp.valid) {
        axiWriteErr := (io.axi.write_resp.bits.resp =/= AxiResp.OKAY)
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TLULOpcodesD.AccessAck.asUInt
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := tlSize
        io.tl.d.bits.source  := tlSource
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.data    := 0.U
        io.tl.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
        io.tl.d.bits.error   := (io.axi.write_resp.bits.resp =/= AxiResp.OKAY)
        io.tl.d.bits.corrupt := false.B
        when(io.tl.d.ready) {
          state := sIdle
        }
      }
    }
  }
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object EmitTLUL2Axi extends App {
  ChiselStage.emitSystemVerilogFile(
    new TLUL2Axi(addrBits = 32, dataBits = 128, idBits = 4),
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
