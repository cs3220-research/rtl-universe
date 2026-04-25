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

/** SRAM interface bundle.
  *
  * Exposes a synchronous SRAM with word-addressed read/write ports and a
  * byte-granular write-enable mask.
  *
  * @param addrBits  Address width (word address).
  * @param dataBits  Data width in bits.
  */
class SramIO(addrBits: Int, dataBits: Int) extends Bundle {
  val addr   = Output(UInt(addrBits.W))
  val enable = Output(Bool())
  val write  = Output(Bool())
  val wdata  = Output(UInt(dataBits.W))
  val wmask  = Output(UInt(dataBits.W))  // bit-granular mask
  val rdata  = Input(UInt(dataBits.W))
  val rvalid = Input(Bool())
}

/** TileLink-UL to synchronous SRAM adapter.
  *
  * Converts TL-UL A-channel requests (Get / PutFullData / PutPartialData) into
  * synchronous SRAM transactions and returns D-channel responses.
  *
  * The SRAM is assumed to have a one-cycle read latency: `rvalid` is expected
  * to be high the cycle after `enable` & `!write` is asserted.
  *
  * @param p         Project-wide parameters (provides dataBits).
  * @param addrBits  SRAM word-address width.
  */
class TlulToSram(p: coralnpu.Parameters, addrBits: Int) extends Module {
  val tlulP  = new TLULParameters(p)
  val dBytes = p.lsuDataBits / 8

  val io = IO(new Bundle {
    val tl   = new OpenTitanTileLink.Device2Host(tlulP)
    val sram = new SramIO(addrBits, p.lsuDataBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sReadWait :: sWriteAck :: sReadAck :: Nil = Enum(4)
  val state = RegInit(sIdle)

  // Latched request
  val reqSource  = RegInit(0.U(tlulP.sourceBits.W))
  val reqSize    = RegInit(0.U(tlulP.sizeBits.W))
  val reqIsGet   = RegInit(false.B)

  // Pending response (stall if D channel not ready)
  val pendValid  = RegInit(false.B)
  val pendData   = RegInit(0.U(p.lsuDataBits.W))
  val pendError  = RegInit(false.B)
  val pendSource = RegInit(0.U(tlulP.sourceBits.W))
  val pendSize   = RegInit(0.U(tlulP.sizeBits.W))
  val pendIsGet  = RegInit(false.B)

  // -------------------------------------------------------------------------
  // Default SRAM / TL-UL drives
  // -------------------------------------------------------------------------
  io.sram.addr   := 0.U
  io.sram.enable := false.B
  io.sram.write  := false.B
  io.sram.wdata  := 0.U
  io.sram.wmask  := 0.U

  io.tl.a.ready  := false.B
  io.tl.d.valid  := false.B
  io.tl.d.bits   := 0.U.asTypeOf(new OpenTitanTileLink.D_Channel(tlulP))

  // -------------------------------------------------------------------------
  // Pending D-channel response management
  // -------------------------------------------------------------------------
  // If a response is pending and D is not stalled, present it
  when(pendValid) {
    io.tl.d.valid             := true.B
    io.tl.d.bits.opcode       := Mux(pendIsGet,
                                    TLULOpcodesD.AccessAckData.asUInt,
                                    TLULOpcodesD.AccessAck.asUInt)
    io.tl.d.bits.param        := 0.U
    io.tl.d.bits.size         := pendSize
    io.tl.d.bits.source       := pendSource
    io.tl.d.bits.sink         := 0.U
    io.tl.d.bits.data         := pendData
    io.tl.d.bits.user         := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
    io.tl.d.bits.error        := pendError
    io.tl.d.bits.corrupt      := false.B
    when(io.tl.d.ready) {
      pendValid := false.B
    }
  }

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      // Accept new A-channel request only when no pending D response
      io.tl.a.ready := !pendValid
      when(io.tl.a.valid && !pendValid) {
        val op     = io.tl.a.bits.opcode
        val addr   = io.tl.a.bits.address >> log2Ceil(dBytes).U
        val isGet  = (op === TLULOpcodesA.Get.asUInt)
        val isPut  = (op === TLULOpcodesA.PutFullData.asUInt || op === TLULOpcodesA.PutPartialData.asUInt)

        reqSource := io.tl.a.bits.source
        reqSize   := io.tl.a.bits.size
        reqIsGet  := isGet

        io.sram.addr   := addr(addrBits - 1, 0)
        io.sram.enable := true.B
        io.sram.write  := isPut

        when(isPut) {
          // Build bit-granular write mask from byte mask
          io.sram.wdata := io.tl.a.bits.data
          io.sram.wmask := io.tl.a.bits.mask.asBools.zipWithIndex
            .map { case (b, i) => Fill(8, b) << (i * 8) }
            .reduce(_ | _)
          state := sWriteAck
        }.elsewhen(isGet) {
          state := sReadWait
        }.otherwise {
          // Unknown opcode — return error
          pendValid  := true.B
          pendIsGet  := false.B
          pendError  := true.B
          pendSource := io.tl.a.bits.source
          pendSize   := io.tl.a.bits.size
          pendData   := 0.U
        }
      }
    }

    is(sWriteAck) {
      // SRAM write is one-cycle; immediately return AccessAck
      pendValid  := true.B
      pendIsGet  := false.B
      pendError  := false.B
      pendSource := reqSource
      pendSize   := reqSize
      pendData   := 0.U
      state      := sIdle
    }

    is(sReadWait) {
      // Wait for SRAM rvalid
      when(io.sram.rvalid) {
        pendValid  := true.B
        pendIsGet  := true.B
        pendError  := false.B
        pendSource := reqSource
        pendSize   := reqSize
        pendData   := io.sram.rdata
        state      := sIdle
      }
    }
  }
}
