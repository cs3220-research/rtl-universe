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

/** SPI to TileLink-UL bridge (version 1 — simple passthrough).
  *
  * Receives SPI frames and translates them into TL-UL A-channel transactions.
  * This is a simplified implementation that uses a synchronous SPI receiver
  * clocked by the system clock.  The frame format is:
  *
  * {{{
  *   Byte 0    : opcode  (0x01=read, 0x02=write)
  *   Bytes 1-4 : address (big-endian)
  *   Bytes 5-6 : length  (number of 128-bit beats - 1, big-endian)
  *   Bytes 7+  : data    (write: payload; read: ignored)
  * }}}
  *
  * Read responses are driven out on MISO immediately after the header.
  *
  * @param addrBits  TL-UL address width (unused in v1 beyond IO declarations).
  * @param dataBits  TL-UL data width.
  */
class Spi2TLUL(addrBits: Int = 32, dataBits: Int = 32) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    // SPI signals (system-clock domain)
    val spi_clk  = Input(Clock())
    val spi_csb  = Input(Bool())   // active-low chip select
    val spi_mosi = Input(Bool())
    val spi_miso = Output(Bool())

    // TL-UL host-facing port (this module drives A, receives D)
    val tl_a = Decoupled(new OpenTitanTileLink.A_Channel(tlulP))
    val tl_d = Flipped(Decoupled(new OpenTitanTileLink.D_Channel(tlulP)))
  })

  // -------------------------------------------------------------------------
  // SPI shift register (MSB first, 8-bit bytes)
  // -------------------------------------------------------------------------
  val shiftReg = RegInit(0.U(8.W))
  val bitCnt   = RegInit(0.U(3.W))
  val byteReady = Wire(Bool())
  byteReady := false.B

  // Shift on rising edge of spi_clk (modelled as level-change with a flop)
  val sclkSync0 = RegNext(io.spi_clk.asUInt(0), false.B)
  val sclkSync1 = RegNext(sclkSync0, false.B)
  val sclkRise  = sclkSync0 && !sclkSync1

  when(!io.spi_csb) {
    when(sclkRise) {
      shiftReg  := Cat(shiftReg(6, 0), io.spi_mosi)
      bitCnt    := bitCnt + 1.U
      byteReady := (bitCnt === 7.U)
    }
  }.otherwise {
    bitCnt := 0.U
  }

  // -------------------------------------------------------------------------
  // Frame state machine
  // -------------------------------------------------------------------------
  val sHdr0 :: sHdr1 :: sHdr2 :: sHdr3 :: sHdr4 :: sHdr5 :: sHdr6 ::
      sData :: sTlReq :: sTlResp :: Nil = Enum(10)

  val fsmState = RegInit(sHdr0)
  val opcode   = RegInit(0.U(8.W))
  val addr     = RegInit(0.U(32.W))
  val len      = RegInit(0.U(16.W))
  val txData   = RegInit(0.U(dataBits.W))
  val misoReg  = RegInit(false.B)

  io.spi_miso := misoReg

  io.tl_a.valid        := false.B
  io.tl_a.bits         := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlulP))
  io.tl_d.ready        := false.B

  when(io.spi_csb) {
    fsmState := sHdr0
  }.elsewhen(byteReady) {
    switch(fsmState) {
      is(sHdr0) { opcode := shiftReg;           fsmState := sHdr1 }
      is(sHdr1) { addr   := Cat(0.U(24.W), shiftReg); fsmState := sHdr2 }
      is(sHdr2) { addr   := Cat(addr(7, 0), shiftReg, 0.U(8.W)); fsmState := sHdr3 }
      is(sHdr3) { addr   := Cat(addr(15, 0), shiftReg, 0.U(8.W)); fsmState := sHdr4 }
      is(sHdr4) { addr   := Cat(addr(23, 0), shiftReg); fsmState := sHdr5 }
      is(sHdr5) { len    := Cat(0.U(8.W), shiftReg);  fsmState := sHdr6 }
      is(sHdr6) { len    := Cat(len(7, 0), shiftReg); fsmState := sData }
      is(sData) {
        txData   := Cat(txData(dataBits - 9, 0), shiftReg)
        fsmState := sTlReq
      }
    }
  }

  when(fsmState === sTlReq) {
    val isWrite = (opcode === 0x02.U)
    io.tl_a.valid           := true.B
    io.tl_a.bits.opcode     := Mux(isWrite, TLULOpcodesA.PutFullData.asUInt, TLULOpcodesA.Get.asUInt)
    io.tl_a.bits.param      := 0.U
    io.tl_a.bits.size       := log2Ceil(dataBits / 8).U
    io.tl_a.bits.source     := 0.U
    io.tl_a.bits.address    := addr
    io.tl_a.bits.mask       := Fill(dataBits / 8, 1.U)
    io.tl_a.bits.data       := txData
    io.tl_a.bits.user       := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
    io.tl_a.bits.corrupt    := false.B
    when(io.tl_a.ready) {
      fsmState := sTlResp
    }
  }

  when(fsmState === sTlResp) {
    io.tl_d.ready := true.B
    when(io.tl_d.valid) {
      fsmState := sHdr0
    }
  }
}
