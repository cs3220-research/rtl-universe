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
  * TileLink-UL to AXI4 bridge.
  *
  * Converts TL-UL host requests to AXI4 master transactions.
  * Acts as a TL-UL device (slave) that issues AXI transactions.
  */
class TLUL2Axi(p: Parameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl  = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val axi = new AxiMasterIO(tlp.addrBits, tlp.dataBits, tlp.sourceBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sWrAddr :: sWrData :: sWrResp :: sRdAddr :: sRdData :: Nil = Enum(6)
  val state = RegInit(sIdle)

  val addrReg   = RegInit(0.U(tlp.addrBits.W))
  val dataReg   = RegInit(0.U(tlp.dataBits.W))
  val maskReg   = RegInit(0.U((tlp.dataBits / 8).W))
  val sizeReg   = RegInit(0.U(tlp.sizeBits.W))
  val sourceReg = RegInit(0.U(tlp.sourceBits.W))
  val rdDataReg = RegInit(0.U(tlp.dataBits.W))
  val errorReg  = RegInit(false.B)

  // Default outputs
  io.tl.a.ready           := false.B
  io.tl.d.valid           := false.B
  io.tl.d.bits            := 0.U.asTypeOf(new TLULChannelD(tlp))

  io.axi.read.addr.valid  := false.B
  io.axi.read.addr.bits   := 0.U.asTypeOf(new AxiReadAddrChannel(tlp.addrBits, tlp.sourceBits))
  io.axi.read.data.ready  := false.B

  io.axi.write.addr.valid := false.B
  io.axi.write.addr.bits  := 0.U.asTypeOf(new AxiWriteAddrChannel(tlp.addrBits, tlp.sourceBits))
  io.axi.write.data.valid := false.B
  io.axi.write.data.bits  := 0.U.asTypeOf(new AxiWriteDataChannel(tlp.dataBits))
  io.axi.write.resp.ready := false.B

  switch(state) {
    is(sIdle) {
      io.tl.a.ready := true.B
      when(io.tl.a.valid) {
        val isWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData) ||
                      (io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData)
        addrReg   := io.tl.a.bits.address
        dataReg   := io.tl.a.bits.data
        maskReg   := io.tl.a.bits.mask
        sizeReg   := io.tl.a.bits.size
        sourceReg := io.tl.a.bits.source
        when(isWrite) {
          state := sWrAddr
        }.otherwise {
          state := sRdAddr
        }
      }
    }

    is(sWrAddr) {
      io.axi.write.addr.valid       := true.B
      io.axi.write.addr.bits.addr   := addrReg
      io.axi.write.addr.bits.size   := sizeReg
      io.axi.write.addr.bits.len    := 0.U
      io.axi.write.addr.bits.id     := sourceReg
      io.axi.write.addr.bits.burst  := 1.U  // INCR
      io.axi.write.addr.bits.lock   := 0.U
      io.axi.write.addr.bits.cache  := 0.U
      io.axi.write.addr.bits.prot   := 0.U
      io.axi.write.addr.bits.qos    := 0.U
      when(io.axi.write.addr.ready) {
        state := sWrData
      }
    }

    is(sWrData) {
      io.axi.write.data.valid       := true.B
      io.axi.write.data.bits.data   := dataReg
      io.axi.write.data.bits.strb   := maskReg
      io.axi.write.data.bits.last   := true.B
      when(io.axi.write.data.ready) {
        state := sWrResp
      }
    }

    is(sWrResp) {
      io.axi.write.resp.ready := true.B
      when(io.axi.write.resp.valid) {
        errorReg := io.axi.write.resp.bits.resp =/= 0.U
        state    := sIdle
        // Send TL-D response
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TLULOpcodesD.AccessAck
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := sizeReg
        io.tl.d.bits.source  := sourceReg
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := false.B
        io.tl.d.bits.data    := 0.U
        io.tl.d.bits.corrupt := false.B
        io.tl.d.bits.error   := io.axi.write.resp.bits.resp =/= 0.U
      }
    }

    is(sRdAddr) {
      io.axi.read.addr.valid       := true.B
      io.axi.read.addr.bits.addr   := addrReg
      io.axi.read.addr.bits.size   := sizeReg
      io.axi.read.addr.bits.len    := 0.U
      io.axi.read.addr.bits.id     := sourceReg
      io.axi.read.addr.bits.burst  := 1.U  // INCR
      io.axi.read.addr.bits.lock   := 0.U
      io.axi.read.addr.bits.cache  := 0.U
      io.axi.read.addr.bits.prot   := 0.U
      io.axi.read.addr.bits.qos    := 0.U
      when(io.axi.read.addr.ready) {
        state := sRdData
      }
    }

    is(sRdData) {
      io.axi.read.data.ready := true.B
      when(io.axi.read.data.valid) {
        rdDataReg := io.axi.read.data.bits.data
        errorReg  := io.axi.read.data.bits.resp =/= 0.U
        state     := sIdle
        // Send TL-D response
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TLULOpcodesD.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := sizeReg
        io.tl.d.bits.source  := sourceReg
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := false.B
        io.tl.d.bits.data    := io.axi.read.data.bits.data
        io.tl.d.bits.corrupt := false.B
        io.tl.d.bits.error   := io.axi.read.data.bits.resp =/= 0.U
      }
    }
  }
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object EmitTLUL2Axi extends App {
  val p = new Parameters
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TLUL2Axi(p)))
  )
}
