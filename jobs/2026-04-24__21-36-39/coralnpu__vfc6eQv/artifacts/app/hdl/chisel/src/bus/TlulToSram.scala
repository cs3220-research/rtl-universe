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

/** SRAM interface bundle. */
class SramInterface(addrBits: Int, dataBits: Int) extends Bundle {
  val enable = Output(Bool())
  val write  = Output(Bool())
  val addr   = Output(UInt(addrBits.W))
  val wdata  = Output(UInt(dataBits.W))
  val wmask  = Output(UInt((dataBits / 8).W))
  val rvalid = Input(Bool())
  val rdata  = Input(UInt(dataBits.W))
}

/**
  * TileLink-UL to SRAM adapter.
  *
  * Accepts TL-UL requests and translates them to a simple SRAM interface.
  * Handles backpressure on the D-channel (holds response until ready).
  *
  * @param p        coralnpu Parameters (for data bus width)
  * @param addrBits SRAM address width in bits
  */
class TlulToSram(p: Parameters, addrBits: Int) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl   = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val sram = new SramInterface(addrBits, p.lsuDataBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sWaitRdata :: sWaitD :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Latch request fields
  val pendingWrite   = RegInit(false.B)
  val pendingSource  = RegInit(0.U(tlp.sourceBits.W))
  val pendingSize    = RegInit(0.U(tlp.sizeBits.W))
  val pendingRdata   = RegInit(0.U(p.lsuDataBits.W))
  val pendingDValid  = RegInit(false.B)

  // Default SRAM outputs
  io.sram.enable := false.B
  io.sram.write  := false.B
  io.sram.addr   := 0.U
  io.sram.wdata  := 0.U
  io.sram.wmask  := 0.U

  // Default TL-UL outputs
  io.tl.a.ready      := false.B
  io.tl.d.valid      := false.B
  io.tl.d.bits       := 0.U.asTypeOf(new TLULChannelD(tlp))

  val byteAddrBits = log2Ceil(p.lsuDataBits / 8) // number of byte-offset bits
  val wordAddrBits = addrBits                      // word address bits

  switch(state) {
    is(sIdle) {
      io.tl.a.ready := true.B
      when(io.tl.a.valid) {
        val isWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData) ||
                      (io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData)

        // Drive SRAM this cycle
        io.sram.enable := true.B
        io.sram.write  := isWrite
        io.sram.addr   := io.tl.a.bits.address >> byteAddrBits.U
        io.sram.wdata  := io.tl.a.bits.data
        io.sram.wmask  := io.tl.a.bits.mask

        pendingWrite  := isWrite
        pendingSource := io.tl.a.bits.source
        pendingSize   := io.tl.a.bits.size

        when(isWrite) {
          // Write: no read data needed, wait for D acceptance
          pendingRdata  := 0.U
          pendingDValid := true.B
          state         := sWaitD
        }.otherwise {
          // Read: wait for SRAM read data
          pendingDValid := false.B
          state         := sWaitRdata
        }
      }
    }

    is(sWaitRdata) {
      when(io.sram.rvalid) {
        pendingRdata  := io.sram.rdata
        pendingDValid := true.B
        state         := sWaitD
      }
    }

    is(sWaitD) {
      io.tl.d.valid        := true.B
      io.tl.d.bits.opcode  := Mux(pendingWrite, TLULOpcodesD.AccessAck, TLULOpcodesD.AccessAckData)
      io.tl.d.bits.param   := 0.U
      io.tl.d.bits.size    := pendingSize
      io.tl.d.bits.source  := pendingSource
      io.tl.d.bits.sink    := 0.U
      io.tl.d.bits.denied  := false.B
      io.tl.d.bits.data    := pendingRdata
      io.tl.d.bits.corrupt := false.B
      io.tl.d.bits.error   := false.B

      when(io.tl.d.ready) {
        pendingDValid := false.B
        state         := sIdle
      }
    }
  }
}
