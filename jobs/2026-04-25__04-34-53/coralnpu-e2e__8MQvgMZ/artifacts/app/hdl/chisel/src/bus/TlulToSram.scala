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

/** SRAM-side IO bundle.
  *
  * @param addrBits word-address width (log2 of number of words)
  * @param dataWidth data bus width in bits
  */
class TlulToSramSramIO(addrBits: Int, dataWidth: Int) extends Bundle {
  val enable = Output(Bool())
  val write  = Output(Bool())
  val addr   = Output(UInt(addrBits.W))
  val wdata  = Output(UInt(dataWidth.W))
  val wmask  = Output(UInt((dataWidth / 8).W))
  val rvalid = Input(Bool())
  val rdata  = Input(UInt(dataWidth.W))
}

/** TileLink-UL to SRAM adapter.
  *
  * Accepts a single in-flight request.  Write responses are generated
  * one cycle after the request fires.  Read responses are generated when
  * the SRAM asserts rvalid (typically one cycle later).
  *
  * @param p        coralnpu parameters (determines TL-UL data/mask widths)
  * @param addrBits word-address width for the SRAM interface
  */
class TlulToSram(p: coralnpu.Parameters, addrBits: Int) extends Module {
  val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    val tl   = Flipped(new OpenTitanTileLink.Host2Device(tlP))
    val sram = new TlulToSramSramIO(addrBits, tlP.dataWidth)
  })

  // -------------------------------------------------------------------------
  // Request side
  // -------------------------------------------------------------------------
  val req  = io.tl.a
  val resp = io.tl.d

  // We stall new requests while a response is pending.
  val respPending = RegInit(false.B)

  req.ready := !respPending

  // Drive SRAM signals.
  io.sram.enable := req.valid && req.ready
  io.sram.write  := req.bits.opcode =/= TLULOpcodesA.Get
  // Word address: drop the byte-offset bits.
  val byteOffsetBits = log2Ceil(tlP.dataWidth / 8)
  io.sram.addr  := req.bits.address(addrBits + byteOffsetBits - 1, byteOffsetBits)
  io.sram.wdata := req.bits.data
  io.sram.wmask := req.bits.mask

  // -------------------------------------------------------------------------
  // Response tracking registers
  // -------------------------------------------------------------------------
  val respData   = RegInit(0.U(tlP.dataWidth.W))
  val respSrc    = RegInit(0.U(tlP.sourceWidth.W))
  val respOp     = RegInit(TLULOpcodesD.AccessAck)
  val respSz     = RegInit(0.U(tlP.sizeWidth.W))
  val isRead     = RegInit(false.B)
  val rdataReady = RegInit(false.B) // rdata has been captured

  when(req.fire) {
    respPending := true.B
    respSrc     := req.bits.source
    respSz      := req.bits.size
    isRead      := req.bits.opcode === TLULOpcodesA.Get
    respOp      := Mux(
      req.bits.opcode === TLULOpcodesA.Get,
      TLULOpcodesD.AccessAckData,
      TLULOpcodesD.AccessAck
    )
    rdataReady  := false.B
  }

  // Capture read data when SRAM presents it.
  when(io.sram.rvalid) {
    respData   := io.sram.rdata
    rdataReady := true.B
  }

  // For writes the response can go out immediately (next cycle after request).
  // For reads we wait until rdata is ready.
  val respReady = !isRead || rdataReady

  resp.valid           := respPending && respReady
  resp.bits.opcode     := respOp
  resp.bits.param      := 0.U
  resp.bits.size       := respSz
  resp.bits.source     := respSrc
  resp.bits.sink       := 0.U
  resp.bits.denied     := false.B
  resp.bits.data       := respData
  resp.bits.corrupt    := false.B
  resp.bits.error      := false.B

  when(resp.fire) {
    respPending := false.B
    rdataReady  := false.B
  }

  private def log2Ceil(x: Int): Int = {
    require(x >= 1)
    var r = 0; var v = x - 1; while (v > 0) { r += 1; v >>= 1 }; r
  }
}
