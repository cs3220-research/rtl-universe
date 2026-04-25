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

/** Converts AXI4-slave transactions into TileLink-UL host transactions.
  *
  * Each AXI beat maps to a single TL-UL A-channel request.  Write responses
  * are generated once the D-channel acknowledges the put.  Read data is
  * returned directly from the D-channel AccessAckData response.
  *
  * This is a simplified, single-outstanding-transaction implementation
  * suitable for low-bandwidth control-plane traffic.
  */
class Axi2TLUL(addrBits: Int, dataBits: Int, idBits: Int) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    val axi = new AxiSlaveIO(addrBits, dataBits, idBits)
    val tl  = new OpenTitanTileLink.Host2Device(tlulP)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sReadReq :: sReadResp :: sWriteAddrWait :: sWriteDataWait ::
    sWriteReq :: sWriteResp :: Nil = Enum(7)

  val state = RegInit(sIdle)

  // Latched AXI request fields
  val axiAddr  = RegInit(0.U(addrBits.W))
  val axiId    = RegInit(0.U(idBits.W))
  val axiLen   = RegInit(0.U(8.W))
  val axiSize  = RegInit(0.U(3.W))
  val axiWData = RegInit(0.U(dataBits.W))
  val axiWStrb = RegInit(0.U((dataBits / 8).W))

  // Beat counter for burst support
  val beatCnt = RegInit(0.U(8.W))

  // -------------------------------------------------------------------------
  // Default IO drives
  // -------------------------------------------------------------------------
  io.axi.read_addr.ready  := false.B
  io.axi.read_data.valid  := false.B
  io.axi.read_data.bits   := 0.U.asTypeOf(new AxiReadDataBundle(dataBits, idBits))
  io.axi.write_addr.ready := false.B
  io.axi.write_data.ready := false.B
  io.axi.write_resp.valid := false.B
  io.axi.write_resp.bits  := 0.U.asTypeOf(new AxiWriteRespBundle(idBits))

  io.tl.a.valid        := false.B
  io.tl.a.bits         := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlulP))
  io.tl.d.ready        := false.B

  // -------------------------------------------------------------------------
  // Helper: build a TL-UL A-channel message
  // -------------------------------------------------------------------------
  def tlSize: UInt = axiSize

  // -------------------------------------------------------------------------
  // State machine logic
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      // Prefer reads over writes when both are pending (arbitration policy)
      when(io.axi.read_addr.valid) {
        io.axi.read_addr.ready := true.B
        axiAddr := io.axi.read_addr.bits.addr
        axiId   := io.axi.read_addr.bits.id
        axiLen  := io.axi.read_addr.bits.len
        axiSize := io.axi.read_addr.bits.size
        beatCnt := 0.U
        state   := sReadReq
      }.elsewhen(io.axi.write_addr.valid && io.axi.write_data.valid) {
        io.axi.write_addr.ready := true.B
        io.axi.write_data.ready := true.B
        axiAddr  := io.axi.write_addr.bits.addr
        axiId    := io.axi.write_addr.bits.id
        axiLen   := io.axi.write_addr.bits.len
        axiSize  := io.axi.write_addr.bits.size
        axiWData := io.axi.write_data.bits.data
        axiWStrb := io.axi.write_data.bits.strb
        beatCnt  := 0.U
        state    := sWriteReq
      }.elsewhen(io.axi.write_addr.valid) {
        io.axi.write_addr.ready := true.B
        axiAddr := io.axi.write_addr.bits.addr
        axiId   := io.axi.write_addr.bits.id
        axiLen  := io.axi.write_addr.bits.len
        axiSize := io.axi.write_addr.bits.size
        beatCnt := 0.U
        state   := sWriteDataWait
      }
    }

    is(sWriteDataWait) {
      io.axi.write_data.ready := true.B
      when(io.axi.write_data.valid) {
        axiWData := io.axi.write_data.bits.data
        axiWStrb := io.axi.write_data.bits.strb
        state    := sWriteReq
      }
    }

    is(sReadReq) {
      io.tl.a.valid            := true.B
      io.tl.a.bits.opcode      := TLULOpcodesA.Get.asUInt
      io.tl.a.bits.param       := 0.U
      io.tl.a.bits.size        := axiSize
      io.tl.a.bits.source      := axiId(tlulP.sourceBits - 1, 0)
      io.tl.a.bits.address     := axiAddr
      io.tl.a.bits.mask        := Fill(dataBits / 8, 1.U(1.W))
      io.tl.a.bits.data        := 0.U
      io.tl.a.bits.user        := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      io.tl.a.bits.corrupt     := false.B
      when(io.tl.a.ready) {
        state := sReadResp
      }
    }

    is(sReadResp) {
      io.tl.d.ready := io.axi.read_data.ready
      when(io.tl.d.valid) {
        io.axi.read_data.valid        := true.B
        io.axi.read_data.bits.data    := io.tl.d.bits.data
        io.axi.read_data.bits.id      := axiId
        io.axi.read_data.bits.resp    := Mux(io.tl.d.bits.error, AxiResp.SLVERR, AxiResp.OKAY)
        io.axi.read_data.bits.last    := (beatCnt === axiLen)
        when(io.axi.read_data.ready) {
          when(beatCnt === axiLen) {
            state := sIdle
          }.otherwise {
            beatCnt := beatCnt + 1.U
            axiAddr := axiAddr + (1.U << axiSize)
            state   := sReadReq
          }
        }
      }
    }

    is(sWriteReq) {
      io.tl.a.valid            := true.B
      io.tl.a.bits.opcode      := TLULOpcodesA.PutFullData.asUInt
      io.tl.a.bits.param       := 0.U
      io.tl.a.bits.size        := axiSize
      io.tl.a.bits.source      := axiId(tlulP.sourceBits - 1, 0)
      io.tl.a.bits.address     := axiAddr
      io.tl.a.bits.mask        := axiWStrb
      io.tl.a.bits.data        := axiWData
      io.tl.a.bits.user        := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      io.tl.a.bits.corrupt     := false.B
      when(io.tl.a.ready) {
        state := sWriteResp
      }
    }

    is(sWriteResp) {
      io.tl.d.ready := true.B
      when(io.tl.d.valid) {
        when(beatCnt === axiLen) {
          // Last beat — issue AXI write response
          io.axi.write_resp.valid      := true.B
          io.axi.write_resp.bits.id    := axiId
          io.axi.write_resp.bits.resp  := Mux(io.tl.d.bits.error, AxiResp.SLVERR, AxiResp.OKAY)
          when(io.axi.write_resp.ready) {
            state := sIdle
          }
        }.otherwise {
          beatCnt := beatCnt + 1.U
          axiAddr := axiAddr + (1.U << axiSize)
          state   := sWriteDataWait
        }
      }
    }
  }
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object EmitAxi2TLUL extends App {
  ChiselStage.emitSystemVerilogFile(
    new Axi2TLUL(addrBits = 32, dataBits = 128, idBits = 4),
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
