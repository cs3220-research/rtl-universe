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
  * AXI4 to TileLink-UL bridge.
  *
  * Converts an AXI4 master interface to a TL-UL host interface.
  * Supports single-beat read and write transactions.
  */
class Axi2TLUL(p: Parameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val axi = Flipped(new AxiMasterIO(tlp.addrBits, tlp.dataBits, tlp.sourceBits))
    val tl  = new OpenTitanTileLink.Host2Device(tlp)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle :: sWrAddr :: sWrData :: sTlWr :: sTlWrWait :: sTlRd :: sTlRdWait :: sWrResp :: sRdData :: Nil = Enum(9)
  val state = RegInit(sIdle)

  val addrReg   = RegInit(0.U(tlp.addrBits.W))
  val dataReg   = RegInit(0.U(tlp.dataBits.W))
  val strbReg   = RegInit(0.U((tlp.dataBits / 8).W))
  val sizeReg   = RegInit(0.U(tlp.sizeBits.W))
  val idReg     = RegInit(0.U(tlp.sourceBits.W))
  val respReg   = RegInit(0.U(2.W))
  val rdDataReg = RegInit(0.U(tlp.dataBits.W))

  // Default AXI outputs
  io.axi.read.addr.ready  := false.B
  io.axi.read.data.valid  := false.B
  io.axi.read.data.bits   := 0.U.asTypeOf(new AxiReadDataChannel(tlp.dataBits, tlp.sourceBits))
  io.axi.write.addr.ready := false.B
  io.axi.write.data.ready := false.B
  io.axi.write.resp.valid := false.B
  io.axi.write.resp.bits  := 0.U.asTypeOf(new AxiWriteRespChannel(tlp.sourceBits))

  // Default TL outputs
  io.tl.a.valid     := false.B
  io.tl.a.bits      := 0.U.asTypeOf(new TLULChannelA(tlp))
  io.tl.d.ready     := false.B

  switch(state) {
    is(sIdle) {
      // Prioritize writes
      when(io.axi.write.addr.valid && io.axi.write.data.valid) {
        io.axi.write.addr.ready := true.B
        io.axi.write.data.ready := true.B
        addrReg := io.axi.write.addr.bits.addr
        dataReg := io.axi.write.data.bits.data
        strbReg := io.axi.write.data.bits.strb
        sizeReg := io.axi.write.addr.bits.size
        idReg   := io.axi.write.addr.bits.id
        state   := sTlWr
      }.elsewhen(io.axi.read.addr.valid) {
        io.axi.read.addr.ready := true.B
        addrReg := io.axi.read.addr.bits.addr
        sizeReg := io.axi.read.addr.bits.size
        idReg   := io.axi.read.addr.bits.id
        state   := sTlRd
      }
    }

    is(sTlWr) {
      io.tl.a.valid        := true.B
      io.tl.a.bits.opcode  := TLULOpcodesA.PutFullData
      io.tl.a.bits.param   := 0.U
      io.tl.a.bits.size    := sizeReg
      io.tl.a.bits.source  := idReg
      io.tl.a.bits.address := addrReg
      io.tl.a.bits.mask    := strbReg
      io.tl.a.bits.data    := dataReg
      io.tl.a.bits.corrupt := false.B

      when(io.tl.a.ready) {
        state := sTlWrWait
      }
    }

    is(sTlWrWait) {
      io.tl.d.ready := true.B
      when(io.tl.d.valid) {
        respReg := Mux(io.tl.d.bits.error, 2.U, 0.U)
        state   := sWrResp
      }
    }

    is(sWrResp) {
      io.axi.write.resp.valid       := true.B
      io.axi.write.resp.bits.resp   := respReg
      io.axi.write.resp.bits.id     := idReg
      when(io.axi.write.resp.ready) {
        state := sIdle
      }
    }

    is(sTlRd) {
      io.tl.a.valid        := true.B
      io.tl.a.bits.opcode  := TLULOpcodesA.Get
      io.tl.a.bits.param   := 0.U
      io.tl.a.bits.size    := sizeReg
      io.tl.a.bits.source  := idReg
      io.tl.a.bits.address := addrReg
      io.tl.a.bits.mask    := ((1.U << (1.U << sizeReg)) - 1.U)
      io.tl.a.bits.data    := 0.U
      io.tl.a.bits.corrupt := false.B

      when(io.tl.a.ready) {
        state := sTlRdWait
      }
    }

    is(sTlRdWait) {
      io.tl.d.ready := true.B
      when(io.tl.d.valid) {
        rdDataReg := io.tl.d.bits.data
        respReg   := Mux(io.tl.d.bits.error, 2.U, 0.U)
        state     := sRdData
      }
    }

    is(sRdData) {
      io.axi.read.data.valid       := true.B
      io.axi.read.data.bits.data   := rdDataReg
      io.axi.read.data.bits.last   := true.B
      io.axi.read.data.bits.resp   := respReg
      io.axi.read.data.bits.id     := idReg
      when(io.axi.read.data.ready) {
        state := sIdle
      }
    }
  }
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object EmitAxi2TLUL extends App {
  val p = new Parameters
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new Axi2TLUL(p)))
  )
}
